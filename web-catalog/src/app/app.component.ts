import { Location } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, Optional } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { fadeAnimation } from '@core/animations';
import { AuthService } from '@core/services/auth.service';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { Mode } from '@shared/components';
import { Subscription } from 'rxjs';
import { TicketService } from './modules/ticket/ticket.service';

@UntilDestroy()
@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss'],
  animations: [
    fadeAnimation
  ]
})
export class AppComponent implements OnInit {
  activeDataset: string | null = null;
  title: string;
  mode: Mode = 'title';
  frameHidden: boolean = true;
  
  private sub: Subscription;

  constructor(
    location: Location,
    private auth: AuthService,
    @Optional() private ticket: TicketService
  ) {
    this.activeDataset = null;
    location.onUrlChange(url => {
      const datasetRegex = /\/ds\/([^/]+)\/?/;
      const homeRegex = /\/ds/;
      const myRegex = /\/my/;
      const errorRegex = /\/error/;
      const adminRegex = /\/admin/;
      const loginRegex = /\/login/;
      if (datasetRegex.test(url)) {
        this.activeDataset = datasetRegex.exec(url)[1];
        this.mode = 'dataset';
      } else if (homeRegex.test(url)) {
        this.activeDataset = null;
        this.mode = 'title';
      } else if (myRegex.test(url)) {
        this.activeDataset = null;
        this.mode = 'empty';
      } else if (errorRegex.test(url)) {
        this.activeDataset = null;
        this.mode = 'empty';
      } else if (adminRegex.test(url)) {
        this.activeDataset = null;
        this.mode = 'empty';
      } else if (loginRegex.test(url)) {
        this.activeDataset = null;
        this.mode = 'invisible';
      } else {
        this.activeDataset = null;
        this.mode = 'title';
      }
    });
  }

  ngOnInit() {
    if (this.ticket) {
      this.ticket.isTicketFrameHidden$
        .pipe(untilDestroyed(this))
        .subscribe(hidden => this.frameHidden = hidden);
    }
  }

  logout() {
    this.auth.getClient().clearTokens();
    location.reload();
  }

  prepareRoute(outlet: RouterOutlet) {
    return outlet && outlet.activatedRouteData && outlet.activatedRouteData['animation'];
  }

  openTicketFrame() {
    if (this.ticket) {
      this.ticket.openTicketFrame();
    }
  }

  closeTicketFrame() {
    if (this.ticket) {
      this.ticket.removeTicketFrame();
      // this.ticket.closeTicketFrame();
    }
  }

  toggleTicketFrame($event: MouseEvent) {
    if (this.ticket) {
      this.ticket.toggleTicketFrame();
    }
  }

  isTicketFrameHidden() {
    return this.ticket?.isTicketFrameHidden$.asObservable();
  }

  isLarge() {
    return (this.ticket?.getFrameSize() == 'large') || false;
  }
}

