import { MyComponent } from "./my.component";
import { ClientComponent } from "./client/client.component";
import { ClientsComponent } from "./clients/clients.component";
import { DatasetsComponent } from "./datasets/datasets.component";
import { ProfileComponent } from "./profile/profile.component";
import { TeamsComponent } from "./teams/teams.component";
import { RateLimitComponent } from "./rate-limit/rate-limit.component";
import { ExportsComponent } from "./exports/exports.component";
import { AccessRequestsComponent } from "./access-requests/access-requests.component";
import { StreamsComponent } from "./streams/streams.component";
import { SettingsComponent } from './settings/settings.component';
import { TicketsComponent } from './tickets/tickets.component';
import { TicketComponent } from './ticket/ticket.component';
import { RemovalComponent } from "./removal/removal.component";

export const pages: any[] = [
    MyComponent,
    ClientComponent,
    ClientsComponent,
    DatasetsComponent,
    ProfileComponent,
    TeamsComponent,
    RateLimitComponent,
    ExportsComponent,
    AccessRequestsComponent,
    StreamsComponent,
    SettingsComponent,
    TicketsComponent,
    TicketComponent,
    RemovalComponent
];

export * from "./my.component";
export * from "./client/client.component";
export * from "./clients/clients.component";
export * from "./datasets/datasets.component";
export * from "./profile/profile.component";
export * from "./teams/teams.component";
export * from "./rate-limit/rate-limit.component";
export * from "./exports/exports.component";
export * from "./access-requests/access-requests.component";
export * from "./streams/streams.component";
export * from "./settings/settings.component";
export * from "./tickets/tickets.component";
export * from "./ticket/ticket.component";
export * from "./removal/removal.component";