import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { LoginRoutingModule } from './login-routing.module';

import * as fromPages from './pages';
import { SharedModule } from '@shared/shared.module';


@NgModule({
  declarations: [
    ...fromPages.pages,
  ],
  imports: [
    CommonModule,
    SharedModule,
    LoginRoutingModule
  ]
})
export class LoginModule { }
