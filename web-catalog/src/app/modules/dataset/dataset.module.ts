import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { SharedModule } from '@shared/shared.module';
import { DatasetRoutingModule } from './dataset-routing.module';
import * as fromPages from './pages';

@NgModule({
  declarations: [
    ...fromPages.pages,
  ],
  imports: [
    CommonModule,
    SharedModule,
    DatasetRoutingModule
  ]
})
export class DatasetModule { }
