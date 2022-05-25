import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({
  providedIn: 'root'
})
export class ConfigService {
  private cfg: any;

  constructor(private http: HttpClient) { }

  public getCfg() {
    return this.cfg;
  }

  public loadConfig() {
    return this.http.get('./assets/config/config.json')
      .toPromise()
      .then(cfg => this.cfg = this.rcFix(cfg));
  }

  private rcFix(cfg: any) {
    if (location.origin === 'https://rc.obelisk.ilabt.imec.be') {
      cfg.oblxHost = location.origin;
      cfg.clientHost = location.origin;
      cfg.clientRedirectUri = location.origin + cfg.clientBasePath;
    }
    return cfg;
  }
}
