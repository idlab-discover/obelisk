import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { SharedModule } from '@shared/shared.module';
import { MyRoutingModule } from './my-routing.module';
import * as fromPages from './pages';

@NgModule({
  declarations: [
    ...fromPages.pages
  ],
  imports: [
    CommonModule,
    SharedModule,
    MyRoutingModule,
  ]
})
export class MyModule { }
