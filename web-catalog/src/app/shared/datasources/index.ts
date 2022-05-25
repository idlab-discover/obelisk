import { CarouselDataSource } from "./carousel-data-source";
import { FullSource } from "./full-source";
import { NgSelectDataSource } from "./ng-select-data-source";
import { ObeliskDataSource } from "./obelisk-data-source";

export const datasources: any[] = [
    CarouselDataSource,
    FullSource,
    NgSelectDataSource,
    ObeliskDataSource
];

export * from "./carousel-data-source";
export * from "./full-source";
export * from "./ng-select-data-source";
export * from "./obelisk-data-source";