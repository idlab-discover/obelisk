import { Injectable } from '@angular/core';
import { ConfigService } from './config.service';

@Injectable({
  providedIn: 'root'
})
export class InviteService {

  private host: string;
  private basePath: string;

  constructor(private config: ConfigService) {
    this.host = this.config.getCfg().clientHost;
    this.basePath = this.config.getCfg().clientBasePath;
  }

  getTeamInviteURL(inviteId: string, teamId: string) {
    return this.host + this.basePath + `/invite?iid=${inviteId}&tid=${teamId}`;
  }

  getDatasetInviteURL(inviteId: string, datasetId: string) {
    return this.host + this.basePath + `/invite?iid=${inviteId}&did=${datasetId}`;
  }
}
