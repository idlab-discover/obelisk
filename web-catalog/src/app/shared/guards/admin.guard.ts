import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { RoleService } from '@core/services';
import { AuthService } from '@core/services/auth.service';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class AdminGuard implements CanActivate {

  constructor(
    private router: Router,
    private auth: AuthService,
    private role: RoleService) { }


  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {


    const targetState = encodeURIComponent(btoa(state.url));
    if (route.queryParamMap.has('error')) {
      this.router.navigateByUrl('/error' + location.search);
    } else if (route.queryParamMap.has('code')) {
      return this.auth.getClient().handleCodeExchange(state)
        .then(clientState => this.router.navigateByUrl(this.router.parseUrl(atob(decodeURIComponent(clientState)))));
    } else {
      if (this.auth.getClient().loggedIn()) {
        // console.log('GUARD TRUE')
        return this.role.isAdmin$().pipe(map(ok => ok || this.router.parseUrl(`/login?state=${targetState}`)));
      } else if (this.auth.getClient().idTokenSaved()) {
        // console.log('GUARD DOUBTING')
        return this.router.parseUrl(`/login?state=${targetState}&token`);
      } else {
        // console.log('FALSE')
        return this.router.parseUrl(`/login?state=${targetState}`);
      }
    }
  }

}
