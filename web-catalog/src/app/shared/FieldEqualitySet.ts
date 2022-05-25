export class FieldEqualitySet<T> {
    private map: Map<string, T>;

    constructor(private field: string, items?: T[]) {
        this.map = new Map();
        if (items) {
            for (let idx = 0; idx < items.length; idx++) {
                const el = items[idx];
                this.add(el);
            }
        }
    }

    add(element: T) {
        this.check(element);
        this.map.set(this.f(element), element);
    }

    remove(element: T): boolean {
        this.check(element);
        return this.map.delete(this.f(element))
    }

    has(element: T) {
        this.check(element);
        return this.map.has(this.f(element))
    }

    values(): IterableIterator<T> {
        return this.map.values();
    }

    toArray(): T[] {
        return Array.from(this.values());
    }

    private check(element: T) {
        if (!(this.field in element)) {
            throw Error('Element to remove must contain configured equality field: \'' + this.field + '\'');
        }
    }

    private f(element: T): string {
        return element[this.field];
    }
}