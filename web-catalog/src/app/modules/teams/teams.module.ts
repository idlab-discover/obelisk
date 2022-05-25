import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { SharedModule } from '@shared/shared.module';
import * as fromPages from './pages';
import { TeamsRoutingModule } from './teams-routing.module';

@NgModule({
  declarations: [
    ...fromPages.pages
  ],
  imports: [
    CommonModule,
    SharedModule,
    TeamsRoutingModule
  ]
})
export class TeamsModule { }
