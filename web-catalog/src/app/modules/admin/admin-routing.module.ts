import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { AdminGuard } from '@shared/guards/admin.guard';
import { AdminComponent, ClientComponent, ClientsComponent, DatasetComponent, DatasetsComponent, TeamComponent, TeamsComponent, UsageLimitComponent, UsageLimitsComponent, UsagePlanComponent, UsagePlansComponent, UsersComponent, UserComponent, NewsComponent, NewsItemComponent, TicketsComponent, TicketComponent, AdvancedStatusComponent } from './pages';

const routes: Routes = [
  {
    path: '',
    component: AdminComponent,
    data: { animation: 'dataset' },
    canActivate: [AdminGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'datasets'
      },
      { path: 'news', component: NewsComponent },
      { path: 'news/:newsId', component: NewsItemComponent },
      { path: 'users', component: UsersComponent },
      { path: 'users/:userId', component: UserComponent },
      { path: 'clients', component: ClientsComponent },
      { path: 'clients/:clientId', component: ClientComponent },
      { path: 'datasets', component: DatasetsComponent },
      { path: 'datasets/:datasetId', component: DatasetComponent },
      { path: 'usagelimit', component: UsageLimitsComponent },
      { path: 'usagelimit/:usageLimitId', component: UsageLimitComponent },
      { path: 'usageplan', component: UsagePlansComponent },
      { path: 'usageplan/:usagePlanId', component: UsagePlanComponent },
      { path: 'teams', component: TeamsComponent },
      { path: 'teams/:teamId', component: TeamComponent },
      { path: 'tickets', component: TicketsComponent },
      { path: 'tickets/:ticketId', component: TicketComponent },
      { path: 'status', component: AdvancedStatusComponent }
    ]
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class AdminRoutingModule { }
