import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { SharedModule } from '@shared/shared.module';

import * as fromComponents from './components';
import * as fromPages from './pages';

@NgModule({
  declarations: [
    ...fromComponents.components,
    ...fromPages.pages,
  ],
  imports: [
    CommonModule,
    SharedModule
  ]
})
export class HomeModule { }
