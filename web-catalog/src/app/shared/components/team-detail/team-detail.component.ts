import { Component, Input, OnInit } from '@angular/core';
import { Team } from '@shared/model';
import { Utils } from '@shared/utils';

@Component({
  selector: 'app-team-detail',
  templateUrl: './team-detail.component.html',
  styleUrls: ['./team-detail.component.scss']
})
export class TeamDetailComponent implements OnInit {

  @Input('team') team: Team;
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
    return Utils.generateAvatarImgUrl(this.team.name, null, 36);
  }

}
