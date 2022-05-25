import { Injectable } from '@angular/core';
import { AuthService } from './auth.service';
import { SidebarState } from './header.service';

@Injectable({
  providedIn: 'root'
})
export class SettingsService {

  settings: Settings = {
    sidebarState: 'expanded'
  };

  private user: string;
  private key: string;

  constructor(
    authService: AuthService
  ) {
    this.user = authService.getClient().getTokens().idToken;
    this.key = `oblx_${this.user.sub}_settings`;
  }

  save(settings: Settings) {
    localStorage.setItem(this.key, JSON.stringify(settings));
  }

  load(): Settings {
    return JSON.parse(localStorage.getItem(this.key));
  }

  setSidebarState(state: SidebarState) {
    const settings = this.load() ?? {sidebarState:'expanded'};
    settings.sidebarState = state;
    this.save(settings);
  }
}

export interface Settings {
  sidebarState: SidebarState
}
