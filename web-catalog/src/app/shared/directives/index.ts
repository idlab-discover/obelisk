import { AllowTabDirective } from "./allow-tab.directive";
import { SidebarToggleDirective } from "./sidebar-toggle.directive";
import { DownloadFileDirective } from "./download-file.directive";
import { ColspannerDirective } from "./colspanner.directive";

export const directives: any[] = [
    AllowTabDirective,
    SidebarToggleDirective,
    DownloadFileDirective,
    ColspannerDirective
];

export * from './allow-tab.directive';
export * from './sidebar-toggle.directive';
export * from './download-file.directive';
export * from './colspanner.directive';