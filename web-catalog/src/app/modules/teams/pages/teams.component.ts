import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { fadeAnimation, inOut } from '@core/animations';
import { HeaderService, RoleService } from '@core/services';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { combineLatest } from 'rxjs';
import { filter, map, mapTo, pluck, startWith, switchMap, tap } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-team',
  templateUrl: './teams.component.html',
  styleUrls: ['./teams.component.scss'],
  animations: [
    inOut,
    fadeAnimation
  ]
})
export class TeamsComponent implements OnInit {
  tId: string;
  canManageTeam;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private header: HeaderService,
    private role: RoleService
  ) { }

  ngOnInit(): void {
    combineLatest([
      this.router.events.pipe(
        untilDestroyed(this),
        filter(ev => ev instanceof NavigationEnd),
        startWith(<string>null)),
      this.route.data.pipe(pluck('header'))
    ]).pipe(
      untilDestroyed(this),
      map(([_, header]) => header),
      switchMap(team => this.role.canManageTeam$(team.id).pipe(
        tap(canManageTeam => this.canManageTeam = canManageTeam),
        mapTo(team)
      ))
    ).subscribe(team => {
      this.header.setTitle(team.name, 'sitemap');
      this.tId = team.id;
    });
  }

}
