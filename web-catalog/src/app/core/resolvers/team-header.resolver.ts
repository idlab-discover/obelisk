import { Injectable } from '@angular/core';
import {
  Router, Resolve,
  RouterStateSnapshot,
  ActivatedRouteSnapshot
} from '@angular/router';
import { ObeliskService } from '@core/services';
import { Team } from '@shared/model';
import { Observable, of } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class TeamHeaderResolver implements Resolve<Team> {
  constructor(private obelisk: ObeliskService) { }

  resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<Team> {
    const id = route.paramMap.get('id');
    return this.obelisk.getTeamHeader(id);
  }
}
