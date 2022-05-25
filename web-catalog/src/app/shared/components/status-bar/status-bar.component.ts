import { Component, Input, OnInit } from '@angular/core';
import { ServiceStatus, Status } from '@shared/model';
import { DurationPipe } from '@shared/pipes';

@Component({
  selector: 'app-status-bar, status-bar',
  templateUrl: './status-bar.component.html',
  styleUrls: ['./status-bar.component.scss']
})
export class StatusBarComponent implements OnInit {
  @Input() service: ServiceStatus;

  constructor(private durationPipe: DurationPipe) { }

  ngOnInit(): void {

  }

  time(idx: number) {
    const realIdx = (this.service?.windowDurationMs / this.service?.groupByMs) - idx;
    const intervalTime = this.durationPipe.transform(this.service?.groupByMs * realIdx);
    return `${intervalTime} ago`;
  }

  state(state: Status) {
    switch (state) {
      case 'HEALTHY':
        return 'healthy';
      case 'FAILED':
        return 'down';
      case 'DEGRADED':
        return 'degraded';
      case 'UNKNOWN':
        return '[no data]';
      default:
        return '-';
    }
  }
}
