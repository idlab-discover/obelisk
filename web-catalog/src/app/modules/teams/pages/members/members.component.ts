import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ConfirmService, ObeliskService, ResponseHandlerService, RoleService, ToastService } from '@core/services';
import { ObeliskDataSource } from '@shared/datasources/obelisk-data-source';
import { Team, TeamUser, User } from '@shared/model';
import { EMPTY } from 'rxjs';
import { switchMap, tap } from 'rxjs/operators';


@Component({
  selector: 'app-members',
  templateUrl: './members.component.html',
  styleUrls: ['./members.component.scss']
})
export class MembersComponent implements OnInit, OnDestroy {
  collapsed: boolean = true;
  inviteForm: FormGroup;
  searchForm: FormGroup;

  memberSource: ObeliskDataSource<Partial<User>> = null;

  team: Partial<Team>;

  canManageTeam: boolean;

  constructor(
    private role: RoleService,
    private obelisk: ObeliskService,
    private route: ActivatedRoute,
    private confirm: ConfirmService,
    private toast: ToastService,
    private respHandler: ResponseHandlerService,
    fb: FormBuilder
  ) {

    this.searchForm = fb.group({
      search: []
    });
  }

  ngOnInit(): void {
    this.searchForm.get('search').valueChanges.subscribe(term => this.memberSource.queryLocal$.next(term.trim()));
    this.route.data.pipe(
      switchMap(data => {
        this.team = data.team;
        return this.role.canManageTeam$(data.team.id, true).pipe(tap(canManageTeam => this.canManageTeam = canManageTeam));
      })
    ).subscribe(asManager => {
      this.memberSource = new ObeliskDataSource(this.obelisk.listTeamMembers.bind(this.obelisk, this.team.id, asManager), {
        filterAttributes: ['user.email', 'user.firstName', 'user.lastName']
      });
    });
  }

  ngOnDestroy() {
    this.memberSource.cleanUp();
  }

  remove($event: MouseEvent, memberId: string) {
    this.confirm.areYouSureThen(
      'Do you really want to remove this member form your Team?',
      this.obelisk.removeTeamMember(this.team.id, memberId)
    ).subscribe(res => this.respHandler.observe(res, {
      success: _ => {
        this.toast.info('Team member removed')
        this.memberSource.invalidate();
      }
    }));
  }

  toggleManager(teamUser: TeamUser) {
    const newState: boolean = teamUser.manager;
    const obs = newState ? this.obelisk.setTeamManager(this.team.id, teamUser.user.id, newState) : this.confirm.areYouSure("Demote this manager to a normal user?", { yesLabel: 'Ok' })
      .pipe(switchMap(ok => {
        if (ok) {
          return this.obelisk.setTeamManager(this.team.id, teamUser.user.id, newState);
        } else {
          teamUser.manager = true;
          return EMPTY;
        }
      }));
    obs.subscribe(res => this.respHandler.observe(res, {
      success: _ => {
        this.toast.success('Saved')
        this.memberSource.invalidate();
      },
      badRequest: _ => {
        this.toast.error('Error toggling saving manager status');
        this.memberSource.invalidate();
      },
      error: _ => {
        this.toast.error('Error toggling saving manager status');
        this.memberSource.invalidate();
      }
    }
    ));
  }

}
