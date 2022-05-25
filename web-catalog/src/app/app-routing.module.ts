import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AdminGuard, AuthGuard, OpenGuard } from '@shared/guards';
import { ErrorComponent, NotFoundComponent } from '@shared/pages';
import { HomeComponent, StatusComponent } from './modules/home/pages';

const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'home'
  },
  {
    path: 'home',
    component: HomeComponent,
    canActivate: [OpenGuard]
  },
  {
    path: 'status',
    component: StatusComponent,
    canActivate: [OpenGuard]
  },
  {
    path: 'ds',
    loadChildren: () => import('./modules/dataset/dataset.module').then(m => m.DatasetModule),
    canActivate: [OpenGuard]
  },
  {
    path: 'teams',
    loadChildren: () => import('./modules/teams/teams.module').then(m => m.TeamsModule)
  },
  {
    path: 'my',
    loadChildren: () => import('./modules/my/my.module').then(m => m.MyModule)
  },
  {
    path: 'admin',
    loadChildren: () => import('./modules/admin/admin.module').then(m => m.AdminModule),
    canActivate: [AdminGuard]
  },
  {
    path: 'login',
    loadChildren: () => import('./modules/login/login.module').then(m => m.LoginModule)
  },
  {
    path: 'invite',
    loadChildren: () => import('./modules/invite/invite.module').then(m => m.InviteModule)
  },
  {
    path: 'error',
    component: ErrorComponent
  },
  /* CATCHALL */
  {
    path: '**',
    component: NotFoundComponent
  },
  {
    path: 'ticket',
    outlet:'x',
    loadChildren: () => import('./modules/ticket/ticket.module').then(m => m.TicketModule),
  }

];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
