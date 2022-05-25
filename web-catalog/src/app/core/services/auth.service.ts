import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { AuthEvent, AuthEventType, ObeliskAuth, ObeliskAuthClient, ObeliskConfig } from '@obelisk/auth';
import { AsyncSubject, config, Observable, of, Subject } from 'rxjs';
import { catchError, switchMapTo } from 'rxjs/operators';
import { ConfigService } from './config.service';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private client: ObeliskAuthClient;

  authReady$: Subject<void>;

  constructor(private config: ConfigService, private http: HttpClient) {
    this.authReady$ = new AsyncSubject<void>();
    this.client = new ObeliskAuthClient(config.getCfg(), this.eventHandler);
  }

  getClient(): ObeliskAuthClient {
    return this.client;
  }

  getConfig(): ObeliskConfig {
    return this.client.getConfig();
  }

  isBackendLive(): Observable<boolean> {
    const uri = this.config.getCfg().oblxHost + this.config.getCfg().oblxApiPrefix + this.config.getCfg().oblxAuthEndpoint + '/.well-known/openid-configuration';
    return this.http.head(uri).pipe(
      switchMapTo(of(true)),
      catchError(err => of(err.status !== 0)));
  }

  private eventHandler = (event: AuthEvent) => {
    if (event.type === AuthEventType.READY) {
      this.authReady$.next();
      this.authReady$.complete();
    }
  }
}
