import { Clipboard } from '@angular/cdk/clipboard';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmService, InviteService, ObeliskService, ResponseHandlerService, ToastService } from '@core/services';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { CreateHeaderComponent } from '@shared/components';
import { NgSelectDataSource } from '@shared/datasources/ng-select-data-source';
import { ObeliskDataSource } from '@shared/datasources/obelisk-data-source';
import { Dataset, DatasetInvite, Invite, Role, Team } from '@shared/model';
import { EMPTY, forkJoin, Observable } from 'rxjs';
import { map, shareReplay, switchMap, tap } from 'rxjs/operators';


@UntilDestroy()
@Component({
  selector: 'app-invites',
  templateUrl: './invites.component.html',
  styleUrls: ['./invites.component.scss']
})
export class InvitesComponent implements OnInit, OnDestroy {
  private dataset: Dataset;
  invitesSource: ObeliskDataSource<DatasetInvite>;
  inviteForm: FormGroup;
  rolesSource: NgSelectDataSource<Role>;
  teamSource: ObeliskDataSource<Team>;

  teamAccessGranted: Record<string, boolean> = {};

  constructor(
    private obelisk: ObeliskService,
    private route: ActivatedRoute,
    private router: Router,
    private toast: ToastService,
    private clipboard: Clipboard,
    private confirm: ConfirmService,
    private inviteService: InviteService,
    private respHandler: ResponseHandlerService,
    fb: FormBuilder
  ) {
    this.inviteForm = fb.group({
      roles: [[]],
      disallowTeams: [false]
    });
  }

  ngOnInit(): void {
    this.route.data
      .pipe(untilDestroyed(this))
      .subscribe(data => {
        this.dataset = data.dataset;
        const id = data.dataset.id;
        this.invitesSource = new ObeliskDataSource(this.obelisk.listDatasetInvites.bind(this.obelisk, id));
        this.teamSource = new ObeliskDataSource(this.obelisk.listMyTeams.bind(this.obelisk, id,));
        this.rolesSource = new NgSelectDataSource(this.obelisk.listDatasetRoles.bind(this.obelisk, id));
        this.teamSource.datastream$.pipe(
          switchMap(teams => forkJoin(teams.map(t => this.hasTeamAccess(t.id))).pipe(map(res => [teams, res])))
        )
          .subscribe(([teams, results]: [Team[], boolean[]]) => teams.forEach((t, idx) => this.teamAccessGranted[t.id] = results[idx]));
      });
  }


  private hasTeamAccess(teamId: string): Observable<boolean> {
    return this.obelisk.getTeamDatasetAccess(teamId, this.dataset.id).pipe(
      map(roles => roles != null)
    );
  }

  isDatasetAdded(teamId: string): boolean {
    return this.teamAccessGranted[teamId];
  }

  grantReadAccess(teamId: string): void {
    this.obelisk.addDatasetTeam(this.dataset.id, teamId).subscribe(_ => {
      this.teamSource.invalidate();
      this.router.navigate(['ds',this.dataset.id,'access'], {fragment:'t'+teamId})
    })
  }

  ngOnDestroy() {
    this.invitesSource.cleanUp();
    this.rolesSource.cleanUp();
  }

  addInvite(comp: CreateHeaderComponent<any>) {
    const roleIds: string[] = this.inviteForm.get('roles').value;
    const disallowTeams = this.inviteForm.get('disallowTeams').value;
    this.obelisk.createDatasetInvite(this.dataset.id, roleIds, disallowTeams)
      .subscribe(res => this.respHandler.observe(res, {
        success: _ => {
          this.copyInviteLink(res.item as Invite);
          this.inviteForm.reset({ roles: [], disallowTeams: false });
          this.invitesSource.invalidate();
          comp.setCollapsed(true);
        }
      }));
  }

  remove($event: MouseEvent, id: string) {
    this.confirm.areYouSure('Do you really want to revoke this invite?').pipe(
      switchMap(ok => ok ? this.obelisk.revokeDatasetInvite(this.dataset.id, id) : EMPTY)
    ).subscribe(res => this.respHandler.observe(res, {
      success: _ => {
        this.invitesSource.invalidate();
      }
    }));
  }

  copyInviteLink(invite: Invite) {
    const link = this.inviteService.getDatasetInviteURL(invite.id, this.dataset.id);
    this.clipboard.copy(link);
    this.toast.info("Invite link copied to clipboard");
  }

  listRoles(roles: Role[]) {
    if (roles) {
      return roles.map(r => r.name);
    } else {
      return [];
    }
  }
}
