import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { LoginComponent, NewTicketComponent } from './pages';
import { TicketAuthGuard } from "./ticket-auth.guard";

const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'new',
  },
  {
    path: 'new',
    component: NewTicketComponent,
    canActivate: [TicketAuthGuard]
  },
  {
    path: 'auth',
    component: LoginComponent
  },
  
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TicketRoutingModule { }
