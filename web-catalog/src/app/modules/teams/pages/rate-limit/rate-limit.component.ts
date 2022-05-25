import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ObeliskService } from '@core/services';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { ObeliskDataSource } from '@shared/datasources';
import { Client, Team, UsageLimit, UsageLimitDetails, UsageLimitValues, UsagePlanDetails } from '@shared/model';
import { take } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-rate-limit',
  templateUrl: './rate-limit.component.html',
  styleUrls: ['./rate-limit.component.scss']
})
export class RateLimitComponent implements OnInit, OnDestroy {
  radioForm: FormGroup;
  mode: 'plan' | 'clients' = 'plan';
  clientSource: ObeliskDataSource<Client>;
  client: Client;
  details: UsageLimitDetails;
  upDetails: UsagePlanDetails;

  private team: Partial<Team>;

  constructor(
    private route: ActivatedRoute,
    private obelisk: ObeliskService,
    fb: FormBuilder
  ) {
    this.radioForm = fb.group({
      view: 'plan'
    });

  }

  ngOnInit(): void {
    // Setup switcher
    this.radioForm.get('view').valueChanges
      .pipe(untilDestroyed(this))
      .subscribe(val => {
        this.mode = val;
        if (val === 'plan') {
          this.obelisk.getUsageLimitDetails()
            .pipe(untilDestroyed(this), take(1))
            .subscribe(uld => {
              this.details = uld;
              this.clientSource.invalidate();
            });
        }
        this.deselect();
      });
    this.route.data
    .pipe(untilDestroyed(this))  
    .subscribe(data => {
      this.team = data.team;
      const filter = { onBehalfOfUser: false }
      this.clientSource = new ObeliskDataSource(this.obelisk.listTeamClients.bind(this.obelisk, data.team.id, {filter}));
      
      this.loadUsagePlanDetails(data.team);
    })
  }

  ngOnDestroy() {
    this.clientSource.cleanUp();
  }

  loadClient(client: Client) {
    const details: UsageLimitDetails = {
      usageRemaining: client.usageRemaining as UsageLimitValues,
      usageLimit: client.usageLimit as UsageLimit
    }
    this.client = client;
    this.details = details;
  }

  private deselect() {
    this.client = null;
    this.details = null;
  }


  private loadUsagePlanDetails(team: Team) {
    this.obelisk.getTeamUsagePlan(this.team.id, true).subscribe(up => this.upDetails = {usagePlan: up, usersRemaining: team.usersRemaining, clientsRemaining: team.clientsRemaining});
  }


}
