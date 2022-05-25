import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, ReplaySubject } from 'rxjs';
@Injectable({
  providedIn: 'root'
})
export class TicketService {
  isTicketFrameHidden$ = new BehaviorSubject(true);
  refresh$ = new ReplaySubject<void>(1);
  private framesize: 'large' | 'small' = 'small';

  constructor(
    private router: Router
  ) { }

  openTicketFrame() {
    this.isTicketFrameHidden$.next(false);
  }
  
  closeTicketFrame() {
    this.isTicketFrameHidden$.next(true);
  }
  
  removeTicketFrame() {
    this.closeTicketFrame();
    this.router.navigate([{outlets: {x: null}}])
  }
  
  toggleTicketFrame() {
    this.isTicketFrameHidden$.next(!this.isTicketFrameHidden$.getValue());
  }

  fireRefresh() {
    this.refresh$.next();
  }

  setFrameSize(framesize: 'large' | 'small') {
    this.framesize = framesize;
  }

  getFrameSize() {
    return this.framesize;
  }
}
