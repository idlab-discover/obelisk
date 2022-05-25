import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { AuthService } from '@core/services';
import { ObeliskAuthClient } from '@obelisk/auth';
import { Observable } from 'rxjs';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  private client: ObeliskAuthClient
  private URL_BACKEND_API: string;

  constructor(auth: AuthService) {
    this.client = auth.getClient();
    const config = this.client.getConfig();
    this.URL_BACKEND_API = config.oblxHost + config.oblxApiPrefix;
  }

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    // Check if request is bound for destination with Auth requirement
    const uri = request.urlWithParams;

    // Bound for backend
    const isForApi = uri.startsWith(this.URL_BACKEND_API);

    // if (isForApi) { console.log('Request bound for API: ', uri) }

    // Gather all valid reasons to add Aut
    const mustAddAuth = isForApi;

    // Store original request
    let newOrSameRequest = request;
    if (mustAddAuth) {
      // Get the token if there is any
      const token = this.client?.getTokens()?.accessToken;
      if (token) {
        newOrSameRequest = request.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
      }
    }
    return next.handle(newOrSameRequest);
  }
}
