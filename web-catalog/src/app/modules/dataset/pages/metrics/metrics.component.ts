import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ObeliskService } from '@core/services';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { ObeliskDataSource } from '@shared/datasources';
import { Metric } from '@shared/model';

@UntilDestroy()
@Component({
  selector: 'app-metrics',
  templateUrl: './metrics.component.html',
  styleUrls: ['./metrics.component.scss']
})
export class MetricsComponent implements OnInit, OnDestroy {

  private dsId: string;
  datasource: ObeliskDataSource<Metric>;

  constructor(
    private route: ActivatedRoute,
    private obelisk: ObeliskService,
    private router: Router
  ) {
  }

  ngOnInit() {
    this.route.data
      .pipe(untilDestroyed(this))
      .subscribe(data => {
        this.dsId = data.dataset.id;
        this.refreshData();
      });
  }

  ngOnDestroy() {
    this.datasource.cleanUp();
  }

  private refreshData() {
    this.datasource = new ObeliskDataSource(this.obelisk.listMetrics.bind(this.obelisk, this.dsId));
  }

  openMetric(metricId: string) {
    this.router.navigate(['ds', this.dsId, 'metrics', encodeURIComponent(metricId)]);
  }
}
