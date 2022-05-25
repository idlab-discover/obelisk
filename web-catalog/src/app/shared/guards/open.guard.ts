import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from '@core/services/auth.service';

@Injectable({
  providedIn: 'root'
})
export class OpenGuard implements CanActivate {

  constructor(
    private auth: AuthService,
    private router: Router
  ) { }

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
        return true;
      } else if (this.auth.getClient().idTokenSaved()) {
        // console.log('GUARD DOUBTING')
        return this.router.parseUrl(`/login?state=${targetState}&token`);
      } else {
        return true; // you can see it if you are noto logged in or saved an idtoken
      }
    }

  }

}
