import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ConfigService, ObeliskService } from '@core/services';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Export } from '@shared/model';
import moment from 'moment';

@Component({
  selector: 'app-export-details',
  templateUrl: './export-details.component.html',
  styleUrls: ['./export-details.component.scss'],
  providers: [DatePipe, DecimalPipe]
})
export class ExportDetailsComponent implements OnInit {
  detail: Export;
  private metricsLimit = 5;

  constructor(
    public activeModal: NgbActiveModal,
    private datePipe: DatePipe,
    private decimalPipe: DecimalPipe,
    private obelisk: ObeliskService,
    private config: ConfigService
  ) { }

  ngOnInit(): void {

  }

  getDuration(set: Export) {
    return set.status.status === 'COMPLETED' ? set.result.completedOn - set.requestedOn : moment().valueOf() - set.requestedOn;
  }

  getRecordRate(set: Export) {
    return (set.status.recordsEstimate / this.getDuration(set)) * 1000;
  }

  getRecordAmount = (set: Export) => {
    const curr = Math.min(set.status.recordsProcessed, set.status.recordsEstimate);
    const total = set.status.recordsEstimate;
    const percent = ((curr / total) * 100) || 0;
    const FORMAT = '1.0-0';
    const LOCALE = 'nl-BE';
    if (set.status.status === 'COMPLETED') {
      return this.decimalPipe.transform(total, FORMAT, LOCALE) + ' <small>100%</small>';
    } else {
      return this.decimalPipe.transform(curr, FORMAT, LOCALE) + '/' + this.decimalPipe.transform(total, FORMAT, LOCALE) + ' <small>(' + this.decimalPipe.transform(percent, FORMAT, LOCALE) + '%)</small>';
    }
  }

  getWriteSpeed(set: Export) {
    return (set.result.sizeInBytes / this.getDuration(set)) * 1000;
  }

  getWriteSpeedCompressed(set: Export) {
    return (set.result.compressedSizeInBytes / this.getDuration(set)) * 1000;
  }


  getContextMetrics(set: Export) {
    const m = set.dataRange.metrics;
    return m.slice(0, this.metricsLimit);
  }

  getRange(detail: Export): string {
    if (detail.from && detail.to) {
      return this.datePipe.transform(detail.from * 1000, 'MMM d yyyy, HH:mm') + ' - ' + this.datePipe.transform(detail.to * 1000, 'MMM d yyyy, HH:mm');
    } else if (detail.from) {
      return this.datePipe.transform(detail.from * 1000, 'MMM d yyyy, HH:mm') + ' - *';
    } else if (detail.to) {
      return '* - ' + this.datePipe.transform(detail.to * 1000, 'MMM d yyyy, HH:mm');
    } else {
      return '* - *';
    }
  }

  toggleMetrics() {
    if (!this.metricsLimit) {
      this.metricsLimit = 5
    } else {
      this.metricsLimit = undefined;
    }
  }

  isExpandable(set: Export) {
    return set.dataRange.metrics.length > this.metricsLimit;
  }

  refreshDetail() {
    this.obelisk.getExport(this.detail.id).subscribe(exp => this.detail = exp as Export);
  }

  getDownloadUrl() {
    const cfg = this.config.getCfg();
    return cfg.oblxHost + cfg.oblxApiPrefix + '/data/exports/' + this.detail.id;
  }

}
