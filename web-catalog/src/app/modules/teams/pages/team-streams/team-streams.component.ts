import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ConfirmService, HeaderService, ObeliskService, ResponseHandlerService, ToastService } from '@core/services';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CreateHeaderComponent } from '@shared/components';
import { ObeliskDataSource } from '@shared/datasources';
import { FieldEqualitySet } from '@shared/FieldEqualitySet';
import { DataRangePickerComponent, FilterEditorComponent, StreamInfoComponent } from '@shared/modals';
import { CreateStreamInput, Dataset, DataStream, EventField, EventFieldTuple, EventField_ALL, FilterExpressionSchema, Team, TimestampPrecision, TimestampPrecisionTuple, TimestampPrecision_ALL } from '@shared/model';
import { FilterBuilder, Utils } from '@shared/utils';

@Component({
  selector: 'app-team-streams',
  templateUrl: './team-streams.component.html',
  styleUrls: ['./team-streams.component.scss']
})
export class TeamStreamsComponent implements OnInit, OnDestroy {
  streamSource: ObeliskDataSource<DataStream>;
  streamsForm: FormGroup;

  rangeDatasets: Partial<Dataset>[] = [];
  rangeMetrics: string[] = [];

  allFields: EventFieldTuple;
  allPrecision: TimestampPrecisionTuple;

  private team: Team;

  constructor(private obelisk: ObeliskService,
    private modal: NgbModal,
    private toast: ToastService,
    private confirm: ConfirmService,
    private header: HeaderService,
    private route: ActivatedRoute,
    private respHandler: ResponseHandlerService,
    fb: FormBuilder) {
    this.allFields = EventField_ALL;
    this.allPrecision = TimestampPrecision_ALL;
    this.streamsForm = fb.group({
      name: ['', Validators.required],
      dataRange: [null],
      filter: {},
      fields: [],
      timestampPrecision: ['MILLISECONDS', Validators.required],
      selectedDataset: [],
      selectedMetric: []
    });
  }

  ngOnInit(): void {
    this.route.data
      .subscribe(data => {
        this.team = data.team;
        const filter = term => FilterBuilder.regex_i('name', term);
        this.streamSource = new ObeliskDataSource(this.obelisk.listTeamStreams.bind(this.obelisk, this.team.id), { filterFn: filter })
      });
    this.header.setTitle('My Streams');
  }

  ngOnDestroy() {
    this.streamSource.cleanUp();
  }

  addStream(comp: CreateHeaderComponent<any>) {
    if (!this.isFormValid()) {
      return;
    }
    const name = this.streamsForm.get('name').value as string;
    const datasets = this.rangeDatasets.map(d => d.id!!);
    const metrics = this.rangeMetrics;
    const dataRange = { datasets, metrics }
    const filter = this.streamsForm.get('filter').value || {};
    const fields = this.streamsForm.get('fields').value as EventField[] || [];
    const timestampPrecision = this.streamsForm.get('timestampPrecision').value as TimestampPrecision;
    const input: CreateStreamInput = { name, dataRange, filter, fields, timestampPrecision };
    this.obelisk.createTeamStream(this.team.id, input)
      .subscribe(resp => this.respHandler.observe(resp, {
        success: _ => {
          this.resetForm();
          this.streamSource.invalidate();
          comp.setCollapsed(true);
        }
      }));

  }

  removeStream(id: string, event?: MouseEvent) {
    this.confirm.areYouSureThen(
      "Are you sure you want to remove this stream?",
      this.obelisk.removeTeamStream(this.team.id, id)
    )
      .subscribe(resp => this.respHandler.observe(resp, {
        success: _ => {
          this.toast.show("Stream removed");
          this.streamSource.invalidate();
        }
      }));
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
  }

  endStream(id: string, event?: MouseEvent) {
    this.confirm.areYouSureThen(
      "Are you sure you want to end any active session on this stream?<br><i>This will not remove the stream, but disconnect the subscriber if there is one.</i>",
      this.obelisk.endTeamStreamSession(this.team.id, id)
    )
      .subscribe(resp => this.respHandler.observe(resp, {
        success: _ => {
          this.toast.show("End stream sent");
          this.streamSource.invalidate();
        }
      }));
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
  }



  isFormValid() {
    return this.streamsForm.valid && this.rangeDatasets.length > 0;
  }

  refresh() {
    this.streamSource.invalidate();
  }

  showInfo(stream: DataStream) {
    const ref = this.modal.open(StreamInfoComponent, { size: 'lg' });
    ref.componentInstance.initFromStream(stream);

    ref.result.then(action => {
      if ('remove' == action) {
        this.removeStream(stream.id);
      }
      else if ('end' == action) {
        this.endStream(stream.id);
      }
    }, Utils.doNothing);
  }

  openDataRangeWizard() {
    const ref = this.modal.open(DataRangePickerComponent, { size: 'lg' });
    (ref.componentInstance as DataRangePickerComponent).initForTeam(this.team.id);
    ref.result.then(result => {
      const ds = result.dataset;
      const metrics = result.metrics;
      if (ds != null) {
        const s = new FieldEqualitySet('id', this.rangeDatasets.concat(ds));
        this.rangeDatasets = new FieldEqualitySet('id', this.rangeDatasets.concat(ds)).toArray()
      }
      if (metrics && metrics.length > 0) {
        this.rangeMetrics = Array.from(new Set(this.rangeMetrics.concat(metrics)));
      }
    }, Utils.doNothing())
  }

  removeMetric() {
    const metric = this.streamsForm.get('selectedMetric').value;
    const idx = this.rangeMetrics.indexOf(metric);
    this.rangeMetrics.splice(idx, 1);
    this.streamsForm.get('selectedMetric').reset();
  }

  removeDataset() {
    const dataset = this.streamsForm.get('selectedDataset').value;
    const idx = this.rangeDatasets.findIndex(ds => ds.id === dataset.id);
    this.rangeDatasets.splice(idx, 1);
    this.streamsForm.get('selectedDataset').reset();
  }

  isDatasetSelected(): boolean {
    return this.rangeDatasets.length > 0 && this.streamsForm.get('selectedDataset').value != null;
  }

  isMetricSelected(): boolean {
    return this.rangeMetrics.length > 0 && this.streamsForm.get('selectedMetric').value != null;
  }

  openFilterPanel() {
    const ref = this.modal.open(FilterEditorComponent, { size: 'xl', backdrop: 'static', windowClass: 'special' });
    ref.componentInstance.init(this.streamsForm.get('filter').value, FilterExpressionSchema);

    ref.result.then(
      newFilter => {
        this.streamsForm.get('filter').setValue(newFilter);
      },
      Utils.doNothing);
  }

  selectAllFields() {
    const fields = this.streamsForm.get('fields');
    fields.setValue(this.allFields);
  }

  getSpinnerStyle(clientConnected: boolean) {
    return {
      'text-success': clientConnected,
      'text-secondary': !clientConnected,
      'spinner-stop': !clientConnected
    }
  }

  get fName() {
    return this.streamsForm.get('name');
  }

  get fTsPrecision() {
    return this.streamsForm.get('timestampPrecision');
  }

  get filterActive() {
    return JSON.stringify(this.streamsForm?.get('filter')?.value) != '{}';
  }

  private resetForm() {
    this.rangeDatasets = [];
    this.rangeMetrics = [];
    this.streamsForm.reset();
  }
}
