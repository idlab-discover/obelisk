import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { HeaderService, ObeliskService } from '@core/services';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { ObeliskDataSource } from '@shared/datasources';
import { Issue } from '@shared/model';
import { TicketService } from 'app/modules/ticket/ticket.service';
import { startWith } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-tickets',
  templateUrl: './tickets.component.html',
  styleUrls: ['./tickets.component.scss']
})
export class TicketsComponent implements OnInit, OnDestroy {
  ticketSource: ObeliskDataSource<Issue>;
  filterForm: FormGroup;
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
  ]

  constructor(
    private obelisk: ObeliskService,
    private header: HeaderService,
    private tickets: TicketService,
    fb: FormBuilder
  ) {
    this.filterForm = fb.group({
      sort: [this.sortOptions[1].value],
      containsText: '',
      hideClosed: true,
    });
  }

  ngOnInit(): void {
    this.header.setTitle('My Tickets')
    const filter = term => this.filterForm.value;
    this.ticketSource = new ObeliskDataSource(this.obelisk.listPersonalIssues.bind(this.obelisk), { filterFn: filter });

    this.filterForm.valueChanges.pipe(startWith(this.filterForm.value)).subscribe(_ => {
      this.ticketSource.queryRemote$.next(this.filterForm.value);
    });

    // reload on refresh
    this.tickets.refresh$.pipe(untilDestroyed(this)).subscribe(_ => this.ticketSource.invalidate());
  }

  ngOnDestroy() {
    this.ticketSource.cleanUp();
  }

}
