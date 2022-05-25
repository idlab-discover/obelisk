import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateChild, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { RoleService } from '@core/services';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class TeamGuard implements CanActivateChild {
  private HOME: string = 'home';

  constructor(private role: RoleService, private router: Router) { }

  canActivateChild(
    childRoute: ActivatedRouteSnapshot,
    state: RouterStateSnapshot): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    const teamId = childRoute.parent.params.id;
    const childPath = childRoute.routeConfig.path;
    return this.role.canManageTeam$(teamId, true).pipe(
      map(canManage => {
        const isManagerOnly = childRoute.data.managerOnly ?? false;
        if (isManagerOnly && !canManage) {
          return this.router.createUrlTree([childRoute.parent.pathFromRoot.map(s => s.url[0]?.path).join('/').concat(`/${this.HOME}`)]);
        } else {
          return true;
        }
      })
    )
  }



}
