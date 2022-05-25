import { Injectable } from '@angular/core';
import { Observable, ReplaySubject, Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class HeaderService {
  private title: string = 'Obelisk catalog';
  private icon: string | null = null;
  private sidebarState: SidebarState;
  private sidebarState$: Subject<SidebarState> = new ReplaySubject<SidebarState>(1);

  constructor(
  ) {
    this.setSidebarState('none');
  }

  setTitle(title: string, faIcon?: string) {
    this.title = title;
    this.icon = faIcon;
  }

  getTitle(): string {
    return this.title;
  }

  getIcon(): string | null {
    return this.icon;
  }

  setSidebarState(state: SidebarState) {
    this.sidebarState = state;
    this.sidebarState$.next(state);
  }

  getSidebarState(): SidebarState {
    return this.sidebarState;
  }

  streamSidebarStates(): Observable<SidebarState> {
    return this.sidebarState$.asObservable();
  }
}

export type SidebarState = 'none' | 'collapsed' | 'expanded';
