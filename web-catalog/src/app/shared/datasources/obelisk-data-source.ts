import { CollectionViewer, DataSource } from '@angular/cdk/collections';
import { Page, PageArgs } from '@shared/model';
import { Filter, FilterBuilder } from '@shared/utils';
import { AsyncSubject, BehaviorSubject, EMPTY, Observable, of, Subject, Subscription } from 'rxjs';
import { debounceTime, delay, switchMap, switchMapTo, tap } from 'rxjs/operators';

export class ObeliskDataSource<T> extends DataSource<T | undefined> {
    /**
     * Queries on the server
     */
    queryRemote$ = new Subject<string>();

    /**
     * Queries locally 
     * @deprecated Might not work so good
     * @see queryRemote$
     */
    queryLocal$ = new Subject<string>();

    /**
     * This observable only finishes once data is ready
     */
    loading$: BehaviorSubject<boolean> = new BehaviorSubject(false);

    private collection = Array.from<T>({ length: 0 });
    private dataStream = new BehaviorSubject<(T | undefined)[]>(this.collection);
    private subs: Subscription[] = [];
    private currentItems: T[] = [];
    private lastCursor: string | undefined | null = undefined;
    private extraFilter: any = null;
    private originalFilter: any = null;

    private pageArgs: PageArgs = { limit: 15 };
    private opt: ObeliskDataSourceOptions = {
        filterFn: (term) => FilterBuilder.regex_i('name', term),
        minChars: 2,
        filterAttributes: ['name'],
    };

    /**
     * Use this datasource to virtual scroll over Page<T> type from Obelisk.
     * @param fetchDataFunction The function to execute, bound with all arguments except for PageArgs
     * @param pageArgs The seperate pageArgs to use
     */
    constructor(
        private fetchDataFunction: (pageArgs: PageArgs) => Observable<Page<T>>,
        options?: Partial<ObeliskDataSourceOptions>,
        pageArgs?: PageArgs
    ) {
        super();


        this.init({ options, pageArgs });
    }

    private init({ options, pageArgs }: { options?: Partial<ObeliskDataSourceOptions>, pageArgs?: PageArgs } = {}) {
        // Init pageArgs and Options
        if (pageArgs) {
            this.originalFilter = pageArgs.filter;
            Object.assign(this.pageArgs, pageArgs);
        }
        this.opt = Object.assign(this.opt, options);

        // CleanUp any subs 
        this.cleanUp();

        // Start with some data
        this.fetchMissingItems(this.pageArgs.limit);

        // Setup filtering (do not add to subs, since it will be disconnected otherwise)
        this.subs.push(this.queryRemote$.pipe(
            debounceTime(200),
            switchMap(term => (term?.length > 0 && term?.length < this.opt.minChars) ? of('') : of(term))
        ).subscribe(term => this.filterRemote(this.opt.filterFn(term))));

        this.subs.push(this.queryLocal$.pipe(
            debounceTime(200),
            switchMap(term => (term?.length > 0 && term?.length < this.opt.minChars) ? of('') : of(term))
        ).subscribe(term => this.filterLocal(term)));
    }

    connect(collectionViewer: CollectionViewer): Observable<(T | undefined)[] | ReadonlyArray<T | undefined>> {
        this.init();
        this.subs.push(collectionViewer.viewChange.subscribe(range => {
            // Update the data
            const missingItemCount = this.getMissingItemCount(range.end);

            if (missingItemCount > 0) {
                // Request
                this.fetchMissingItems(missingItemCount);
            }
        }));
        return this.dataStream;
    }

    disconnect(collectionViewer: CollectionViewer): void {
        this.cleanUp();
    }

    /**
     * Invalidates the current cache and allows new refreshes of data.
     */
    invalidate() {
        this.collection = Array.from<T>({ length: 0 });
        // this.txItems(this.collection);
        this.lastCursor = undefined;
        this.fetchMissingItems(this.pageArgs.limit);
    }


    private filterLocal(term: string) {
        const t = term?.toLowerCase().trim() || '';
        const getField = (value: Object, dottedKey: string) => {
            const k = dottedKey.split('.');
            return k.reduce((acc, cur) => acc[cur], value);
        }
        let produceHaystack = (entry, key) => (key ? getField(entry, key) : entry) as string;
        let produceHaystacks = (entry: any, keys: string[] | null) => {
            if (keys != null && keys.length > 0) {
                return keys.map(key => produceHaystack(entry, key)).join('\n');
            } else {
                return entry;
            }
        }
        const keys = this.opt.filterAttributes;
        let results = this.collection.filter(entry => produceHaystacks(entry, keys).toLowerCase().indexOf(t) > -1);

        if (!t || t.length == 0) {
            this.txItems(this.collection);
        } else {
            this.txItems(results);
        }
    }

    /**
     * Filter on the backend by sending a filter 
     * @param filter Filter opbject
     */
    private filterRemote(filter: any) {
        this.extraFilter = filter;
        this.collection = [];

        this.lastCursor = undefined;
        this.fetchMissingItems(this.pageArgs.limit);
    }

    /**
    * Resets the last query. Usefull after selecting a value in multiselect (use as **(add)="dataSource.resetQuery()"**)
    * This goes through the debounce pipeline of 200ms
    */
    resetQuery() {
        this.queryRemote$.next('');
        this.queryLocal$.next('');
    }

    /**
     * Immeditaly reset the filter as if using the empty term ''. (no debounce)
     */
    resetImmediate() {
        this.filterRemote(this.opt.filterFn(''));
        this.filterLocal('');
    }

    private composeFilter() {
        if (this.extraFilter && this.originalFilter) {
            return { "_and": [this.originalFilter, this.extraFilter] };
        } else if (this.extraFilter) {
            return this.extraFilter;
        } else if (this.originalFilter) {
            return this.originalFilter;
        } else {
            return undefined;
        }
    }

    /**
     * The cache is empty and data is not still loading
     * @returns 
     */
    isCurrentlyEmpty() {
        return this.currentItems.length === 0 && !this.loading$.getValue();
    }

    get datastream$() {
        return this.dataStream;
    }

    /**
     * Unsubscribe from any pending subscriptions
     */
    cleanUp() {
        this.subs.filter(s => !s.closed).forEach(s => s.unsubscribe());
        this.subs = [];
    }

    /**
     * Returns the items that were sent last. (eg. the ones that you can see in view)
     * @returns 
     */
    getCurrentItems() {
        return this.currentItems;
    }

    /**
     * Call backend to fetch missing items, based on the given itemCount and the lastCursor.
     * Stores the new lastCursor, caches results and publishes to dataStream.
     */
    private fetchMissingItems(itemCount: number): void {
        this.subs.push(of(true).pipe(
            delay(0),
            tap(_ => this.loading$ = new BehaviorSubject<boolean>(true)),
            switchMapTo(this.fetchDataFunction({ cursor: this.lastCursor, limit: itemCount, filter: this.composeFilter() })),
        ).subscribe(page => {
            if (this.loading$.getValue()) {
                this.loading$.next(false);
                // this.loading$.complete();
            }
            this.lastCursor = page.cursor;
            this.collection = this.collection.concat(page.items);
            this.txItems(this.collection);
        }));

    }

    /**
     * Returns -1 if item is already in cache.
     * Returns items to fetch to get to item if not in cache.
     * @param i Index count of item
     */
    private getMissingItemCount(i: number): number {
        const collectedItems = this.collection.length;
        // this.lastCursor === null means there is no more data
        if (i < collectedItems || this.lastCursor === null) {
            return 0;
        } else {
            // Request at least a pagesize.
            return Math.max(this.pageArgs.limit, i - collectedItems + 1);
        }
    }

    private txItems(items: T[]) {
        this.currentItems = items;
        this.dataStream.next(items);
    }

}


export interface ObeliskDataSourceOptions {
    /**
     * Filter function to call for remote filter only
     */
    filterFn?: (term: any) => Filter<any>;
    /**
     * Minimum characters typed before filter/search fires.
     */
    minChars?: number;

    /**
     * For local filter only
     */
    filterAttributes?: string[];
}