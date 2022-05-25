import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '@core/services';
import { faLeaf } from '@fortawesome/free-solid-svg-icons';

@Injectable({
  providedIn: 'root'
})
export class TicketAuthGuard implements CanActivate {
  constructor(private router: Router, private auth: AuthService) {
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
    if (this.auth.getClient().loggedIn()) {
      // console.log('GUARD TRUE')
      return true;
    } else {
      // console.log('GUARD DOUBTING')
      let primaryPath = location.pathname;
      primaryPath = primaryPath.startsWith('/') ? primaryPath.slice(1): primaryPath;
      // return this.router.parseUrl('home(x:ticket/auth');
      this.router.navigate([{outlets:{x: 'ticket/auth'}}]);
      return false;
    }
  }
}


