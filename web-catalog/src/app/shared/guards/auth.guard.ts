import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '@core/services/auth.service';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {

  constructor(private router: Router, private auth: AuthService) {
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
    const targetState = encodeURIComponent(btoa(state.url));
    if (route.queryParamMap.has('error')) {
      this.router.navigateByUrl('/error' + location.search);
    } else if (route.queryParamMap.has('code')) {
      return this.auth.getClient().handleCodeExchange(state)
        .then(clientState => {
          const path = atob(decodeURIComponent(clientState));
          // console.log('PATH_STATE', path);
          return this.router.navigateByUrl(this.router.parseUrl(path))
        });
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
