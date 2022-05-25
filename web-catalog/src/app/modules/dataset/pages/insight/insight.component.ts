import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ObeliskService } from '@core/services';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { DatasetProjection } from '@shared/model';
import { Tx } from '@shared/utils';
import { ApexOptions } from 'apexcharts';
import moment, { duration } from 'moment';
import { delay, switchMap, tap } from 'rxjs/operators';

const nrFormatter = (precision, prefixOn) => function (value: number, opts?: any) {
  return Tx.number(value, precision, prefixOn);
}

@UntilDestroy()
@Component({
  selector: 'app-insight',
  templateUrl: './insight.component.html',
  styleUrls: ['./insight.component.scss']
})
export class InsightComponent implements OnInit {

  private defaultOptions: ApexOptions = {
    chart: {
      height: 400,
      type: 'area',
    },
    grid: {
      padding: {
        right: 50,
        left: 50
      }
    },
    series: [],
    stroke: {
      curve: 'straight'
    },
    xaxis: {
      type: 'datetime',
      tickAmount: 13,
      labels: {
        formatter: this.timeFormatter,
      },
      axisTicks: {
        show: true,
      },
    },
    dataLabels: {
      enabled: true,
    },
    title: {
      text: 'loading'
    },
    annotations: {
      xaxis: [
        {
          x: moment().add(duration(1, 'month')).valueOf(),
          // label: { text: '1 month' }
        },
        {
          x: moment().add(duration(6, 'months')).valueOf(),
          // label: { text: '6 months' }
        },
      ]
    },
    noData: {
      text: 'Loading data ...',
      align: 'center',
      verticalAlign: 'middle'
    }
  }

  insights = null;
  sizePerDay: number;
  eventsPerDay: number;

  sizeOpt: ApexOptions = {
    ...this.defaultOptions,
    theme: {
      monochrome: {
        enabled: true,
        color: '#085f88',
        shadeTo: 'light',
        shadeIntensity: 0.5

      }
    },
    title: {
      text: 'Projected dataset size'
    },
    yaxis: {
      labels: {
        formatter: this.sizeFormatter,
      },
      forceNiceScale: true,
    },
    dataLabels: {
      enabled: true,
      formatter: this.sizeFormatter
    },
  }

  eventOpt: ApexOptions = {
    ...this.defaultOptions,
    theme: {
      monochrome: {
        enabled: true,
        color: '#5d6985',
        shadeTo: 'light',
        shadeIntensity: 0.5

      }
    },
    title: {
      text: 'Projected total events'
    },
    yaxis: {
      labels: {
        formatter: nrFormatter(1, false),
      },
      forceNiceScale: true,
    },
    dataLabels: {
      enabled: true,
      formatter: nrFormatter(1, false)
    },
  }

  constructor(
    private route: ActivatedRoute,
    private obelisk: ObeliskService,
  ) { }

  ngOnInit(): void {
    this.route.data.pipe(
      untilDestroyed(this),
      switchMap(data => this.obelisk.getDatasetProjection(data.dataset.id))
    )
      .subscribe(insights => {
        this.insights = insights;
        this.sizePerDay = (insights.approxSizeBytesProjection - insights.approxSizeBytes) / 365;
        this.eventsPerDay = (insights.nrOfEventsProjection - insights.nrOfEvents) / 365;
        this.plot(insights);
      });
  }

  private sizeFormatter(value: number, opts?: any) {
    return Tx.fileSize(value, 1);
  }



  private timeFormatter(value: string, timestamp?: number, opts?: any): string | string[] {
    return opts.i != null ? moment(timestamp).format('MMM \'YY') : moment(timestamp).fromNow(false);
  }

  private plot(insights: DatasetProjection) {
    const dataSize = this.getProjection(insights.approxSizeBytes, insights.approxSizeBytesProjection);
    const dataEvents = this.getProjection(insights.nrOfEvents, insights.nrOfEventsProjection);
    this.sizeOpt.series = [{
      name: 'dataset size',
      data: dataSize
    }]
    this.eventOpt.series = [{
      name: 'total events',
      data: dataEvents
    }];
  }

  private getProjection(value: number, projectedValue: number): [number, number][] {
    const now = moment();
    const perDay = (projectedValue - value) / 365;
    const pair = (duration: moment.Duration): [number, number] => {
      const to = now.clone().add(duration);
      const days = duration.asDays();
      return [to.valueOf(), Math.round(value + (perDay * days))];
    }
    return [
      [now.valueOf(), value],
      pair(duration(1, 'week')),
      pair(duration(1, 'month')),
      pair(duration(3, 'month')),
      pair(duration(6, 'month')),
      pair(duration(1, 'year'))
    ];
  }

}
