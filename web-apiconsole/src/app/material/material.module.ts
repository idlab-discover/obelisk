import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSidenavModule } from '@angular/material/sidenav';

const MODULES = [
  MatButtonToggleModule,
  MatToolbarModule,
  MatIconModule,
  MatSelectModule,
  MatInputModule,
  MatButtonModule,
  MatTooltipModule,
  MatSidenavModule
];

@NgModule({
  declarations: [],
  imports: [
    CommonModule,
    ...MODULES
  ],
  exports: [
    ...MODULES
  ]
})
export class MaterialModule { }
