import { Component, OnInit } from '@angular/core';
import { fadeAnimation, inOut } from '@core/animations';
import { HeaderService } from '@core/services';

@Component({
  selector: 'app-admin',
  templateUrl: './admin.component.html',
  styleUrls: ['./admin.component.scss'],
  animations: [
    inOut,
    fadeAnimation
  ]
})
export class AdminComponent implements OnInit {

  constructor(private header: HeaderService) { }

  ngOnInit(): void {
    this.header.setTitle('Admin area');
    // this.header.setSidebarState('collapsed');
  }

}
