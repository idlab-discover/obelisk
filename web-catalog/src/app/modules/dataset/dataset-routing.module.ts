import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DatasetHeaderResolver, DatasetResolver } from '@core/resolvers';
import { AuthGuard, DatasetGuard } from "@shared/guards";
import { AccessComponent, AccessRequestsComponent, DatasetComponent, EditComponent, InsightComponent, InvitesComponent, ListComponent, MembershipInfoComponent, MetricComponent, MetricsComponent, OriginsComponent, OverviewComponent, PeekComponent, ReadmeComponent, RoleComponent, RolesComponent, ThingComponent, ThingsComponent } from "./pages";

const routes: Routes = [
  {
    path: '',
    component: ListComponent
  },
  {
    path: ':id',
    component: DatasetComponent,
    data: { animation: 'dataset' },
    resolve: { header: DatasetHeaderResolver },
    canActivate: [AuthGuard],
    canActivateChild: [DatasetGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'home'
      },
      { path: 'home', component: OverviewComponent, resolve: { dataset: DatasetResolver } },
      { path: 'metrics', component: MetricsComponent, resolve: { dataset: DatasetResolver } },
      { path: 'metrics/:metricId', component: MetricComponent, resolve: { dataset: DatasetResolver } },
      { path: 'things', component: ThingsComponent, resolve: { dataset: DatasetResolver } },
      { path: 'things/:thingId', component: ThingComponent, resolve: { dataset: DatasetResolver } },
      { path: 'readme', component: ReadmeComponent, resolve: { dataset: DatasetResolver } },
      { path: 'origins', component: OriginsComponent, resolve: { dataset: DatasetResolver } },
      { path: 'insight', component: InsightComponent, resolve: { dataset: DatasetResolver } },
      { path: 'membership-info', component: MembershipInfoComponent, resolve: { dataset: DatasetResolver } },

      { path: 'invites', component: InvitesComponent, resolve: { dataset: DatasetResolver }, data: { managerOnly: true } },
      { path: 'accessrequests', component: AccessRequestsComponent, resolve: { dataset: DatasetResolver }, data: { managerOnly: true } },
      { path: 'access', component: AccessComponent, resolve: { dataset: DatasetResolver }, data: { managerOnly: true } },
      { path: 'roles', component: RolesComponent, resolve: { dataset: DatasetResolver }, data: { managerOnly: true } },
      { path: 'roles/:roleId', component: RoleComponent, resolve: { dataset: DatasetResolver }, data: { managerOnly: true } },
      { path: 'edit', component: EditComponent, resolve: { dataset: DatasetResolver }, data: { managerOnly: true } },

      { path: 'peek', component: PeekComponent, resolve: { dataset: DatasetResolver } },
    ]
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class DatasetRoutingModule { }
