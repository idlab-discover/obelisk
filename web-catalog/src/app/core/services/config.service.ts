
import { Injectable } from '@angular/core';
import { ObeliskConfig } from '@obelisk/auth';

@Injectable({
  providedIn: 'root'
})
export class ConfigService {
  private cfg: ObeliskConfig;

  constructor() { }

  /**
   * Returns the cached config object.
   * @returns ObeliskConfig object
   */
  getCfg(): ObeliskConfig {
    return this.cfg;
  }

  /**
   * Asynchronously load the config file from the assets.
   * @returns Promise that completes when the config file has been loaded
   */
  loadConfig(): Promise<void> {
    return new Promise<void>((resolve, reject) => {
      let xhr = new XMLHttpRequest();

      xhr.overrideMimeType("application/json");
      xhr.open('GET', './assets/config/config.json', true);
      xhr.onload = () => {
        this.cfg = this.rcFix(JSON.parse(xhr.responseText));
        resolve();
      };
      xhr.onerror = err => {
        console.error('Error: retrieving config.json')
        reject('Error: retrieving config.json');
      };
      xhr.send(null);
    });
  }

  /**
   * Returns the README template for empty readmes
   * @returns Textbased markdown template
   */
  getReadmeTemplate() {
    return README_TEMPLATE;
  }

  private rcFix(cfg: ObeliskConfig) {
    if (location.origin === 'https://rc.obelisk.ilabt.imec.be') {
      cfg.oblxHost = location.origin;
      cfg.clientHost = location.origin;
      cfg.clientRedirectUri = location.origin + cfg.clientBasePath;
    }
    return cfg;
  }
}

const README_TEMPLATE = `## General information
Here you can find some general information that you would need to be able to interpret the metrics names and their data records.

## Development
If you want to start developing with this dataset, you can use the [API][] with your own login, or [generate client credentials][client_credentials] to develop an application in your stead.

[API]: https://obelisk.docs.apiary.io
[client_credentials]: /my/clients`;
