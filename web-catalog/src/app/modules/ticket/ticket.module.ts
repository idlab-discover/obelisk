import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SharedModule } from '@shared/shared.module';
import { TicketRoutingModule} from './ticket-routing.module';

import * as fromPages from './pages';

@NgModule({
  declarations: [
    ... fromPages.pages,
  ],
  imports: [
    CommonModule,
    SharedModule,
    TicketRoutingModule
  ]
})
export class TicketModule { }
