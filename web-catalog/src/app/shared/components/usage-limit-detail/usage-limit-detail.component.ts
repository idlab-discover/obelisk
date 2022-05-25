import { AfterViewInit, Component, Input, OnChanges, OnInit, SimpleChange, SimpleChanges } from '@angular/core';
import { UsageLimit, UsageLimitDetails, UsageLimitValue, UsageLimitValues } from '@shared/model';

@Component({
  selector: 'usage-limit-detail',
  templateUrl: './usage-limit-detail.component.html',
  styleUrls: ['./usage-limit-detail.component.scss']
})
export class UsageLimitDetailComponent implements OnInit, OnChanges {
  @Input() details: UsageLimitDetails;

  private ul: UsageLimitValues;
  private ur: UsageLimitValues;

  constructor() { }

  ngOnInit(): void {
  }
  
  ngOnChanges(changes: SimpleChanges) {
    if (changes.details.currentValue) {
      this.ul = this.details.usageLimit?.values;
      this.ur = this.details.usageRemaining;
    }
  }

  current(stat: UsageLimitValue): number {
    return this.ul[stat] - this.ur[stat];
  }

  currentOf(stat: UsageLimitValue): string {
    const padLength = this.ul[stat].toString().length;
    return this.current(stat).toString().padStart(padLength)+ '/' + this.ul[stat];
  }

  max(stat: UsageLimitValue): number {
    return this.ul[stat]
  }

  type(stat: UsageLimitValue): string {
    const perc = this.current(stat) / this.max(stat);
    if (perc < 0.65 ) {
      return 'success'
    } if (perc < 0.80) {
      return 'warning'
    } else {
      return 'danger';
    }
  }
}
