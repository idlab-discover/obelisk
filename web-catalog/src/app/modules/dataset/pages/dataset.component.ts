import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { fadeAnimation, inOut } from '@core/animations';
import { DatasetCommService, HeaderService, ObeliskService, RoleService } from '@core/services';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { combineLatest, iif, of, ReplaySubject } from 'rxjs';
import { filter, map, mapTo, pluck, startWith, switchMap, tap } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-dataset',
  templateUrl: './dataset.component.html',
  styleUrls: ['./dataset.component.scss'],
  animations: [
    inOut,
    fadeAnimation
  ]
})
export class DatasetComponent implements OnInit {
  dsId: string;
  aReqs: number = 0;
  canManage: boolean;

  private checker$ = new ReplaySubject<void>(1);

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private comm: DatasetCommService,
    private obelisk: ObeliskService,
    private header: HeaderService,
    private role: RoleService
  ) {
    // Check immediatly for manage rights
    this.checker$.next();
  }

  ngOnInit(): void {
    // Listen to internal dataset comms
    this.comm.listen$(this.dsId).subscribe(msg => {
      if ('accessRequests' in msg) {
        this.aReqs = msg.accessRequests;
      }
    });

    combineLatest([
      this.router.events.pipe(
        untilDestroyed(this),
        filter(ev => ev instanceof NavigationEnd),
        startWith(<string>null)),
      this.route.data.pipe(pluck('header'))
    ]).pipe(
      untilDestroyed(this),
      map(([_, header]) => header),
      tap(header => this.header.setTitle(header.name || header.id, 'database')),
      // Recheck if you can manage this dataset, if so, refresh dataset accessrequests count
      switchMap(header => this.role.canManageDataset$(header.id).pipe(
        switchMap(canManage => iif(() => canManage,
          this.obelisk.countDatasetAccessReqeuests(header.id).pipe(
            tap(nr => this.comm.send(header.id, { accessRequests: nr })),
            mapTo(canManage)),
          of(canManage))
        ))
      ))
      .subscribe(ok => this.canManage = ok);
  }

}
