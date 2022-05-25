import { TeamsComponent } from "./teams.component";
import { HomeComponent } from "./home/home.component";
import { MembersComponent } from "./members/members.component";
import { InvitesComponent } from "./invites/invites.component";
import { RateLimitComponent } from "./rate-limit/rate-limit.component";
import { EditComponent } from "./edit/edit.component";
import { ClientsComponent } from "./clients/clients.component";
import { ClientComponent } from "./client/client.component";
import { TeamStreamsComponent} from "./team-streams/team-streams.component";
import { TeamExportsComponent} from "./team-exports/team-exports.component";
import { TeamDatasetsComponent } from "./team-datasets/team-datasets.component";

export const pages: any[] = [
    TeamsComponent,
    HomeComponent,
    MembersComponent,
    InvitesComponent,
    RateLimitComponent,
    EditComponent,
    ClientsComponent,
    ClientComponent,
    TeamStreamsComponent,
    TeamExportsComponent,
    TeamDatasetsComponent
];

export * from './teams.component';
export * from './home/home.component';
export * from './members/members.component';
export * from './invites/invites.component';
export * from './rate-limit/rate-limit.component';
export * from './edit/edit.component';
export * from './clients/clients.component';
export * from './client/client.component';
export * from './team-streams/team-streams.component';
export * from './team-exports/team-exports.component';
export * from './team-datasets/team-datasets.component';