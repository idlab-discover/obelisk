import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ConfigService, ConfirmService, HeaderService, ObeliskService, ToastService } from '@core/services';
import { NgbDate, NgbInputDatepicker, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CreateHeaderComponent } from '@shared/components';
import { ObeliskDataSource } from '@shared/datasources';
import { FieldEqualitySet } from "@shared/FieldEqualitySet";
import { DataRangePickerComponent, ExportDetailsComponent, FilterEditorComponent } from '@shared/modals';
import { Dataset, EventField, EventFieldTuple, EventField_ALL, Export, ExportInput, FilterExpressionSchema, TimestampPrecision, TimestampPrecisionTuple, TimestampPrecision_ALL } from '@shared/model';
import { FilterBuilder, Utils } from '@shared/utils';
import moment from 'moment';
import { EMPTY } from 'rxjs';
import { switchMap } from 'rxjs/operators';


@Component({
  selector: 'app-exports',
  templateUrl: './exports.component.html',
  styleUrls: ['./exports.component.scss'],
})
export class ExportsComponent implements OnInit, OnDestroy {
  @ViewChild('datepicker') datepicker: NgbInputDatepicker;
  exportSource: ObeliskDataSource<Export>
  detail: any;
  exportForm: FormGroup;

  rangeDatasets: Partial<Dataset>[] = [];
  rangeMetrics: string[] = [];


  hoveredDate: NgbDate | null = null;
  fromDate: NgbDate | null;
  toDate: NgbDate | null;

  dateHidden: boolean = true;

  allFields: EventFieldTuple;
  allPrecision: TimestampPrecisionTuple;

  constructor(private obelisk: ObeliskService,
    private modal: NgbModal,
    private toast: ToastService,
    private confirm: ConfirmService,
    private config: ConfigService,
    private header: HeaderService,
    fb: FormBuilder
  ) {
    this.allFields = EventField_ALL;
    this.allPrecision = TimestampPrecision_ALL;
    this.exportForm = fb.group({
      name: ['', Validators.required],
      timespan: ['', [
        Validators.required,
        Validators.pattern(/[0-3]\d\/[0-1]\d\/[1-2]\d\d\d - [0-3]\d\/[0-1]\d\/[1-2]\d\d\d/)
      ]],
      dataRange: [null],
      filter: {},
      fields: [],
      timestampPrecision: ['MILLISECONDS', Validators.required],
      limit: [],
      selectedDataset: [],
      selectedMetric: []
    });
  }

  ngOnInit(): void {
    this.header.setTitle('My Exports');
    const filter = term => FilterBuilder.regex_i('name', term);
    this.exportSource = new ObeliskDataSource(this.obelisk.listMyExports.bind(this.obelisk), {filterFn: filter});
  }

  ngOnDestroy() {
    this.exportSource.cleanUp();
  }

  toggleDate($event?: MouseEvent) {
    this.datepicker.toggle();
    $event.preventDefault();
    $event.stopPropagation();
  }

  onDateSelection(date: NgbDate) {
    if (!this.fromDate && !this.toDate) {
      this.fromDate = date;
    } else if (this.fromDate && !this.toDate && date && date.after(this.fromDate)) {
      this.toDate = date;
    } else {
      this.toDate = null;
      this.fromDate = date;
    }
    const fromTo = this.ngbDateToString(this.fromDate) + ' - ' + this.ngbDateToString(this.toDate);
    this.exportForm.get('timespan').setValue(fromTo)
  }

  isHovered(date: NgbDate) {
    return this.fromDate && !this.toDate && this.hoveredDate && date.after(this.fromDate) && date.before(this.hoveredDate);
  }

  isInside(date: NgbDate) {
    return this.toDate && date.after(this.fromDate) && date.before(this.toDate);
  }

  isRange(date: NgbDate) {
    return date.equals(this.fromDate) || (this.toDate && date.equals(this.toDate)) || this.isInside(date) || this.isHovered(date);
  }

  selectAllFields() {
    const fields = this.exportForm.get('fields');
    fields.setValue(this.allFields);
  }

  private ngbDateToString(date: NgbDate) {
    if (date) {
      const d = date.day.toString().padStart(2, '0');
      const m = date.month.toString().padStart(2, '0');
      const y = date.year.toString();
      return `${d}/${m}/${y}`
    } else {
      return '??/??/????';
    }
  }

  isFormValid() {
    return this.exportForm.valid && this.rangeDatasets.length > 0;
  }

  addExport(comp: CreateHeaderComponent<any>) {
    if (!this.isFormValid()) {
      return;
    }
    const name = this.exportForm.get('name').value as string;
    const timespan = (this.exportForm.get('timespan').value as string)?.replace(/\s+/, '').split('-');
    const from = moment(timespan[0], 'DD/MM/YYYY').valueOf();
    const to = moment(timespan[1], 'DD/MM/YYYY').valueOf();
    const datasets = this.rangeDatasets.map(d => d.id!!);
    const metrics = this.rangeMetrics;
    const dataRange = { datasets, metrics }
    const filter = this.exportForm.get('filter').value || {};
    const fields = this.exportForm.get('fields').value as EventField[] || [];
    const timestampPrecision = this.exportForm.get('timestampPrecision').value as TimestampPrecision;
    const limit = this.exportForm.get('limit').value as number;
    const input: ExportInput = { name, from, to, dataRange, filter, fields, timestampPrecision, limit };
    this.obelisk.createExport(input).subscribe(resp => {
      if (resp.responseCode === 'SUCCESS') {
        this.resetForm();
        this.exportSource.invalidate();
        comp.setCollapsed(true);
      }
    }
    )
  }


  removeExport(id: string, event?: MouseEvent) {
    this.confirm.areYouSure("Are you sure you want to remove this export?")
      .pipe(
        switchMap(ok => ok ? this.obelisk.removeExport(id) : EMPTY)
      )
      .subscribe(resp => {
        if (resp.responseCode === 'SUCCESS') {
          this.toast.info("Export removed");
          this.exportSource.invalidate();
        } else {
          this.toast.error("Error removing export!");
        }
      });
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
  }

  cancelExport(id: string, event?: MouseEvent) {
    this.confirm.areYouSure("Are you sure you want to cancel this export?")
      .pipe(
        switchMap(ok => ok ? this.obelisk.removeExport(id) : EMPTY)
      )
      .subscribe(resp => {
        if (resp.responseCode === 'SUCCESS') {
          this.toast.show("Export canceled");
          this.exportSource.invalidate();
        } else {
          this.toast.error("Error canceling export!");
        }
      });
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
  }

  refresh() {
    this.exportSource.invalidate();
  }

  ignore($event: MouseEvent) {
    $event.preventDefault();
    $event.stopPropagation();
  }

  getName(info: any) {
    const context = info.context;
    const filter = context.filter && context.filter.length > 0;
    const fromMs = context.fromMs;
    const toMs = context.toMs;
    let duration;
    if (!fromMs && !toMs) {
      duration = 'all data';
    } else if (!fromMs) {
      duration = '... - ' + moment(toMs).format('MMM d, HH:mm');
    } else if (!toMs) {
      duration = moment.duration(info.requestedOn - fromMs, 'milliseconds').humanize();
    } else {
      duration = moment.duration(toMs - fromMs, 'milliseconds').humanize();
    }
    const area = context.geohashArea && context.geohashArea.length > 0;
    const metrics = context.metrics ? context.metrics.length : 'all';
    let name = `${metrics} metric` + (metrics === 1 ? ' ' : 's ');
    let filters = [];
    if (filter) { filters.push('filter') };
    if (area) { filters.push('area') };
    if (filters.length > 0) { name += '[' + filters.join(',') + ']' }
    name += ` <sup>(${duration})</sup>`;
    return name;
  }

  showDetails(set: Export) {
    const ref = this.modal.open(ExportDetailsComponent, {
      size: 'lg',
    });
    ref.componentInstance.detail = set;
    ref.result.then(
      Utils.doNothing(),
      Utils.doNothing()
    )
  }

  stopPropagation(event: MouseEvent) {
    event.stopPropagation();
  }

  removeMetric() {
    const metric = this.exportForm.get('selectedMetric').value;
    const idx = this.rangeMetrics.indexOf(metric);
    this.rangeMetrics.splice(idx, 1);
    this.exportForm.get('selectedMetric').reset();
  }

  removeDataset() {
    const dataset = this.exportForm.get('selectedDataset').value;
    const idx = this.rangeDatasets.findIndex(ds => ds.id === dataset.id);
    this.rangeDatasets.splice(idx, 1);
    this.exportForm.get('selectedDataset').reset();
  }


  openFilterPanel() {
    const ref = this.modal.open(FilterEditorComponent, { size: 'xl', backdrop: 'static', windowClass: 'special' });
    ref.componentInstance.init(this.exportForm.get('filter').value, FilterExpressionSchema);

    ref.result.then(
      newFilter => {
        this.exportForm.get('filter').setValue(newFilter);
      },
      Utils.doNothing);
  }

  openDataRangeWizard() {
    const ref = this.modal.open(DataRangePickerComponent, { size: 'lg' });
    (ref.componentInstance as DataRangePickerComponent).initForUser();
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

  isDatasetSelected(): boolean {
    return this.rangeDatasets.length > 0 && this.exportForm.get('selectedDataset').value != null;
  }

  isMetricSelected(): boolean {
    return this.rangeMetrics.length > 0 && this.exportForm.get('selectedMetric').value != null;
  }

  getDownloadUrl(exp: Export) {
    const cfg = this.config.getCfg();
    return cfg.oblxHost + cfg.oblxApiPrefix + '/data/exports/' + exp.id;
  }

  private resetForm() {
    this.rangeDatasets = [];
    this.rangeMetrics = [];
    this.exportForm.reset();
  }

  get fName() {
    return this.exportForm.get('name');
  }

  get fTimespan() {
    return this.exportForm.get('timespan');
  }

  get fTsPrecision() {
    return this.exportForm.get('timestampPrecision');
  }

  get fLimit() {
    return this.exportForm.get('limit');
  }

  get filterActive() {
    return JSON.stringify(this.exportForm?.get('filter')?.value)?.trim() != '{}';
  }
}


