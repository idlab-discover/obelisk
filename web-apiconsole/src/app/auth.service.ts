import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { AuthEvent, AuthEventType, ObeliskAuth, ObeliskAuthClient } from '@obelisk/auth';
import { Observable } from 'graphiql';
import { of, ReplaySubject, Subject } from 'rxjs';
import { catchError, switchMapTo } from 'rxjs/operators';
import { ConfigService } from './config.service';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private client: ObeliskAuth;

  authReady$: Subject<boolean>;

  constructor(private config: ConfigService, private http: HttpClient) {
    this.authReady$ = new ReplaySubject(1);
    this.client = new ObeliskAuthClient(config.getCfg(), this.eventHandler);
  }

  getClient() {
    return this.client;
  }

  getTokens() {
    return this.client.getTokens();
  }

  clearTokens() {
    return this.client.clearTokens();
  }

  isTokenPresent(): boolean {
    return this.client.loggedIn();
  }

  getNamespace(userMapped: boolean = false): string {
    const ns = this.client.getHostUrl() + this.client.getConfig().clientBasePath;
    return userMapped ? this.client.getTokens().idToken['email'] + '@' + ns : ns;
  }

  isBackendLive(): Observable<boolean> {
    const uri = this.config.getCfg().oblxHost + this.config.getCfg().oblxApiPrefix + this.config.getCfg().oblxAuthEndpoint + '/.well-known/openid-configuration';
    return this.http.head(uri).pipe(
      switchMapTo(of(true)),
      catchError(err => of(err.status !== 0)));
  }

  private eventHandler = (event: AuthEvent) => {
    if (event.type === AuthEventType.READY) {
      this.authReady$.next(true);
    }
  }
}
