import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { Router } from '@angular/router';
import { Clipboard } from "@angular/cdk/clipboard";
import { environment } from 'environments/environment';
import { AuthService } from './auth.service';



@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class AppComponent implements OnInit {
  public title = 'Obelisk API Console';
  public version: string;

  constructor(private auth: AuthService, public router: Router, private clipboard: Clipboard) { }

  ngOnInit() {
    this.version = environment.VERSION;
  }

  public logout() {
    this.auth.getClient().logout();
    location.reload();
  }

  public loginAsUser() {
    this.auth.getClient().getLoginUrl().then(uri => location.href = uri);
  }

  public login() {
    this.auth.getClient().getLoginUrl().then(uri => location.href = uri);
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
    return this.auth.isTokenPresent();
  }

  /** Should update on each event */
  isLoggedIn(): boolean {
    return this.auth.isTokenPresent();
  }

  public copyToken = () => {
    this.clipboard.copy(this.auth.getClient().getTokens().accessToken);
    const el = document.getElementById('copy-token-icon');
    const txt = el.textContent;
    el.textContent = 'check_circle_outline';
    setTimeout(() => {
      el.textContent = txt;
    }, 1500);
  }

}
