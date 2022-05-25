import { Injectable } from '@angular/core';
import licenseInfo from '../../../assets/licenses/licenses.json'

@Injectable({
  providedIn: 'root'
})
export class LicenseService {
  nameToUri: Map<string, string> = new Map();
  uritToName: Map<string, string> = new Map();
  spdxToName: Map<string, string> = new Map();
  nameToSpdx: Map<string, string> = new Map();

  constructor() {
    this.init();
  }

  private init() {
    licenseInfo.licenses.forEach(license => {
      this.nameToUri.set(license.name, license.uri);
      this.uritToName.set(license.uri, license.name);
      this.spdxToName.set(license.spdx, license.name);
      this.nameToSpdx.set(license.name, license.spdx);
    })
  }

  getUri(licenseName: string) {
    return this.nameToUri.get(licenseName);
  }

  getName(licenseUri: string) {
    return this.uritToName.get(licenseUri);
  }

  expandSpdxToName(licenseSpdx: string) {
    return this.spdxToName.get(licenseSpdx);
  }

  getSpdx(licenseName: string) {
    return this.nameToSpdx.get(licenseName);
  }

  listNames(): string[] {
    return licenseInfo.licenses.map(license => license.name).sort((a, b) => a.localeCompare(b));
  }

}
