import { Injectable } from '@angular/core';
import { Observable, ReplaySubject, Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class DatasetCommService {
  private map = new Map<string, ReplaySubject<DsMessage>>();

  constructor() { }

  send(datasetId: string, message: DsMessage): void {
    this.createOrGet(datasetId).next(message);
  }

  listen$(datasetId: string): Observable<DsMessage> {
    return this.createOrGet(datasetId);
  }

  private createOrGet(datasetId: string): Subject<DsMessage> {
    if (!this.map.has(datasetId)) {
      this.map.set(datasetId, new ReplaySubject(1));
    }
    return this.map.get(datasetId);
  }
}

export interface DsMessage {
  accessRequests?: number;
}
