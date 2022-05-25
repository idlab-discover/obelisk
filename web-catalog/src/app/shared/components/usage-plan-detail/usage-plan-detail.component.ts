import { Component, Input, OnInit } from '@angular/core';
import { UsagePlan, UsagePlanDetails } from '@shared/model';

@Component({
  selector: 'usage-plan-detail',
  templateUrl: './usage-plan-detail.component.html',
  styleUrls: ['./usage-plan-detail.component.scss']
})
export class UsagePlanDetailComponent implements OnInit {
  @Input('details') details: UsagePlanDetails;

  constructor() { }

  ngOnInit(): void {

  }

  get maxUsers() {
    return this.details.usagePlan.maxUsers;
  }
  
  get maxClients() {
    return this.details.usagePlan.maxClients;
  }

  get currentUsers() {
    return this.maxUsers - this.details.usersRemaining;
  }

  get currentClients() {
    return this.maxClients - this.details.clientsRemaining;
  }

  type(cur: number, max: number):string {
    const perc = cur / max;
    if (perc < 0.65 ) {
      return 'success'
    } if (perc < 0.80) {
      return 'warning'
    } else {
      return 'danger';
    }
  }

  currentOf(cur: number, max: number): string {
    const padLength = max.toString().length;
    return cur.toString().padStart(padLength)+ '/' + max;
  }

}
