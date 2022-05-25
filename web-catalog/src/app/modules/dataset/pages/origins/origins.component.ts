import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ObeliskService } from '@core/services/obelisk.service';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { ObeliskDataSource } from '@shared/datasources/obelisk-data-source';
import { Origin } from '@shared/model/types';

@UntilDestroy()
@Component({
  selector: 'app-origins',
  templateUrl: './origins.component.html',
  styleUrls: ['./origins.component.scss']
})
export class OriginsComponent implements OnInit, OnDestroy {
  private dsId: string;
  datasource: ObeliskDataSource<Origin>;

  constructor(private route: ActivatedRoute, private obelisk: ObeliskService) {
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
    this.datasource = new ObeliskDataSource(this.obelisk.listDatasetOrigins.bind(this.obelisk, this.dsId));
  }
}
