import { Injectable } from '@angular/core';
import { Observable, ReplaySubject, Subject } from 'rxjs';
import { filter, map, pluck, share, tap } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class CommunicationService {
  private expanded = false;
  private bus: Subject<Message> = new ReplaySubject(1);

  constructor() { }

  send<T>(channel: string, message: any) {
    this.bus.next({ channel, message });
  }

  receive(channel: string): Observable<any> {
    return this.bus.asObservable().pipe(
      filter(msg => msg.channel === channel),
      pluck('message'),
      share()
    );
  }
}

export interface Message {
  channel: string,
  message: any;
}