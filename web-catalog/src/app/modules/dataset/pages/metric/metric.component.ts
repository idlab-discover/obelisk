import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ObeliskService } from '@core/services';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { ObeliskDataSource } from '@shared/datasources';
import { Metric, Thing } from '@shared/model';
import { FilterBuilder } from '@shared/utils';
import { debounceTime, map, switchMap, tap } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-metric',
  templateUrl: './metric.component.html',
  styleUrls: ['./metric.component.scss']
})
export class MetricComponent implements OnInit, OnDestroy {
  private dsId: string;

  metric: Metric;
  datasource: ObeliskDataSource<Thing>;
  searchForm: FormGroup;

  constructor(
    private route: ActivatedRoute,
    private obelisk: ObeliskService,
    private router: Router,
    fb: FormBuilder
  ) {
    this.searchForm = fb.group({
      search: []
    });
  }

  ngOnInit() {
    const filter = term => FilterBuilder.regex_i('id', term.trim());
    this.route.data
    this.route.data
      .pipe(
        untilDestroyed(this),
        switchMap((data: any) => {
          this.dsId = data.dataset.id;
          return this.route.paramMap.pipe(
            map(map => decodeURIComponent(map.get('metricId'))),
            switchMap(id => this.obelisk.getMetric(this.dsId, id)));
        }))
      .subscribe(metric => {
        this.metric = metric;
        this.datasource = new ObeliskDataSource(this.obelisk.listThingsOfMetric.bind(this.obelisk, this.dsId, this.metric.id), { filterFn: filter });
        this.searchForm.get('search').valueChanges.pipe(debounceTime(200)).subscribe(term => this.datasource.queryRemote$.next(term));
      });
  }

  ngOnDestroy() {
    this.datasource.cleanUp();
  }

  openThing(thingId: string) {
    this.router.navigate(['ds', this.dsId, 'things', encodeURIComponent(thingId)]);
  }

}
