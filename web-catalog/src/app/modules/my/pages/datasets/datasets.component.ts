import { Component, OnDestroy, OnInit } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ConfirmService, HeaderService, ToastService } from '@core/services';
import { ObeliskService } from '@core/services/obelisk.service';
import { CreateHeaderComponent } from '@shared/components';
import { ObeliskDataSource } from '@shared/datasources/obelisk-data-source';
import { Dataset } from '@shared/model/types';
import { EMPTY } from 'rxjs';
import { switchMap } from 'rxjs/operators';


@Component({
  selector: 'app-datasets',
  templateUrl: './datasets.component.html',
  styleUrls: ['./datasets.component.scss']
})
export class DatasetsComponent implements OnInit, OnDestroy {
  dsForm: FormGroup;
  datasetSource: ObeliskDataSource<Partial<Dataset>>;

  constructor(
    private obelisk: ObeliskService,
    private router: Router,
    private confirm: ConfirmService,
    private toast: ToastService,
    private header: HeaderService,
    fb: FormBuilder
  ) {
    this.dsForm = fb.group({
      name: ['', Validators.required],
      description: ['', Validators.maxLength(255)]
    });
    this.datasetSource = new ObeliskDataSource(this.obelisk.listMyDatasets.bind(this.obelisk));
   }

  ngOnInit(): void {
    this.header.setTitle('My Datasets');
  }

  ngOnDestroy() {
    this.datasetSource.cleanUp();
  }
  
  add(comp: CreateHeaderComponent<any>): void {
    const name = this.dsForm.get('name').value;
    const description = this.dsForm.get('description').value;
    const ownerId = null;
    this.obelisk.createDataset(name, description, ownerId).subscribe(ok => {
      this.dsForm.reset();
      this.datasetSource.invalidate()
      comp.setCollapsed(true);
    });
  }

  lock($event: MouseEvent, datasetId: string) {
    $event.preventDefault();
    $event.stopPropagation();
    this.confirm.areYouSure('Are you sure you want to remove this Dataset?')
    .pipe(switchMap(ok => ok ? this.obelisk.archiveDataset(datasetId) : EMPTY))
      .subscribe(res => {
        if (res.responseCode === 'SUCCESS') {
          this.datasetSource.invalidate();
          this.toast.success('Dataset locked');
        } else {
          this.toast.error('Could not lock dataset!');
        }
      });
  }

  unlock($event: MouseEvent, datasetId: string) {
    $event.preventDefault();
    $event.stopPropagation();
    this.confirm.areYouSure('Are you sure you want to remove this Dataset?')
    .pipe(switchMap(ok => ok ? this.obelisk.unarchiveDataset(datasetId) : EMPTY))
      .subscribe(res => {
        if (res.responseCode === 'SUCCESS') {
          this.datasetSource.invalidate();
          this.toast.success('Dataset locked');
        } else {
          this.toast.error('Could not lock dataset!');
        }
      });
  }

  manageTeamAccess($event: Event, id: string) {
    $event.preventDefault();
    $event.stopPropagation();
    this.router.navigate(['ds', id, 'invites'], {fragment: 't'});
  }

  get desc(): AbstractControl {   
    return this.dsForm?.get('description');
  }

}
