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
export class DatasetHeaderResolver implements Resolve<Dataset> {
  constructor(private obelisk: ObeliskService) { }
  
  resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<Dataset> {
    const id = route.paramMap.get('id');
    return this.obelisk.getDatasetHeader(id);
  }
}
