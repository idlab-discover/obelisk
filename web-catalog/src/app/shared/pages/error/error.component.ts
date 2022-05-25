import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HeaderService } from '@core/services';
import { AuthService } from '@core/services/auth.service';
import { delay } from 'rxjs/operators';

@Component({
  selector: 'app-error',
  templateUrl: './error.component.html',
  styleUrls: ['./error.component.scss']
})
export class ErrorComponent implements OnInit {
  errorTitle: string;
  errorMessage: string;
  errorCode: string
  readonly PATTERN = new RegExp(/no\ssession\sfound/i).compile();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private auth: AuthService,
    private header: HeaderService,
  ) { }

  ngOnInit(): void {
    this.route.queryParamMap.pipe(delay(500)).subscribe(params => {
      this.errorCode = params.get('error');
      this.errorMessage = params.get('message');
      const state = params.get('state');
      switch (this.errorCode) {
        case '400':
          this.errorTitle = 'Bad request';
          break;
        case '401':
          this.errorTitle = 'Unauthorized';
          if (this.PATTERN.test(this.errorMessage)) {
            this.auth.getClient().logout();
            setTimeout(() => {
              this.router.navigateByUrl('');
            }, 5000);
          }
          break;
        case '401wc':
          this.auth.getClient().logout();
          this.redirectToLoginWithError(state);
          break;
        case '403':
          this.errorTitle = 'Forbidden';
          break;
        case '404':
          this.errorTitle = 'Not Found';
          break;
        case '500':
          this.errorTitle = 'Server error';
          break;
        default:
          this.errorTitle = 'Unknown error';
      }
      this.header.setTitle(this.errorCode + " " + this.errorTitle);
      this.header.setSidebarState('none');
    });
  }

  private redirectToLoginWithError(state?: string) {
    this.auth.getClient().getLoginUrl(state).then(uri => location.replace(uri + '&error=401'));
  }

}
