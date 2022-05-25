import { ID, Page, PageArgs } from '@shared/model';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { debounceTime, finalize, tap } from 'rxjs/operators';

export class CarouselDataSource<T extends ID> {
    collection: T[] = [];
    query$ = new Subject<string>();
    items$ = new Subject<T[]>();
    fetching$ = new BehaviorSubject<boolean>(false);


    private lastCursor: string | undefined | null = undefined;
    private page: number = 0;
    private filter: any;

    constructor(
        private fetchDataFunction: (pageArgs: PageArgs) => Observable<Page<T>>,
        private pageSize: number = 3,
        private filterAttribute: string | null = 'name',
    ) {
        // Start with some data
        this.items$.next([]);
        this.fetchMissingItems().subscribe(page => this.showCurrentPage());
        this.query$.pipe(debounceTime(200)).subscribe(term => this.calcFilter(term));
    }

    /**
    * Call backend to fetch missing items, based on the given itemCount and the lastCursor.
    * Stores the new lastCursor, caches results and publishes to dataStream.
    */
    private fetchMissingItems(itemCount?: number): Observable<Page<T>> {
        if (!itemCount) {
            itemCount = this.pageSize;
        }

        if (this.lastCursor !== null) {
            this.fetching$.next(true);
            return this.fetchDataFunction({ cursor: this.lastCursor, limit: itemCount, filter: this.filter }).pipe(
                tap(page => {
                    this.lastCursor = page?.cursor;
                    if (page?.items?.length > 0) {
                        this.collection = [...this.collection, ...page.items];
                    }
                }),
                finalize(() => this.fetching$.next(false)),
            );
        }
    }

    private showCurrentPage() {
        this.items$.next(this.collection.slice(this.page * this.pageSize, (this.page + 1) * this.pageSize));
    }

    trackByFn(item: T) {
        return item.id;
    }

    hasNextPage() {
        return (this.lastCursor != null) || (this.collection.length > (this.page + 1) * this.pageSize);
    }

    hasPreviousPage() {
        return this.page > 0;
    }

    getNextPage(): void {
        if ((this.lastCursor !== null)) {
            this.page++;
            const missing = ((this.page + 1) * this.pageSize) - this.collection.length;
            if (missing > 0) {
                this.fetchMissingItems(missing).subscribe(page => this.showCurrentPage());
            } else {
                this.showCurrentPage();
            }
        } else if (this.collection.length > (this.page + 1) * this.pageSize) {
            this.page++;
            this.showCurrentPage();
        }
    }

    getPreviousPage(): void {
        if (this.page !== 0) {
            this.page--;
            this.showCurrentPage();
        }
    }

    reset(): void {
        this.collection = [];
        this.page = 0;
        this.lastCursor = undefined;
    }

    private calcFilter(term: string) {
        // Reset datasource cache
        this.reset();

        // Filter by setting filter object
        if (term && term.length > 0) {
            const q = {};
            q[this.filterAttribute] = {"_regex":`.*${term}.*`, "_options": 'i'}
            this.filter = q
        } else {
            this.filter = {};
        }

        // Fetch items
        this.fetchMissingItems().subscribe(page => this.showCurrentPage());

    }

}