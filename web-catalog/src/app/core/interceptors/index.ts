import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { AuthService, ConfigService } from '@core/services';

import { AuthInterceptor } from './auth.interceptor';

/** Http interceptor providers in outside-in order */
export const httpInterceptorProviders = [
  { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
];