import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '@core/services/auth.service';
import { CustomizationService } from '@core/services/customization.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {
  brand: string;

  private state: string;

  constructor(
    cz: CustomizationService,
    private auth: AuthService,
    private route: ActivatedRoute,
    private router: Router) {
    this.brand = cz.load().brandName;
  }

  ngOnInit() {
    this.state = this.route.snapshot.queryParamMap.get('state');
    let params = null;
    // Auto login detect if token is present
    if (this.route.snapshot.queryParamMap.has('token')) {
      const id_token = this.auth.getClient().getTokens().idTokenString;
      const remember_me = this.auth.getClient().isRememberMe();
      params = { id_token, remember_me };
    }
    this.login(params);
  }

  public login(params?: { id_token: string, remember_me: boolean }) {
    this.auth.getClient().getLoginUrl(this.state).then(uri => {
      this.auth.isBackendLive().subscribe(ok => {
        if (ok) {
          if (params) {
            uri += `&id_token=${params.id_token}&remember_me=${params.remember_me}`;
          }
          location.replace(uri)
        } else {
          this.router.navigateByUrl('/error?error=0&message=Could not reach Obelisk Auth Backend..');
        }
      })
    });
  }

  public getPictureUrl() {
    const tok = this.auth.getClient().getTokens().idToken;
    if (tok) {
      return tok['picture'] || '';
    } else {
      return './assets/img/avatar_square.jpg';
    }
  }

  get loggedIn() {
    return this.auth.getClient().loggedIn();
  }

  /** Should update on each event */
  isLoggedIn(): boolean {
    return this.auth.getClient().loggedIn();
  }

}
