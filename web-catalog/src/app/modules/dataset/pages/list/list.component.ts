import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { HeaderService, ObeliskService } from '@core/services';
import { ObeliskDataSource } from '@shared/datasources/obelisk-data-source';
import { Dataset } from '@shared/model';
import { FilterBuilder } from '@shared/utils';
import { debounceTime } from 'rxjs/operators';

@Component({
  selector: 'app-list',
  templateUrl: './list.component.html',
  styleUrls: ['./list.component.scss']
})
export class ListComponent implements OnInit, OnDestroy {
  searchForm: FormGroup;
  datasetSource: ObeliskDataSource<Partial<Dataset>>;

  constructor(
    private header: HeaderService,
    private obelisk: ObeliskService,
    fb: FormBuilder
  ) {
    this.searchForm = fb.group({
      search: []
    });
  }

  ngOnInit(): void {
    this.header.setTitle('Discover datasets');
    this.header.setSidebarState('none');
    const filter = term => FilterBuilder.regex_i('name',  term.trim());
    this.datasetSource = new ObeliskDataSource(this.obelisk.listPublishedDatasets.bind(this.obelisk), {filterFn: filter});
    this.searchForm.get('search').valueChanges.pipe(debounceTime(200)).subscribe((term: string) => this.datasetSource.queryRemote$.next(term));
  }

  ngOnDestroy() {
    this.datasetSource.cleanUp();
  }

}