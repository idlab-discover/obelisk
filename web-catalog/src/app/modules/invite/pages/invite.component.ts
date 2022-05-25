import { Component, OnInit } from '@angular/core';
import { FormBuilder } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CustomizationService } from '@core/services/customization.service';
import { ObeliskService } from '@core/services/obelisk.service';
import { Dataset, Invite, Team, TeamInvite, User } from '@shared/model';
import { Utils } from '@shared/utils';
import { EMPTY, zip } from 'rxjs';
import { map, switchMap, tap } from 'rxjs/operators';

@Component({
  selector: 'app-invite',
  templateUrl: './invite.component.html',
  styleUrls: ['./invite.component.scss']
})
export class InviteComponent implements OnInit {
  brand: string;
  invite: Invite;
  teamInvite: TeamInvite;
  team: Team;
  teams: Team[];
  user: User;
  dataset: Dataset;
  type: InviteType;


  constructor(
    fb: FormBuilder,
    cz: CustomizationService,
    private route: ActivatedRoute,
    private router: Router,
    private obelisk: ObeliskService) {
    this.brand = cz.load().brandName;

  }

  ngOnInit(): void {
    this.route.queryParamMap.pipe(
      map(params => [params.get('iid'), params.get('did'), params.get('tid')]),
      switchMap(([inviteId, datasetId, teamId]) => {
        if (inviteId != null && teamId != null) {
          this.type = 'team';
        }
        else if (inviteId != null && datasetId != null) {
          this.type = 'dataset';
        }
        else {
          this.type = 'invalid';
        }
        switch (this.type) {
          case 'team':
            return this.obelisk.getTeamInvite(teamId, inviteId).pipe(tap(team => {
              this.teamInvite = team.invite;
              this.team = team;
            }));
          case 'dataset':
            return this.obelisk.getDatasetInvite(datasetId, inviteId).pipe(tap(dataset => {
              this.invite = dataset.invite;
              this.dataset = dataset;
            }));
          default:
          case 'invalid':
            return EMPTY;
        }
      }))
      .subscribe();
    this.obelisk.getProfile().subscribe(user => this.user = user);

    Utils.pagesToArray<Team>(this.obelisk.listMyManagedTeams.bind(this.obelisk)).subscribe(teams => this.teams = teams);
  }

  acceptInvite() {
    if (this.type === 'dataset') {
      this.obelisk.acceptDatasetInvite(this.dataset.id, this.invite.id).subscribe(ok => {
        this.router.navigate(['ds', this.dataset.id], {
          replaceUrl: true
        });
      })
    } else if (this.type === 'team') {
      this.obelisk.acceptTeamInvite(this.team.id, this.teamInvite.id).subscribe(ok => {
        this.router.navigate(['teams', this.team.id], {
          replaceUrl: true
        });
      })
    }
  }

  acceptInviteAsTeam(team: Team) {
    if (this.type === 'dataset') {
      this.obelisk.acceptDatasetInviteAsTeam(this.dataset.id, this.invite.id, team.id).subscribe(ok => {
        this.router.navigate(['ds', this.dataset.id], {
          replaceUrl: true
        });
      })
    }
  }

  declineInvite() {
    this.router.navigate(['']);
  }

}

type InviteType = 'dataset' | 'team' | 'invalid';
