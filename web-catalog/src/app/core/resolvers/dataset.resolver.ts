import { Injectable } from '@angular/core';
import {
  ActivatedRouteSnapshot, Resolve,
  RouterStateSnapshot
} from '@angular/router';
import { ObeliskService } from '@core/services';
import { Dataset } from '@shared/model';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class DatasetResolver implements Resolve<Dataset> {

  constructor(private obelisk: ObeliskService) {

  }

  resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<Dataset> | Dataset {
    const id = route.parent.data.header.id
    switch (route.routeConfig.path) {
      case 'home':
        return this.obelisk.getDatasetOverview(id);
      case 'metrics':
      case 'metrics/:metricId':
      case 'things':
      case 'things/:thingId':
      case 'readme':
      case 'origins':
      case 'invites':
      case 'accessrequests':
      case 'access':
      case 'roles':
      case 'roles/:roleId':
      case 'insight':
      case 'membership-info':
        return { id } as Dataset;
      case 'edit':
        return this.obelisk.getDatasetSettings(id);
      case 'peek':
        return this.obelisk.getDatasetOverview(id);
    }
  }
}
