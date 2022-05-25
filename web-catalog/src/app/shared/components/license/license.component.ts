import { Component, Input, OnInit } from '@angular/core';
import { LicenseService } from '@core/services';

@Component({
  selector: 'app-license',
  templateUrl: './license.component.html',
  styleUrls: ['./license.component.scss']
})
export class LicenseComponent implements OnInit {
  @Input() licenseUri: string;

  constructor(
    private licenseService: LicenseService
  ) { }

  ngOnInit(): void {
  }

  openLicense() {
    window.open(this.licenseUri, '_blank');
  }

  get licenseName() {
    return this.licenseService.getName(this.licenseUri);
  }

  get licenseSpdx() {
    return this.licenseService.getSpdx(this.licenseName);
  }

}
