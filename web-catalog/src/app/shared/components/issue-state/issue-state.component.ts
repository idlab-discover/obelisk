import { Component, Input, OnInit } from '@angular/core';
import { IssueState } from '@shared/model';

@Component({
  selector: 'app-issue-state, issue-state',
  templateUrl: './issue-state.component.html',
  styleUrls: ['./issue-state.component.scss']
})
export class IssueStateComponent implements OnInit {
  @Input() state: IssueState;

  constructor() { }

  ngOnInit(): void {

  }

  getCleanedUpState() {
    return this.state?.toLowerCase()?.replace(/_/g, ' ') ?? '-';
  }

  getColor() {
    switch (this.state) {
      case 'IN_PROGRESS': return '#085f88'; // primary
      case 'WAITING_FOR_SUPPORT': return '#77b1cc'; // info
      case 'WAITING_FOR_REPORTER': return '#FFA000'; // warning
      case 'RESOLVED': return '#008148'; // success
      case 'CANCELLED': return '#dd0d3d'; // danger
      default: return '#5d6985'; // secondary
    }
  }

}
