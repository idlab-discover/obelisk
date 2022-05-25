import { DOCUMENT } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { ObeliskService } from '@core/services';
import { AdvancedStatus, AdvancedStatusService, AdvancedStatusStreamer } from '@shared/model';

@Component({
  selector: 'app-advanced-status',
  templateUrl: './advanced-status.component.html',
  styleUrls: ['./advanced-status.component.scss']
})
export class AdvancedStatusComponent implements OnInit {
  status: AdvancedStatus[] = [];

  constructor(
    private obelisk: ObeliskService,
    @Inject(DOCUMENT) private document: Document
  ) { }

  ngOnInit(): void {
    this.loadStatus();
  }

  private loadStatus() {
    this.obelisk.getAdvancedStatus().subscribe(status => {
      this.status = status;
      setTimeout(() => {
        this.status.forEach((st, i) => {
          const mmm = this.document.getElementById(`mmm-${i}`) as HTMLDivElement;
          const div = this.document.getElementById(`mean-${i}`) as HTMLDivElement;
          let perc = 0.5;
          if (this.isService(st)) {
            perc = st.meanRTT / (st.maxRTT - st.minRTT);
          } else if (this.isStreamer(st)) {
            perc = st.meanLagMs / (st.maxLagMs - st.minLagMs);
          }
          let left = Math.round(mmm.clientWidth * perc);
          left = Math.max(32, left); // min 32 px from left
          left = Math.min(left, 32 * 2); // min 32px from right (including own size)
          div.style.left = `${left}px`;
          mmm.style.background = this.generateGradient(mmm.clientWidth, left);
        });
      }, 0);
    });
  }

  private generateGradient(totalWidth: number, mean: number) {
    const stop2 = Math.round((mean / totalWidth) * 100)
    return `linear-gradient(90deg, rgba(0,255,0,1) 0%, rgba(255,255,0,1) ${stop2}%, rgba(255,0,0,1) 100%)`;
  }

  refresh() {
    this.loadStatus();
  }

  isService(st: AdvancedStatus): st is AdvancedStatusService {
    return ['ingest-service', 'catalog-service-read', 'catalog-service-write', 'query-service'].includes(st.component);
  }

  isStreamer(st: AdvancedStatus): st is AdvancedStatusStreamer {
    return ['streaming-service'].includes(st.component);
  }

  lastEvent(st: AdvancedStatusStreamer): number {
    return new Date(st.lastEventThrough).valueOf();
  }

  colorGrade(percent: number) {
    let color = 'red';
    if (percent >= 0.75 ) {
      color = 'orange';
    }
    if (percent == 1) {
      color = 'green';
    }
    return color;
  }

}
