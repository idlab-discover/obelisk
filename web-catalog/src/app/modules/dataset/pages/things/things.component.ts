import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ObeliskService } from '@core/services';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { ObeliskDataSource } from '@shared/datasources';
import { Metric, Thing } from '@shared/model';

@UntilDestroy()
@Component({
  selector: 'app-things',
  templateUrl: './things.component.html',
  styleUrls: ['./things.component.scss']
})
export class ThingsComponent implements OnInit, OnDestroy {

  private dsId: string;
  datasource: ObeliskDataSource<Thing>;

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
    this.datasource = new ObeliskDataSource(this.obelisk.listThings.bind(this.obelisk, this.dsId));
  }

  openThing(thingId: string) {
    this.router.navigate(['ds', this.dsId, 'things', encodeURIComponent(thingId)]);
  }
}
