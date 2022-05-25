import { Component, Input, OnInit, ViewEncapsulation } from '@angular/core';
import { Client } from '@shared/model';
import { Utils } from '@shared/utils';

@Component({
  selector: 'app-client-detail',
  templateUrl: './client-detail.component.html',
  styleUrls: ['./client-detail.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class ClientDetailComponent implements OnInit {

  @Input('client') client: Client;
  @Input('active') active: boolean;
  @Input('activeClass') activeClass: string = 'active';
  @Input('hoverClass') hoverClass: string = "hover";
  @Input('hover') hover: boolean = false;

  constructor() { }

  ngOnInit(): void {
  }

  ngAfterViewInit() {

  }

  getCss() {
    const css = {};
    css[this.activeClass] = this.active;
    css[this.hoverClass] = this.hover
    return css;
  }

  getAvatar(): string {
    return Utils.generateAvatarImgUrl(this.client.name, this.client?.team?.name, 36);
  }

}
