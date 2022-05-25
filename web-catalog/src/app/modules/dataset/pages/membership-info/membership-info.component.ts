import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ObeliskService } from '@core/services';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';

@UntilDestroy()
@Component({
  selector: 'app-membership-info',
  templateUrl: './membership-info.component.html',
  styleUrls: ['./membership-info.component.scss']
})
export class MembershipInfoComponent implements OnInit {
  private dsId: string;
  rights: string[] = [];

  constructor(
    private route: ActivatedRoute,
    private obelisk: ObeliskService
    ) { }

  ngOnInit(): void {
    this.route.data
      .pipe(untilDestroyed(this))
      .subscribe(data => {
        this.dsId = data.dataset.id;
        this.refreshData();
      });
  }

  private refreshData() {
    this.obelisk.getMyAggregatedDatasetRights(this.dsId).subscribe(grant =>
      this.rights = grant?.permissions
    )
  }

}
