import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ToastService } from '@core/services';
import { ConfirmService } from '@core/services/confirm.service';
import { ObeliskService } from '@core/services/obelisk.service';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { CreateHeaderComponent } from '@shared/components';
import { NgSelectDataSource } from '@shared/datasources/ng-select-data-source';
import { ObeliskDataSource } from '@shared/datasources/obelisk-data-source';
import { Dataset, User } from '@shared/model/types';
import { FilterBuilder } from '@shared/utils';
import { EMPTY, Subscription } from 'rxjs';
import { switchMap, tap } from 'rxjs/operators';


@UntilDestroy()
@Component({
  selector: 'app-datasets',
  templateUrl: './datasets.component.html',
  styleUrls: ['./datasets.component.scss']
})
export class DatasetsComponent implements OnInit, OnDestroy {
  dsForm: FormGroup;
  searchForm: FormGroup;
  datasets: Dataset[];
  liveSource: ObeliskDataSource<Dataset>;
  archivedSource: ObeliskDataSource<Dataset>;
  userDataSource: NgSelectDataSource<User>;
  users: Partial<User>[];

  constructor(
    fb: FormBuilder,
    private obelisk: ObeliskService,
    private confirm: ConfirmService,
    private toast: ToastService
  ) {
    this.searchForm = fb.group({
      search: ''
    });
    this.dsForm = fb.group({
      name: ['', Validators.required],
      manager: [null, Validators.required]
    });
    const liveFilter = term => FilterBuilder.and<any>(
      FilterBuilder.regex_i('name', term),
      FilterBuilder.eq('archived', false)
    );
    const archivedFilter = term => FilterBuilder.and<any>(
      FilterBuilder.regex_i('name', term),
      FilterBuilder.eq('archived', true)
    );

    this.liveSource = new ObeliskDataSource(this.obelisk.listAllDatasets.bind(this.obelisk), { filterFn: liveFilter });
    this.archivedSource = new ObeliskDataSource(this.obelisk.listAllDatasets.bind(this.obelisk), { filterFn: archivedFilter });
    this.userDataSource = new NgSelectDataSource<User>(this.obelisk.listAllUsers.bind(this.obelisk), { filterFn: term => FilterBuilder.regex_i('email', term) });
  }

  ngOnInit(): void {
    this.searchForm.get('search').valueChanges.pipe(
      // untilDestroyed(this),
    ).subscribe(term => {
      this.liveSource.queryRemote$.next(term)
      this.archivedSource.queryRemote$.next(term)
    });
  }

  ngOnDestroy() {
    this.liveSource.cleanUp();
    this.archivedSource.cleanUp();
    this.userDataSource.cleanUp();
  }

  add(comp: CreateHeaderComponent<any>): void {
    const name = this.dsForm.get('name').value;
    const ownerId = this.dsForm.get('manager').value;
    this.obelisk.createDataset(name, '', ownerId).subscribe(ok => {
      this.liveSource.invalidate()
      comp.setCollapsed(true);
    });
  }

  archive($event: MouseEvent, datasetId: string) {
    $event.preventDefault();
    $event.stopPropagation();
    this.confirm.areYouSure('Are you sure you want to archive this Dataset?')
      .pipe(switchMap(ok => ok ? this.obelisk.archiveDataset(datasetId) : EMPTY))
      .subscribe(res => {
        if (res.responseCode === 'SUCCESS') {
          this.liveSource.invalidate();
          this.archivedSource.invalidate();
          this.toast.show('Dataset archived');
        } else {
          this.toast.error('Could not archive dataset!');
        }
      });
  }

  unArchive($event: MouseEvent, datasetId: string) {
    $event.preventDefault();
    $event.stopPropagation();
    this.confirm.areYouSure('Are you sure you want to unarchive this Dataset?')
      .pipe(switchMap(ok => ok ? this.obelisk.unarchiveDataset(datasetId) : EMPTY))
      .subscribe(res => {
        if (res.responseCode === 'SUCCESS') {
          this.liveSource.invalidate();
          this.archivedSource.invalidate();
          this.toast.show('Dataset unarchived');
        } else {
          this.toast.error('Could not unarchive dataset!');
        }
      });
  }

  remove($event: MouseEvent, datasetId: string) {
    $event.preventDefault();
    $event.stopPropagation();
    this.confirm.areYouSure('Are you sure you want to <b>permanently</b> remove this Dataset?')
      .pipe(switchMap(ok => ok ? this.obelisk.removeDataset(datasetId) : EMPTY))
      .subscribe(res => {
        if (res.responseCode === 'SUCCESS') {
          this.liveSource.invalidate();
          this.archivedSource.invalidate();
          this.toast.show('Dataset removed');
        } else {
          this.toast.error('Could not remove dataset!');
        }
      });
  }

  onTabChange(activeId) {
    switch (activeId) {
      case 1: {
        this.liveSource.resetImmediate();
        break;
      }
      case 2: {
        this.archivedSource.resetImmediate();
        break;
      }
    }
    this.searchForm.reset({ search: '' }, { emitEvent: false });
  }
}