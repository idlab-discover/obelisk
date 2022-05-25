import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { ObeliskService } from '@core/services';
import { User } from '@shared/model';
import { Utils } from '@shared/utils';

@Component({
  selector: 'app-user-tag, user-tag',
  templateUrl: './user-tag.component.html',
  styleUrls: ['./user-tag.component.scss']
})
export class UserTagComponent implements OnInit, OnChanges {
  @Input() user: Partial<User>;
  @Input() size: number = 22;

  avatar: string = '';
  name: string = '';

  constructor(private obelisk: ObeliskService) { }

  ngOnInit(): void {
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.user) {
      this.init(this.user);
    }
  }

  init(user: Partial<User>) {
    if (user) {
      this.user = user;
      this.avatar = Utils.generateAvatarImgUrl(user.firstName, user.lastName, this.size);
      this.name = `${user?.firstName} ${user?.lastName}`;
    }
  }

  get fontSize() {
    return `${this.size - Math.floor(this.size / 4) - Math.round(this.size / 10)}px`;
  }

  get dataPresent() {
    return this.user != null;
  }

}
