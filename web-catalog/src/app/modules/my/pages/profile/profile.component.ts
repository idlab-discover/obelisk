import { Component, OnInit } from '@angular/core';
import { AuthService, HeaderService } from '@core/services';
import { ObeliskService } from '@core/services/obelisk.service';
import { ObeliskAuthClient } from '@obelisk/auth';
import { User } from '@shared/model/types';
import { Utils } from '@shared/utils';

@Component({
  selector: 'app-profile',
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.scss']
})
export class ProfileComponent implements OnInit {
  profile: Partial<User>;
  avatarUri: string;
  idToken: { [key: string]: any }
  
  private client: ObeliskAuthClient;

  constructor(
    private obelisk: ObeliskService,
    private header: HeaderService,
    auth: AuthService,
  ) {
    this.client = auth.getClient();
  }

  ngOnInit(): void {
    this.header.setTitle('My Account')
    this.obelisk.getProfile().subscribe(me => {
      this.profile = me;
      this.idToken = this.client.getTokens().idToken;
      const pic = this.idToken['picture'];
      this.avatarUri = !!pic ? pic : Utils.generateAvatarImgUrl(me.firstName, me.lastName, 64);
    });
  }

}
