import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateChild, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { RoleService } from '@core/services';
import { Observable, of, zip } from 'rxjs';
import { map, switchMap, tap } from 'rxjs/operators';

/**
 * ChildRoutes marked with <code>{data: {managerOnly: true}}</code> will only load if user has manager rights
 */
@Injectable({
  providedIn: 'root'
})
export class DatasetGuard implements CanActivateChild {
  private PEEK: string = "peek";
  private HOME: string = 'home';

  constructor(private role: RoleService, private router: Router) { }

  canActivateChild(
    childRoute: ActivatedRouteSnapshot,
    state: RouterStateSnapshot): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    const dsId = childRoute.parent.params.id;
    const childPath = childRoute.routeConfig.path;
    if (this.PEEK == childPath) {
      return true;
    } else {
      return zip(this.role.isAdmin$(), this.role.getDatasetRights$(dsId)).pipe(
        map(([isAdmin, perms]: [boolean, ['READ' | 'WRITE' | 'MANAGE']]) => {
          const isManagerOnly = childRoute.data.managerOnly ?? false;
          const check = (isManagerOnly && (perms?.indexOf('MANAGE') > -1)) || (!isManagerOnly && (perms != undefined));
          if (isAdmin || check) {
            return true;
          } else if (perms?.indexOf('READ')) {
            return this.router.createUrlTree([childRoute.parent.pathFromRoot.map(s => s.url[0]?.path).join('/').concat(`/${this.HOME}`)]);
          } else {
            return this.router.createUrlTree([childRoute.parent.pathFromRoot.map(s => s.url[0]?.path).join('/').concat(`/${this.PEEK}`)]);
          }
        })
      )
    }
  }

}
