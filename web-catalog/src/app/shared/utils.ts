import { EMPTY, interval, Observable, of, timer } from 'rxjs';
import { concatMap, expand, map, mapTo, pluck, startWith, switchMap, toArray } from 'rxjs/operators';
import { GraphQLError, isGraphQLErrorResult, Page, PageArgs, Response, ResponseHandler, TimeSeries } from './model';

export class FilterBuilder {
    /**
     * Equals: only a perfect match will do
     * @param field 
     * @param value 
     * @returns 
     */
    static eq<T extends number | boolean | string>(field: string, value: T): FilterElement<T> { return { [field]: value } }

    /**
     * Lower than
     * @param field 
     * @param value 
     * @returns 
     */
    static lt(field: string, value: number): FilterElement<number> { return { [`${field}_lt`]: value } }

    /**
     * Lower than or equals
     * @param field 
     * @param value 
     * @returns 
     */
    static lte(field: string, value: number): FilterElement<number> { return { [`${field}_lte`]: value } }

    /**
     * Greater than
     * @param field 
     * @param value 
     * @returns 
     */
    static gt(field: string, value: number): FilterElement<number> { return { [`${field}_gt`]: value } }

    /**
     * Greater than or equals
     * @param field 
     * @param value 
     * @returns 
     */
    static gte(field: string, value: number): FilterElement<number> { return { [`${field}_gte`]: value } }

    /**
     * Matches any element in array of values
     * @param field 
     * @param values 
     * @returns 
     */
    static in<T extends number | string>(field: string, values: T[]): FilterArray<T> { return { [`${field}_in`]: values } }

    /**
     * Regex match (**case sensitive**)
     * @param field 
     * @param regex 
     * @returns 
     */
    static regex(field: string, regex: string): FilterElement<string> { return { [`${field}_regex`]: regex } }

    /**
     * Regex match (**case insensitive**)
     * @param field 
     * @param regex 
     * @returns 
     */
    static regex_i(field: string, regex: string): FilterElement<string> { return { [`${field}_regex_i`]: regex } }

    /**
     * Value is contained in an array
     * @param field 
     * @param value 
     * @returns 
     */
    static contains(field: string, value: string): FilterElement<string> { return { [`${field}_contains`]: value } }

    /**
     * Logical AND for all provided filters
     * @param filters 
     * @returns 
     */
    static and<T>(...filters: Filter<T>[]): FilterArray<Filter<T>> { return { _and: filters } }

    /**
     * Logical OR for all provided filters
     * @param filters 
     * @returns 
     */
    static or<T>(...filters: Filter<T>[]): FilterArray<Filter<T>> { return { _or: filters } }

    /**
     * Logical NOT for provided filter
     * @param filter 
     * @returns 
     */
    static not<T>(filter: Filter<T>): FilterElement<Filter<T>> { return { _not: filter } }

    /* EXTRA ADDED CONVENIENCE METHODS */
    /**
     * Starts with value (**case sensitive**)
     * @param field 
     * @param value 
     * @returns 
     */
    static startsWith(field: string, value: string): FilterElement<string> { return { [`${field}_regex`]: `^${value}.*` } }

    /**
     * Starts with value (**case insensitive**)
     * @param field 
     * @param value 
     * @returns 
     */
    static startsWith_i(field: string, value: string): FilterElement<string> { return { [`${field}_regex_i`]: `^${value}.*` } }

    /** 
     * Free form key, value. For custom nested properties
     */
    static nested<T>(field: string, value: T): Filter<T> { return { [field]: value } }
}

export type Filter<T> = FilterElement<T> | FilterArray<T>;
export type FilterElement<T> = Record<string, T>;
export type FilterArray<T> = Record<string, T[]>;

export class Utils {

    /**
     * Timer that works in two stages
     * @param firstPeriod Initial period, fires after first period-time every period
     * @param secondPeriod Second period, fires after first period-time every period
     * @param switchTimeFromStart Time from start to switch form first interval to second interval
     */
    static doubleInterval(firstPeriod: number, secondPeriod: number, switchTimeFromStart: number) {
        return timer(switchTimeFromStart).pipe(
            mapTo(true),
            startWith(false),
            switchMap(triggered => triggered ? interval(secondPeriod) : interval(firstPeriod))
        )
    }

    static logErrors(errors: GraphQLError[]) {
        const errorCss = 'background: #DA0093; color: #fff; padding: 3px 5px; font-size: 11px;';
        errors.forEach((err, idx) => {
            if (!err.hide) {
                const path = err.path && err.path.join('/');
                const locations = err.locations && err.locations.map(l => `[${l.line}:${l.column}]`).join(',');
                const msg = err.message;
                const nr = idx + 1;
                console.group(`%cGraphQL Error #${nr}`, errorCss)
                console.group(`message`);
                console.log(`${msg}`);
                console.groupEnd();
                console.groupCollapsed('path')
                console.log(path),
                    console.groupEnd();
                console.groupCollapsed('locations');
                console.log(locations);
                console.groupEnd();
                console.groupEnd();
            }
        });
    }

    static sumTimeSeries(timeSeries: TimeSeries[]): TimeSeries {
        if (timeSeries == null) {
            return {
                label: null,
                values: []
            }
        }

        if (timeSeries.length == 1) {
            return timeSeries[0];
        } else {
            const size = timeSeries.reduce((max, cur) => Math.max(max, cur.values.length), 0);
            const result: TimeSeries = {
                label: 'merged',
                values: []
            }
            const tplSeries = timeSeries.find(t => t.values.length == size);

            for (let idx = 0; idx < size; idx++) {
                result.values[idx] = {
                    timestamp: tplSeries.values[idx].timestamp,
                    value: timeSeries.reduce((total, cur) => total + cur?.values[idx]?.value ?? 0, 0)
                };
            }
            return result;
        }
    }

    static generateAvatarImgUrl(firstName: string | null, lastName: string | null, size: number = 32) {
        if (firstName == null) {
            firstName = "?";
        }
        if (lastName == null) {
            lastName = firstName
        }
        const char = firstName.substr(0, 1).toUpperCase();
        const canvas = document.createElement('canvas') as HTMLCanvasElement;
        const ctx = canvas.getContext('2d');
        const fontSize = size * .75;
        const xLoc = size * .5;
        const yLoc = xLoc + ((size * .25) / 4);
        canvas.width = size;
        canvas.height = size;
        const even = (firstName.length % 2) == 0
        const color = this.strToHslColor(lastName, 30, even ? 80 : 40)
        ctx.fillStyle = color;
        ctx.fillRect(0, 0, size, size);
        ctx.fillStyle = even ? '#555555' : '#efefef';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.font = `${fontSize}px sans-serif`;
        ctx.fillText(char, xLoc, yLoc, size);
        return canvas.toDataURL('image/png');
    }

    /**
     * Silences a Promise's dismiss reason
     * @returns dismiss handling function
     */
    static doNothing(): (dismissReason: string | void) => void {
        return ((dismissReason: string | void) => { });
    }

    static TODO(): void {
        alert("Not yet implemented");
    }

    private static strToHslColor(str: string, saturation: number, lightness: number): string {
        var hash = 0;
        for (var i = 0; i < str.length; i++) {
            hash = str.charCodeAt(i) + ((hash << 5) - hash);
        }

        var h = hash % 360;
        return `hsl(${h}, ${saturation}%,${lightness}%)`;
    }

    private static txtToNr(txt: string): number {
        return parseInt(txt.split('').map(c => c.charCodeAt(0)).join(''))
    }

    /**
     * Takes a function, bound with its this object that returns a Page<T> and fully unfolds it in a T[] array.
     * @param fn The function (eg. this.obj.function.bind(this.obj))
     * @param pageArgs Optional page arguments of cursor and limit
     */
    static pagesToArray<T>(fn: (pageArgs?: PageArgs) => Observable<Page<T>>, pageArgs?: PageArgs): Observable<T[]> {
        let cursor = pageArgs?.cursor;
        let limit = pageArgs?.limit;
        let args = null;
        if (cursor || limit) {
            args = { cursor, limit }
        }
        return fn(args).pipe(
            expand(page => {
                if (page.cursor !== null) {
                    return fn({ cursor: page.cursor, limit: args?.limit });
                } else {
                    return EMPTY;
                }
            }),
            concatMap(page => of(...page.items)),
            toArray()
        );
    }
}

/*
A function that first checks for error being present in graphql response, if so: map it, else do the pluck sequence.
*/
export function data<T>(...properties: string[]): (source: Observable<any>) => Observable<T> {
    return function <T>(source: Observable<any>): Observable<T> {
        return source.pipe(switchMap((obj: T) => {
            if (isGraphQLErrorResult(obj)) {
                Utils.logErrors(obj.errors);
            }
            return of(obj).pipe(pluck<T, T>('data', ...properties));
        }));
    }
}

/**
 * Hide graphql errors present from console logging.
 * Add this as first in the pipe to prevent logging from already being performed!
 * @returns source 
 */
export function hideErrors<T>() {
    return function <T>(source: Observable<T>): Observable<T> {
        return source.pipe(map((obj: any) => {
            if (obj?.errors && obj?.errors.length > 0) {
                obj.errors = obj.errors.map(err => {
                    err.hide = true;
                    return err;
                });
            }
            return obj;
        }))
    }
}

export class Reduce {
    public static mean(series: [number,number][]): number {
        if (series?.length > 0) {
            let nans = 0;
            let sum = series.reduce((total, cur) => {
                if (isNaN(cur[1])) {
                    nans++;
                    return total;
                } else {
                    return total + cur[1];
                }
            }, 0);
            return sum / (series.length - nans);
        } else {
            return 0;
        }
    }

    public static max(series: [number,number][]): number {
        if (series?.length > 0) {
            let sum = series.reduce((max, cur) => {
                if (isNaN(cur[1])) {
                    return max;
                } else {
                    return Math.max(max, cur[1]);
                }
            }, 0);
            return sum;
        } else {
            return 0;
        }
    }
}

export class Tx {

    public static round(num: number, precision: number = 0) {
        return Math.round(num * Math.pow(10, precision)) / Math.pow(10, precision);
    }

    public static number(value: number, precision: number = 0, prefix: boolean = true, niceFormatting: boolean = true): string {
        const roundInprecise = (input: number, log10: number) => {
            return log10 > 0 ? input.toString().slice(0, -log10) : input.toString();
        }
        const roundPrecise = (input: number, log10: number, precision: number) => {
            const val = (input / Math.pow(10, log10))
            if (niceFormatting && (Math.floor(val).toString(10).length >= 3)) {
                return Math.floor(val).toString(10);
            } else {
                const prec = val.toFixed(precision);
                if (parseInt(prec.substr(prec.length - precision)) == 0) {
                    return val.toFixed(0);
                } else {
                    return prec;
                }
            }
        };
        const round = (input: number, log10: number, precision: number) => (precision === 0) ? roundInprecise(input, log10) : roundPrecise(input, log10, precision);
        const p = prefix ? '+' : '';

        if (value == null) {
            return undefined;
        } else if (value > Math.pow(10, 18)) {
            return p + round(value, 18, precision) + 'Qi';
        } else if (value > Math.pow(10, 15)) {
            return p + round(value, 15, precision) + 'Qa';
        } else if (value > Math.pow(10, 12)) {
            return p + round(value, 12, precision) + 'T';
        } else if (value > Math.pow(10, 9)) {
            return p + round(value, 9, precision) + 'B';
        } else if (value > Math.pow(10, 6)) {
            return p + round(value, 6, precision) + 'M';
        } else if (value > Math.pow(10, 3)) {
            return p + round(value, 3, precision) + 'K';
        } else {
            return '' + round(value, 0, precision);
        }
    }

    public static fileSize(value: number, decimals: number = 2, inputUnit: 'B' | 'KB' | 'MB' | 'GB' = 'B'): any {
        const formatBytes = (bytes, decimals) => {
            if (bytes === 0) return '0 B';

            const k = 1024;
            const dm = decimals < 0 ? 0 : decimals;
            const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];

            const i = Math.floor(Math.log(bytes) / Math.log(k));

            try {
                return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
            } catch (err) {
                return '-';
            }
        };

        let parsed;

        if (value == null) {
            return '-'
        }

        switch (inputUnit) {
            default:
            case 'B':
                parsed = value;
                break;
            case 'KB':
                parsed = value * 1024;
                break;
            case 'MB':
                parsed = value * 1024 * 1024;
                break;
            case 'GB':
                parsed = value * 1024 * 1024 * 1024;
                break;
        }
        return formatBytes(parsed, decimals).replace('.', ',');
    }

}

export class Colors {

    static lightGreen = '#6FC95F';
    static darkGreen = '#3F7D34';
    static lightBrown = '#7E362F';
    static darkBrown = '#C26B34';
    static lightBlue = '#36C8C9';
    static darkBlue = '#287C7D';
}