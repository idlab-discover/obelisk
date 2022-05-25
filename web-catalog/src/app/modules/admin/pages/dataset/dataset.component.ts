import { Component, OnInit } from '@angular/core';
import { FormBuilder } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

import { map, switchMap } from 'rxjs/operators';
import { ObeliskService } from '@core/services/obelisk.service';
import { Dataset } from '@shared/model';
import { ConfirmService, HeaderService, ToastService } from '@core/services';
import { EMPTY } from 'rxjs';

@Component({
  selector: 'app-dataset',
  templateUrl: './dataset.component.html',
  styleUrls: ['./dataset.component.scss']
})
export class DatasetComponent implements OnInit {
  dataset: Partial<Dataset>;

  constructor(private route: ActivatedRoute,
    private obelisk: ObeliskService,
    private toast: ToastService,
    private confirm: ConfirmService,
    private header: HeaderService,
    fb: FormBuilder) { }

  ngOnInit(): void {
    // this.header.setTitle('Dataset details');
    this.route.paramMap.pipe(map(p => p.get('datasetId'))).subscribe(datasetId => {
      this.loadDataset(datasetId);
    });
  }

  toggleLocked(): void {
    const newState = !this.dataset.archived;
    const id = this.dataset.id;
    const obs = this.dataset.archived ? this.obelisk.unarchiveDataset(id) : this.obelisk.archiveDataset(id);

    this.confirm.areYouSure('Are you sure you want to remove this Dataset?')
      .pipe(switchMap(ok => ok ? obs : EMPTY))
      .subscribe(result => {
        if (result.responseCode === 'SUCCESS') {
          this.loadDataset(this.dataset.id);
          this.toast.success(`Dataset ${newState ? 'locked' : 'unlocked'}`);
        }
        else {
          this.toast.error(`Error ${newState ? 'locking' : 'unlocking'} dataset`);
        }
      });
  }

  togglePublish(): void {
    const newState = !this.dataset.published;
    this.obelisk.setDatasetPublished(this.dataset.id, newState).subscribe(result => {
      if (result.responseCode === 'SUCCESS') {
        this.loadDataset(this.dataset.id);
        this.toast.success(`dataset ${newState ? 'published' : 'hidden'}`);
      }
      else {
        this.toast.error("could not change dataset published state")
      }
    });
  }

  toggleOpenData(): void {
    const newState = !this.dataset.openData;
    this.obelisk.setDatasetOpenData(this.dataset.id, newState).subscribe(result => {
      if (result.responseCode === 'SUCCESS') {
        this.loadDataset(this.dataset.id);
        this.toast.success(`dataset marked as ${newState ? '\'open data\'' : '\'closed data\''}`);
      }
      else {
        this.toast.error("could not change dataset openData state")
      }
    })
  }


  private loadDataset(datasetId: string) {
    this.obelisk.getDatasetAsAdmin(datasetId).subscribe(dataset => {
      this.dataset = dataset;
    });
  }
}
