import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { ClientComponent, ClientsComponent, EditComponent, HomeComponent, InvitesComponent, MembersComponent, RateLimitComponent, TeamDatasetsComponent, TeamExportsComponent, TeamsComponent, TeamStreamsComponent } from "./pages";
import { AuthGuard, TeamGuard } from "@shared/guards";
import { TeamHeaderResolver, TeamResolver } from '@core/resolvers';


const routes: Routes = [{
  path: ':id',
  component: TeamsComponent,
  canActivate: [AuthGuard],
  canActivateChild: [TeamGuard],
  resolve: { header: TeamHeaderResolver },
  children: [
    {
      path: '',
      pathMatch: 'full',
      redirectTo: 'home'
    },
    { path: 'home', component: HomeComponent, resolve: { team: TeamResolver } },
    { path: 'ds', component: TeamDatasetsComponent, resolve: { team: TeamResolver } },
    { path: 'clients', component: ClientsComponent, resolve: { team: TeamResolver } },
    { path: 'clients/:clientId', component: ClientComponent, resolve: { team: TeamResolver } },
    { path: 'invites', component: InvitesComponent, resolve: { team: TeamResolver }, data: { managerOnly: true } },
    { path: 'members', component: MembersComponent, resolve: { team: TeamResolver } },
    { path: 'ratelimit', component: RateLimitComponent, resolve: { team: TeamResolver } },
    { path: 'streams', component: TeamStreamsComponent, resolve: { team: TeamResolver } },
    { path: 'exports', component: TeamExportsComponent, resolve: { team: TeamResolver } },
    { path: 'edit', component: EditComponent, resolve: { team: TeamResolver }, data: { managerOnly: true } },
  ]
},];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TeamsRoutingModule { }
