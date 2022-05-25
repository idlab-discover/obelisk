import { Injectable } from '@angular/core';
import {
  Router, Resolve,
  RouterStateSnapshot,
  ActivatedRouteSnapshot
} from '@angular/router';
import { ObeliskService, RoleService } from '@core/services';
import { Team } from '@shared/model';
import { Observable, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class TeamResolver implements Resolve<Team> {
  constructor(private obelisk: ObeliskService, private role: RoleService) {

  }

  resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<Team> | Team {
    const id = route.parent.data.header.id;
    switch (route.routeConfig.path) {
      case 'home':
      case 'ds':
      case 'clients':
      case 'clients/:clientId':
      case 'invites':
      case 'members':
      case 'ratelimit':
      case 'streams':
      case 'exports':
      case 'edit':
        return this.role.isAdmin$().pipe(switchMap(asAdmin => this.obelisk.getTeam(id, asAdmin)));
    }
  }
}
