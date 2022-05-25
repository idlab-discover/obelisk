import { Component, OnInit, Output, EventEmitter } from '@angular/core';
import { fadeAnimation, inOut } from '@core/animations';
import { HeaderService } from '@core/services';
import { ObeliskService } from '@core/services/obelisk.service';

@Component({
  selector: 'app-my',
  templateUrl: './my.component.html',
  styleUrls: ['./my.component.scss'],
  animations: [
    inOut,
    fadeAnimation
  ]
})
export class MyComponent implements OnInit {

  constructor(private obelisk: ObeliskService, private header: HeaderService) { }

  ngOnInit(): void {
    this.header.setTitle('My Account')
  }

}
