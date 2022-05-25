import { AfterViewInit, Component, Input, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ObeliskService } from '@core/services';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { NgSelectComponent } from '@ng-select/ng-select';
import { NgSelectDataSource } from '@shared/datasources';
import { Dataset, Metric } from '@shared/model';
import { FilterBuilder } from '@shared/utils';

@Component({
  selector: 'app-data-range-picker',
  templateUrl: './data-range-picker.component.html',
  styleUrls: ['./data-range-picker.component.scss']
})
export class DataRangePickerComponent implements OnInit, AfterViewInit {
  @Input() name;
  @ViewChild('dsInput') dsInput: NgSelectComponent;
  datarangeForm: FormGroup;
  datasetSource: NgSelectDataSource<Dataset>;
  metricSource: NgSelectDataSource<Metric>;
  defineMetrics: boolean = false;

  constructor(
    public activeModal: NgbActiveModal,
    private obelisk: ObeliskService,
    fb: FormBuilder
  ) {
    this.datarangeForm = fb.group({
      dataset: [null, Validators.required],
      metrics: [null]
    });
  
    this.ds.valueChanges.subscribe(ds => this.reloadMetrics(ds.id));
  }

  ngOnInit(): void {
  }

  ngAfterViewInit(): void {
    this.dsInput.focus();
  }

  initForUser() {
    const filter = term => FilterBuilder.and<any>(
      FilterBuilder.nested('dataset', FilterBuilder.regex_i('name', term)),
      FilterBuilder.nested('aggregatedGrant', FilterBuilder.contains('permissions', 'READ'))
    );
    this.datasetSource = new NgSelectDataSource(this.obelisk.listReadableDatasets.bind(this.obelisk), { filterFn: filter });

  }

  initForTeam(teamId: string) {
    const filter = term => FilterBuilder.and<any>(
      FilterBuilder.nested('dataset', FilterBuilder.regex_i('name', term)),
      FilterBuilder.nested('aggregatedGrant', FilterBuilder.contains('permissions', 'READ'))
    );
    this.datasetSource = new NgSelectDataSource(this.obelisk.listTeamReadableDatasets.bind(this.obelisk, teamId), { filterFn: filter });
  }

  toggleAddMetrics() {
    this.defineMetrics = !this.defineMetrics
    if (this.defineMetrics) {
      this.ds.disable();
    } else {
      this.ds.enable();
    }
  }

  get ds() {
    return this.datarangeForm.get('dataset');
  }

  finish() {
    const dataset = this.ds.value;
    const metrics = this.datarangeForm.get('metrics').value;
    const result = { dataset, metrics }
    this.activeModal.close(result);
  }

  private reloadMetrics(datasetId: string) {
    if (this.metricSource != null) {
      this.metricSource.cleanUp();
    }
    this.metricSource = new NgSelectDataSource(this.obelisk.listMetrics.bind(this.obelisk, datasetId), { filterFn: term => FilterBuilder.regex_i('id', term) });
  }
}
