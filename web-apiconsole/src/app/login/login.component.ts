import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {

  private state: string;

  constructor(
    private auth: AuthService, 
    private route: ActivatedRoute,
    private router: Router) { }

  ngOnInit() {

    this.route.queryParamMap.subscribe(params => {
      const errorCode = params.get('error');
      const state = params.get('state');
      switch (errorCode) {
        case '401wc':
          this.auth.getClient().logout();
          this.redirectToLoginWithError(state);
          break;
      }
    });

    this.state = this.route.snapshot.queryParamMap.get('state');
    if (this.route.snapshot.queryParamMap.has('token')) {
      const id_token = this.auth.getClient().getTokens().idTokenString;
      const remember_me = this.auth.getClient().isRememberMe();

      this.login({id_token, remember_me});
    }
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

  private redirectToLoginWithError(state?: string) {
    this.auth.getClient().getLoginUrl(state).then(uri => location.replace(uri + '&error=401'));
  }


}
