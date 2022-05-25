import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ObeliskService } from '@core/services';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { ObeliskDataSource } from '@shared/datasources';
import { Issue } from "@shared/model/types";
import { TicketService } from 'app/modules/ticket/ticket.service';
import { concat, of } from 'rxjs';
import { startWith } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-tickets',
  templateUrl: './tickets.component.html',
  styleUrls: ['./tickets.component.scss']
})
export class TicketsComponent implements OnInit, OnDestroy {
  ticketSource: ObeliskDataSource<Issue>;
  filterForm: FormGroup
  sortOptions = [
    {
      label: 'created a &rarr; z',
      value: 'created_at_asc'
    },
    {
      label: 'created z &rarr; a',
      value: 'created_at_desc'
    },
    {
      label: 'updated a &rarr; z',
      value: 'modified_at_asc'
    },
    {
      label: 'updated z &rarr; a',
      value: 'modified_at_desc'
    }
  ];
  states = ['WAITING_FOR_SUPPORT', 'WAITING_FOR_REPORTER', 'IN_PROGRESS', 'CANCELLED', 'RESOLVED'];

  private defaults: any;

  constructor(
    private obelisk: ObeliskService,
    private tickets: TicketService,
    fb: FormBuilder
  ) {
    this.filterForm = fb.group({
      sort: [this.sortOptions[1].value],
      containsText: '',
      hideClosed: [true],
      status: null
    });
    this.defaults = this.filterForm.value;
  }

  ngOnInit(): void {
    const filter = term => this.filterForm.value;
    this.ticketSource = new ObeliskDataSource(this.obelisk.listAllIssues.bind(this.obelisk), {filterFn: filter});
    this.filterForm.valueChanges.subscribe(change => {
      this.ticketSource?.queryRemote$.next(change);
    });
    
    setTimeout(() => {
      this.filterForm.reset(this.defaults);
    }, 0);

    // reload on refresh
    this.tickets.refresh$.pipe(untilDestroyed(this)).subscribe(_ => this.ticketSource.invalidate());
  }

  ngOnDestroy() {
    this.ticketSource.cleanUp();
  }

}
