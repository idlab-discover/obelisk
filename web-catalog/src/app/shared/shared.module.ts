import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ScrollingModule } from '@angular/cdk/scrolling';

// Third party
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgSelectModule } from '@ng-select/ng-select';
import { NgApexchartsModule } from 'ng-apexcharts';
import { MarkdownModule } from 'ngx-markdown';
import { CodemirrorModule } from '@ctrl/ngx-codemirror';
import {
  NgbButtonsModule,
  NgbProgressbarModule,
  NgbDatepickerModule,
  NgbModalModule,
  NgbToastModule,
  NgbAlertModule,
  NgbDropdownModule,
  NgbCollapseModule,
  NgbTooltipModule,
  NgbNavModule
} from '@ng-bootstrap/ng-bootstrap';

// Proprietary
import * as fromComponents from './components';
import * as fromDirectives from './directives';
import * as fromModals from './modals';
import * as fromPipes from './pipes';
import * as fromPages from './pages';

// CodeMirror
import 'codemirror/mode/markdown/markdown';
import 'codemirror/mode/javascript/javascript';
import 'codemirror/addon/edit/closebrackets';
import 'codemirror/addon/edit/matchbrackets';
import 'codemirror/addon/hint/show-hint';
import 'codemirror/addon/display/autorefresh';
import { LicenseComponent } from './components/license/license.component';

const exposedModules: any[] = [
  FormsModule,
  RouterModule,
  FormsModule,
  ReactiveFormsModule,
  FontAwesomeModule,
  NgApexchartsModule,
  NgSelectModule,
  ScrollingModule,
  MarkdownModule,
  CodemirrorModule,
  NgbButtonsModule,
  NgbProgressbarModule,
  NgbDatepickerModule,
  NgbModalModule,
  NgbToastModule,
  NgbAlertModule,
  NgbDropdownModule,
  NgbCollapseModule,
  NgbTooltipModule,
  NgbNavModule
]

/**
 * In the Shared Module we place all reusable components, pipes and directives. 
 * Since this module can be imported in multiple other modules, singleton services 
 * should **not** be part of this module.
 * Other modules that are not to be declared only once, can be added here too 
 * (like FormsModule) and exported.
 */
@NgModule({
  declarations: [
    ...fromComponents.components,
    ...fromDirectives.directives,
    ...fromModals.modals,
    ...fromPipes.pipes,
    ...fromPages.pages,
  ],
  imports: [
    CommonModule,
    RouterModule, // Only for Adaptive Header Component routerLink usage
    ...exposedModules
  ],
  providers: [
    ...fromPipes.pipes
  ],
  exports: [
    ...exposedModules,
    ...fromComponents.components,
    ...fromDirectives.directives,
    ...fromModals.modals,
    ...fromPipes.pipes,
    ...fromPages.pages,
  ]
})
export class SharedModule { }
