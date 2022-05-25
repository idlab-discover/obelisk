import { EMPTY, Observable, of, Subscription } from 'rxjs';
import { concatMap, expand, tap, toArray } from 'rxjs/operators';
import { Page, PageArgs } from '../model/types';

const defaults = {
    pageSize: 15,
    sortField: null,
    sort: 'asc' as ('asc' | 'desc')
}

export class FullSource<T> {
    items: T[];
    private subs: Subscription[] = [];

    private options: FullSourceOptions;

    constructor(
        private fetchDataFunction: (pageArgs: PageArgs) => Observable<Page<T>>,
        options: FullSourceOptions = {
            pageSize: 15,
            sortField: null,
            sort: 'asc'
        }
    ) {

        this.options = Object.assign(defaults, options);
        // Start with some data
        this.getAll();
    }

    private getAll() {
        this.subs.push(this.fetchDataFunction({ limit: this.options.pageSize }).pipe(
            expand(page => {
                if (page.cursor !== null) {
                    return this.fetchDataFunction({ limit: this.options.pageSize, cursor: page.cursor });
                } else {
                    return EMPTY;
                }
            }),
            concatMap(page => of(...page.items)),
            toArray()
        )
            .subscribe(arr => this.items = this.sortIfRequired(arr)));
    }

    private sortIfRequired(arr: T[]) {
        const field = this.options.sortField;
        if (field == null || arr.length <= 1) {
            return arr;
        } else {
            const sort = this.options.sort;
            const comp = (a, b, operator: (a: any, b: any) => number = (a, b) => a - b) => {
                return sort === 'asc' ? operator.call(a, b) : operator.call(b, a);
            }
            return arr?.sort((a, b) => {
                const aField = a[field];
                const bField = b[field];
                if (typeof aField === "string") {
                    return comp(aField.toLocaleLowerCase(), bField.toLocaleLowerCase(), String.prototype.localeCompare);
                } else {
                    return comp(aField, bField);
                }
            });
        }
    }

    /**
     * Cleanup any pending subscriptions
     */
    cleanUp() {
        this.subs.filter(sub => !sub.closed).forEach(sub => sub.unsubscribe());
    }
}

export interface FullSourceOptions {
    /** Page size, default: 15 */
    pageSize?: number;
    /** Field to sort on, default: null (no sorting) */
    sortField?: string | null;
    /** If sorting, which order. Default: 'asc' */
    sort?: 'asc' | 'desc';
}