import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { SharedModule } from '@shared/shared.module';
import { AdminRoutingModule } from './admin-routing.module';

import * as fromPages from './pages';

@NgModule({
  declarations: [
    ...fromPages.pages
  ],
  imports: [
    CommonModule,
    AdminRoutingModule,
    SharedModule
  ]
})
export class AdminModule { }
