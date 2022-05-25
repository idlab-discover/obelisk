import { Clipboard } from '@angular/cdk/clipboard';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ConfirmService, InviteService, ObeliskService, ResponseHandlerService, ToastService } from '@core/services';
import { ObeliskDataSource } from '@shared/datasources/obelisk-data-source';
import { Team, TeamInvite } from '@shared/model';
import { EMPTY } from 'rxjs';
import { switchMap } from 'rxjs/operators';


@Component({
  selector: 'app-invites',
  templateUrl: './invites.component.html',
  styleUrls: ['./invites.component.scss'],
})
export class InvitesComponent implements OnInit, OnDestroy {

  searchForm: FormGroup;

  inviteSource: ObeliskDataSource<Partial<TeamInvite>>;

  private team: Partial<Team>;

  constructor(
    private route: ActivatedRoute,
    private obelisk: ObeliskService,
    private toast: ToastService,
    private clipboard: Clipboard,
    private confirm: ConfirmService,
    private inviteService: InviteService,
    private respHandler: ResponseHandlerService,
    fb: FormBuilder
  ) {

    this.searchForm = fb.group({
      search: []
    });
  }

  ngOnInit(): void {
    this.route.data.subscribe(data => {
      this.team = data.team;
      this.inviteSource = new ObeliskDataSource(this.obelisk.listTeamInvites.bind(this.obelisk, data.team.id));
    });
  }

  ngOnDestroy() {
    this.inviteSource.cleanUp();
  }

  remove($event: MouseEvent, id: string) {
    this.confirm.areYouSure('Do you really want to revoke this invite?').pipe(
      switchMap(ok => ok ? this.obelisk.revokeTeamInvite(this.team.id, id) : EMPTY)
    ).subscribe(res => this.respHandler.observe(res, {
      success: _ => {
        this.inviteSource.invalidate();
      }
    }));
  }

  createInvite(): void {
    const id = this.team.id;
    this.obelisk.createTeamInvite(id)
      .subscribe(res => this.respHandler.observe(res, {
        success: _ => {
          this.copyInviteLink(res.item as TeamInvite)
          this.inviteSource.invalidate();
        }
      }));
  }

  copyInviteLink(invite: TeamInvite) {
    const link = this.inviteService.getTeamInviteURL(invite.id, this.team.id)
    this.clipboard.copy(link);
    this.toast.info("Invite link copied to clipboard");
  }

}
