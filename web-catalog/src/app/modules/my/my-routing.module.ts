import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '@shared/guards';
import { ClientComponent, ClientsComponent, DatasetsComponent, ProfileComponent, MyComponent, TeamsComponent, RateLimitComponent, ExportsComponent, AccessRequestsComponent, StreamsComponent, SettingsComponent, TicketsComponent, TicketComponent, RemovalComponent } from './pages';

const routes: Routes = [
  {
    path: '',
    component: MyComponent,
    data: { animation: 'dataset' },
    canActivate: [AuthGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'profile'
      },
      { path: 'profile', component: ProfileComponent },
      { path: 'clients', component: ClientsComponent},
      { path: 'clients/:clientId', component: ClientComponent},
      { path: 'datasets', component: DatasetsComponent},
      { path: 'teams', component: TeamsComponent},
      { path: 'accessrequests', component: AccessRequestsComponent},
      { path: 'ratelimit', component: RateLimitComponent},
      { path: 'exports', component: ExportsComponent},
      { path: 'streams', component: StreamsComponent},
      { path: 'settings', component: SettingsComponent},
      { path: 'tickets', component: TicketsComponent},
      { path: 'tickets/:ticketId', component: TicketComponent},
      { path: 'removal', component: RemovalComponent},

    ]
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class MyRoutingModule { }
