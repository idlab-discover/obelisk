import { Component, OnInit } from '@angular/core';
import { HeaderService, ObeliskService, RoleService } from '@core/services';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { ServiceStatus } from '@shared/model';
import { timer } from 'rxjs';
import { switchMapTo } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-status',
  templateUrl: './status.component.html',
  styleUrls: ['./status.component.scss']
})
export class StatusComponent implements OnInit {
  states: ServiceStatus[];
  lastUpdate: number;
  isAdmin: boolean;

  constructor(
    private header: HeaderService,
    private obelisk: ObeliskService,
    private role: RoleService
  ) { }

  ngOnInit(): void {
    this.header.setTitle('Status overview', 'wave-square');
    timer(0, 1 * 60 * 1000).pipe(
      untilDestroyed(this),
      switchMapTo(this.obelisk.getStatusAll(30))
    )
      .subscribe(states => {
        this.lastUpdate = Date.now();
        this.states = states;
      });
    this.role.isAdmin$().subscribe(isAdmin => this.isAdmin = isAdmin);
  }

  state(componentId: string): ServiceStatus {
    return this.states?.find(s => s.componentId == componentId);
  }

  isUp(service: ServiceStatus) {
    const size = service?.history.length ?? -1;
    if (size < 1) {
      return false;
    }
    return 'HEALTHY' == service.history[size - 1];
  }

  isDown(service: ServiceStatus) {
    const size = service?.history.length ?? -1;
    if (size < 1) {
      return false;
    }
    return 'FAILED' == service.history[size - 1];
  }

  isDud(service: ServiceStatus) {
    const size = service?.history.length ?? -1;
    if (size < 1) {
      return false;
    }
    return 'UNKNOWN' == service.history[size - 1];
  }

  isDegraded(service: ServiceStatus) {
    const size = service?.history.length ?? -1;
    if (size < 1) {
      return false;
    }
    return 'DEGRADED' == service.history[size - 1];
  }

}
