import { AfterViewInit, Component, Input, OnInit, ViewEncapsulation } from '@angular/core';
import { User } from '@shared/model';
import { Utils } from '@shared/utils';

@Component({
  selector: 'app-user-detail',
  templateUrl: './user-detail.component.html',
  styleUrls: ['./user-detail.component.scss'],
})
export class UserDetailComponent implements OnInit, AfterViewInit {
  @Input('user') user: User;
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
    return Utils.generateAvatarImgUrl(this.user?.firstName, this.user?.lastName, 36);
  }
}
