import { ID, Page, PageArgs } from '@shared/model';
import { Filter, FilterBuilder } from '@shared/utils';
import { BehaviorSubject, EMPTY, Observable, of, Subject, Subscription } from 'rxjs';
import { debounceTime, filter, switchMap } from 'rxjs/operators';

export class NgSelectDataSource<T extends ID> {
    collection = Array.from<T>({ length: 0 });
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
     * Async results of queries (even if empty)
     */
    items$ = new BehaviorSubject<T[]>(this.collection);
    private currentItems: T[] = [];

    private opt: NgSelectDataSourceOptions = {
        filterFn: (term) => FilterBuilder.regex_i('name',  term),
        minChars: 2,
        filterAttribute: 'name',
    };

    private lastCursor: string | undefined | null = undefined;
    private extraFilter: any = null;
    private originalFilter: any = null;
    private pageArgs: PageArgs = { limit: 15 };

    private subs: Subscription[] = [];


    /**
     * Creates a dataSource for use with NgSelect and Obelisk Paged results
     * @param fetchDataFunction The function with all arguments bound, except for pageArgs
     * @param options Options for the behaviour. Default will be minChars:2, filterAttribute:name, filterFn: contains in name
     * @param pageArgs Default is pages of 15
     */
    constructor(
        private fetchDataFunction: (pageArgs: PageArgs) => Observable<Page<T>>,
        options?: Partial<NgSelectDataSourceOptions>,
        pageArgs?: PageArgs,
    ) {
        // Init pageArgs and Options
        if (pageArgs) {
            this.originalFilter = pageArgs.filter;
            Object.assign(this.pageArgs, pageArgs);
        }
        this.opt = Object.assign(this.opt, options);

        // Start with some data
        this.fetchMissingItems(this.pageArgs.limit);

        this.subs.push(this.queryRemote$.pipe(
            filter(t => t != null),
            debounceTime(200),
            switchMap(term => (term.length > 0 && term.length < this.opt.minChars) ? EMPTY : of(term))
        ).subscribe(term => this.filterRemote(this.opt.filterFn(term))));
        
        this.subs.push(this.queryLocal$.pipe(
            debounceTime(200),
            switchMap(term => (term?.length > 0 && term?.length < this.opt.minChars) ? EMPTY : of(term))
        ).subscribe(term => this.filterLocal(term)));
    }

    /**
    * Call backend to fetch missing items, based on the given itemCount and the lastCursor.
    * Stores the new lastCursor, caches results and publishes to dataStream.
    */
    fetchMissingItems(itemCount?: number): void {
        if (this.lastCursor !== null) {
            this.subs.push(this.fetchDataFunction({ cursor: this.lastCursor, limit: itemCount || this.pageArgs.limit, filter: this.composeFilter() })
                .subscribe(page => {
                    this.lastCursor = page?.cursor;
                    this.collection = this.collection.concat(page?.items);
                    this.txItems(this.collection);
                }));
        }
    }

    private txItems(items: T[]) {
        this.currentItems = items;
        this.items$.next(items);
    }

    trackByFn(item: T) {
        return item.id;
    }

    compareWith(opt: Partial<T>, selection: Partial<T>) {
        return opt.id === selection.id;
    }

    /**
     * Resets the last query. Usefull after selecting a value in multiselect (use as **(add)="dataSource.resetQuery()"**)
     */
    resetQuery() {
        this.queryRemote$.next('');
        this.queryLocal$.next('');
    }

    /**
     * Filter on the backend by sending a filter 
     * @param filter Filter opbject
     */
    private filterRemote(filter: any) {
        this.extraFilter = filter;
        // Reset results
        this.collection = [];
        // Reset lastCursor if reached
        this.lastCursor = undefined;
        this.fetchMissingItems();
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

    private filterLocal(term: string) {
        const t = term?.toLowerCase() || '';
        const getField = (item) => (this.opt.filterAttribute ? item[this.opt.filterAttribute] : item) as string;
        if (!t || t.length == 0) {
            this.items$.next(this.collection);
        } else {
            this.items$.next(this.collection.filter(item => getField(item).toLowerCase().indexOf(t) > -1));
        }
    }

    /**
     * Cleanup any open subscribption
     */
    cleanUp() {
        this.subs.filter(s => !s.closed).forEach(s => s.unsubscribe());
    }

    /**
     * Returns the items that were sent last. (eg. the ones that you can see in view)
     * @returns 
     */
    getCurrentItems() {
        return this.currentItems;
    }

    /**
     * Unpages the source fully. To fetch all items you can subscribe to the items$() method.
     */
    unpageFully(): void {
        while(this.lastCursor != null) {
            this.fetchMissingItems()
        }
    }

}

export interface NgSelectDataSourceOptions {
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
    filterAttribute?: string;
}