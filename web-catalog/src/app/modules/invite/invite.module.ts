import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SharedModule } from '@shared/shared.module';

import { InviteRoutingModule } from './invite-routing.module';

import * as fromPages from './pages';

@NgModule({
  declarations: [
    ...fromPages.pages,
  ],
  imports: [
    CommonModule,
    SharedModule,
    InviteRoutingModule
  ]
})
export class InviteModule { }
