import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ObeliskService } from '@core/services';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { ObeliskDataSource } from '@shared/datasources';
import { Metric, Thing } from '@shared/model';
import { FilterBuilder } from '@shared/utils';
import { debounceTime, map, switchMap } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-thing',
  templateUrl: './thing.component.html',
  styleUrls: ['./thing.component.scss']
})
export class ThingComponent implements OnInit, OnDestroy {

  private dsId: string;

  thing: Thing;
  datasource: ObeliskDataSource<Metric>;
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
    const filter = term => FilterBuilder.regex_i('id',  term.trim());
    this.route.data
      .pipe(
        untilDestroyed(this),
        switchMap((data: any) => {
          this.dsId = data.dataset.id;
          return this.route.paramMap.pipe(
            map(map => decodeURIComponent(map.get('thingId'))),
            switchMap(id => this.obelisk.getThing(this.dsId, id))
          );
        }))
      .subscribe(thing => {
        this.thing = thing;
        this.datasource = new ObeliskDataSource(this.obelisk.listMetricsOfThing.bind(this.obelisk, this.dsId, this.thing.id), {filterFn: filter});
        this.searchForm.get('search').valueChanges.pipe(debounceTime(200)).subscribe(term => this.datasource.queryRemote$.next(term));
      });
  }

  ngOnDestroy() {
    this.datasource.cleanUp();
  }

  openMetric(metricId: string) {
    this.router.navigate(['ds', this.dsId, 'metrics', encodeURIComponent(metricId)]);
  }

}
