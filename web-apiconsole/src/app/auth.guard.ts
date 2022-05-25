import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {

  constructor(private router: Router, private auth: AuthService) {
  }

  canActivate(route: ActivatedRouteSnapshot, routerState: RouterStateSnapshot) {
    const targetState = encodeURIComponent(btoa(routerState.url));
    if (route.queryParamMap.has('error')) {
      this.auth.getClient().logout();
      const errorCode = route.queryParamMap.get('error');
      const state = route.queryParamMap.get('state');
      return this.router.parseUrl(`/login?state=${state}&error=${errorCode}`);
    } else if (route.queryParamMap.has('code')) {
      return this.auth.getClient().handleCodeExchange(routerState)
        .then(clientState => this.router.navigateByUrl(this.router.parseUrl(atob(decodeURIComponent(clientState)))));
      // return false;
    } else {
      if (this.auth.getClient().loggedIn()) {
        // console.log('GUARD TRUE')
        return true;
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
