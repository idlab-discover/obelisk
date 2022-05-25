import { Component, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AuthService, ConfirmService, HeaderService, ObeliskService, ResponseHandlerService, RoleService, ToastService } from '@core/services';
import { NgbDate, NgbInputDatepicker, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ObeliskAuthClient } from '@obelisk/auth';
import { NgSelectDataSource } from '@shared/datasources';
import { FilterEditorComponent } from '@shared/modals';
import { DataRemovalRequestInput, Dataset, FilterExpressionSchema, Metric, User } from '@shared/model';
import { FilterBuilder, Utils } from '@shared/utils';
import moment from 'moment';
import { defer } from 'rxjs';

@Component({
  selector: 'app-removal',
  templateUrl: './removal.component.html',
  styleUrls: ['./removal.component.scss']
})
export class RemovalComponent implements OnInit {
  @ViewChild('datepicker') datepicker: NgbInputDatepicker;
  profile: Partial<User>;
  
  datasetSource: NgSelectDataSource<Dataset>;
  metricSource: NgSelectDataSource<Metric>;

  removalForm: FormGroup;

  hoveredDate: NgbDate | null = null;
  fromDate: NgbDate | null;
  toDate: NgbDate | null;
  private myId: string;

  constructor(
    private obelisk: ObeliskService,
    private header: HeaderService,
    private confirm: ConfirmService,
    private respHandler: ResponseHandlerService,
    private toast: ToastService,
    private modal: NgbModal,
    role: RoleService,
    fb: FormBuilder
  ) {
    role.myId$().subscribe(id => this.myId = id);
    this.removalForm = fb.group({
      dataset: [null, Validators.required],
      metrics: [null, Validators.required],
      timespan: ['', [
        Validators.pattern(/(^[0-3]\d\/[0-1]\d\/[1-2]\d\d\d - [0-3]\d\/[0-1]\d\/[1-2]\d\d\d$)|(^\* - \*$)/)
      ]],
      filter: [{}],
    })
  }

  ngOnInit(): void {
    this.header.setTitle('Data removal')

    this.datasetSource = new NgSelectDataSource(this.obelisk.listMyDatasets.bind(this.obelisk));
    this.removalForm.get('dataset').valueChanges.subscribe(ds => this.loadMetrics(ds.id));
  }

  private loadMetrics(datasetId: string) {
    this.removalForm.get('metrics').reset();
    this.metricSource = new NgSelectDataSource<Metric>(this.obelisk.listMetrics.bind(this.obelisk, datasetId), { filterFn: t => FilterBuilder.regex_i("id", t) });
  }


  deleteData() {
    if (this.removalForm.valid) {
      const timespan = (this.removalForm.get('timespan').value as string)?.replace(/\s+/, '').split('-');
      const from = timespan[0].trim() == '*' ? 0 : moment(timespan[0], 'DD/MM/YYYY').valueOf();
      const to = timespan[1].trim() == '*' ? moment().valueOf() : moment(timespan[1], 'DD/MM/YYYY').valueOf();
      const datasets = [this.fDataset.value?.id];
      const metrics = this.fMetrics.value;
      const dataRange = { datasets, metrics }
      const filter = this.fFilter.value || {};
      const request: DataRemovalRequestInput = {
        dataRange,
        filter,
        from,
        to,
      };
      const actualObs = defer(() => this.confirm.areYouSureThen('Are you really sure? The deleted data cannot be restored!', this.obelisk.removeMyData(this.myId, request)))
      this.confirm.areYouSureThen('Are you sure you want to <b>permanently</b> delete all your data? <b>This is irreversible!!</b>', actualObs)
        .subscribe(res => this.respHandler.observe(res, {
          success: _ => {
            this.toast.show('Delete request sent')
            this.removalForm.reset(
              { dataset: null, metrics: null, timespan: '', filter: {} }, { emitEvent: false }
            );
          }
        }));
    }
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
    this.removalForm.get('timespan').setValue(fromTo)
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

  ignore($event: MouseEvent) {
    $event.preventDefault();
    $event.stopPropagation();
  }

  setAllTime($event: MouseEvent) {
    $event.preventDefault();
    $event.stopPropagation();
    this.fTimespan.setValue('* - *');
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

  selectAllMetrics() {
    const sub = this.metricSource.items$.subscribe(items => this.removalForm.get('metrics').setValue(items.map(m => m.id)))
    this.metricSource.unpageFully();
    sub.unsubscribe();
  }

  deselectAllMetrics() {
    this.removalForm.get('metrics').reset();
  }

  openFilterPanel() {
    const ref = this.modal.open(FilterEditorComponent, { size: 'xl', backdrop: 'static', windowClass: 'special' });
    ref.componentInstance.init(this.removalForm.get('filter').value, FilterExpressionSchema);

    ref.result.then(
      newFilter => {
        this.removalForm.get('filter').setValue(newFilter);
      },
      Utils.doNothing);
  }

  get fTimespan() {
    return this.removalForm.get('timespan');
  }

  get fDataset() {
    return this.removalForm.get('dataset');
  }

  get fMetrics() {
    return this.removalForm.get('metrics');
  }

  get fFilter() {
    return this.removalForm.get('filter');
  }

  get filterActive() {
    return JSON.stringify(this.fFilter?.value)?.trim() != '{}';
  }

}
