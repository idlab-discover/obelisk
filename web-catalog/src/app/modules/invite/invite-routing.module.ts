import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { InviteComponent } from './pages';
import { AuthGuard } from "@shared/guards";

const routes: Routes = [
  {
    path: '',
    component: InviteComponent,
    canActivate: [AuthGuard],
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class InviteRoutingModule { }
