import { AfterViewInit, Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { HeaderService, ObeliskService } from '@core/services';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { NgSelectDataSource, ObeliskDataSource } from '@shared/datasources';
import { Client, Team, UsageLimit, UsageLimitDetails, UsageLimitValues } from '@shared/model';
import { FilterBuilder, Utils } from '@shared/utils';
import { take } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-rate-limit',
  templateUrl: './rate-limit.component.html',
  styleUrls: ['./rate-limit.component.scss']
})
export class RateLimitComponent implements OnInit, AfterViewInit, OnDestroy {
  details: UsageLimitDetails;
  teamClientSource: ObeliskDataSource<Partial<Client>>;
  userClientSource: ObeliskDataSource<Partial<Client>>;
  client: Client;
  radioForm: FormGroup;
  teamForm: FormGroup;
  myTeams: Team[];
  teamSource: NgSelectDataSource<Team>;
  mode: 'personal' | 'team' = 'personal';

  constructor(
    private obelisk: ObeliskService,
    private header: HeaderService,
    fb: FormBuilder
  ) {
    this.radioForm = fb.group({
      view: 'personal'
    });

    this.teamForm = fb.group({
      team: [null]
    });
  }

  ngAfterViewInit() {
    
  }

  ngOnInit(): void {
    this.header.setTitle('My Rate Limits')
    // Setup team select listener
    this.teamForm.get('team').valueChanges
      .pipe(untilDestroyed(this))
      .subscribe(teamId => this.refreshTeamClientList(teamId));
    // Populate myTeams
    Utils.pagesToArray(this.obelisk.listMyTeams.bind(this.obelisk))
      .pipe(untilDestroyed(this))
      .subscribe(teams => this.myTeams = teams as Team[]);
    // Setup switcher
    this.radioForm.get('view').valueChanges
      .pipe(untilDestroyed(this))
      .subscribe(val => {
        this.mode = val;
        if (val === 'personal') {
          this.obelisk.getAggregatedUsageLimitDetails()
            .pipe(untilDestroyed(this), take(1))
            .subscribe(uld => {
              this.details = {usageRemaining: uld.usageRemaining, usageLimit: uld.aggregatedUsageLimit};
              this.userClientSource.cleanUp()
              this.userClientSource.invalidate();
            });
        }
        this.deselect();
      });
    // Get personal clients
    const filter = { onBehalfOfUser: false }
    this.userClientSource = new ObeliskDataSource(this.obelisk.listMyClients.bind(this.obelisk, { filter }));
    this.teamSource = new NgSelectDataSource(this.obelisk.listMyTeams.bind(this.obelisk), {filterFn: term => FilterBuilder.regex_i('name', `.*${term}.*`)});
    // Get personal usage details
    this.obelisk.getAggregatedUsageLimitDetails()
      .pipe(untilDestroyed(this))
      .subscribe(uld => this.details = {usageRemaining: uld.usageRemaining, usageLimit: uld.aggregatedUsageLimit});
  }

  ngOnDestroy() {
    this.userClientSource.cleanUp();
    this.teamSource.cleanUp();
  }

  loadClient(client: Client) {
    const details: UsageLimitDetails = {
      usageRemaining: client.usageRemaining as UsageLimitValues,
      usageLimit: client.usageLimit as UsageLimit
    }
    this.client = client;
    this.details = details;
  }

  private refreshTeamClientList(teamId: string) {
    if (this.teamClientSource) {
      this.teamClientSource.cleanUp();
    }
    const filter = { onBehalfOfUser: false }
    this.teamClientSource = new ObeliskDataSource(this.obelisk.listMyTeamClients.bind(this.obelisk, teamId, { filter }));
  }

  private deselect() {
    this.client = null;
    this.details = null;
  }
}
