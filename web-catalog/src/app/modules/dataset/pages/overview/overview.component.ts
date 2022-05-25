import { AfterViewInit, Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ObeliskService } from '@core/services/obelisk.service';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { Dataset } from '@shared/model/types';
import { Colors, Reduce, Tx } from '@shared/utils';
import moment from 'moment';
import { ChartComponent } from 'ng-apexcharts';
import { map } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-overview',
  templateUrl: './overview.component.html',
  styleUrls: ['./overview.component.scss']
})
export class OverviewComponent implements OnInit, AfterViewInit {
  dataset: Dataset;

  @ViewChild('ingestChart', { static: false }) ingestChart: ChartComponent;
  @ViewChild('consumeChart', { static: false }) consumeChart: ChartComponent;
  @ViewChild('streamChart', { static: false }) streamChart: ChartComponent;
  @ViewChild('requestChart', { static: false }) requestChart: ChartComponent;
  @ViewChild('featuredChart', { static: false }) featuredChart: ChartComponent;
  public ingestOpt: Partial<ChartComponent>;
  public consumeOpt: Partial<ChartComponent>;
  public requestOpt: Partial<ChartComponent>;
  public streamOpt: Partial<ChartComponent>;
  public featuredOpt: Partial<ChartComponent>;
  public justRefreshed: boolean = false;
  public lastRefreshed: number;
  public optionsLoaded: boolean = false;

  constructor(
    private obelisk: ObeliskService,
    private route: ActivatedRoute
  ) {

    // IN
    this.ingestOpt = {
      series: [],
      noData: { text: 'Loading ...' },
      colors: ['#B66A4A'],
      chart: {
        height: 120,
        sparkline: {
          enabled: true,
        },
        type: 'area',
        stacked: true,
      },
      stroke: { curve: 'straight', width: 2 },
      markers: { size: 0 },
      tooltip: {
        fixed: {
          enabled: true
        },
        x: {
          formatter: function (val) {
            return moment(val).format("HH:mm");
          }
        },
        y: {
          formatter: this.tooltipFormatter('evt/s')
        }
      },
      fill: { opacity: 0.3 },
      yaxis: { min: 0, show: false },
      title: {
        text: 'Data in',
        offsetX: 0,
        style: {
          fontSize: '16px'
        }
      },
    };

    // OUT
    this.consumeOpt = {
      series: [],
      noData: { text: 'Loading ...' },
      chart: {
        height: 120,
        sparkline: {
          enabled: true,
        },
        type: 'area',
        stacked: true,

      },
      stroke: { curve: 'straight', width: 2 },
      markers: { size: 0 },
      tooltip: {
        fixed: {
          enabled: true
        },
        x: {
          formatter: function (val) {
            return moment(val).format("HH:mm");
          }
        },
        y: {
          formatter: this.tooltipFormatter('evt/s')
        }
      },
      fill: { opacity: 0.3 },
      yaxis: { min: 0, show: false },
      title: {
        text: 'Data out',
        offsetX: 0,
        style: {
          fontSize: '16px'
        }
      },
    };

    // STREAMS
    this.streamOpt = {
      series: [],
      noData: {
        text: 'Loading ...'
      },
      title: {
        text: 'Streams',
        offsetX: 0,
        style: {
          fontSize: '16px'
        }
      },
      tooltip: {
        shared: true,
        x: {
          formatter: function (val) {
            return moment(val).format("HH:mm");
          }
        },
        y: [
          { formatter: this.tooltipFormatter('evt/s') },
          { formatter: this.tooltipFormatter(null) },
        ],
        intersect: false
      },
      legend: {
        show: true
      },
      dataLabels: {
        enabled: false
      },
      markers: {
        size: 0
      },
      xaxis: {
        type: "datetime",
        labels: {
          formatter: function (value, timestamp) {
            return moment(timestamp).format("HH:mm");
          }
        },
        tooltip: { enabled: false }
      },
      yaxis: [
        {
          forceNiceScale: true,
          decimalsInFloat: 3,
        },
        {
          opposite: true,
          forceNiceScale: true,
          decimalsInFloat: 0,
        }
      ],
      stroke: {
        curve: ['smooth', 'stepline'],
        width: [2, 2],
        colors: [Colors.lightBrown, Colors.darkBrown],
      },
      fill: {
        type: ['gradient', 'solid'],
        opacity: [0.3, 1],
        gradient: {
          shade: 'light',
          type: 'vertical',
          opacityFrom: 0.8,
          opacityTo: 0.5,
          inverseColors: false,
          stops: [0, 90, 100]
        },
      },
      chart: {
        type: 'line',
        height: 240,
        toolbar: {
          tools: {
            download: false,
            selection: false,
            zoom: false,
            zoomin: false,
            zoomout: false,
            pan: false,
            reset: false
          },
        }
      }
    };

    // REQUEST RATE
    this.requestOpt = {
      series: [],
      noData: {
        text: 'Loading ...'
      },
      chart: {
        animations: { enabled: false },
        height: 280,
        type: "bar",
        stacked: true,
        stackType: 'normal',
        toolbar: {
          tools: {
            download: false,
            selection: false,
            zoom: false,
            zoomin: false,
            zoomout: false,
            pan: false,
            reset: false
          },
        }
      },
      title: {
        text: "Request rates",
        style: {
          fontSize: '16px'
        },
      },
      markers: {
        size: 0
      },
      xaxis: {
        type: "datetime",
        labels: {
          formatter: function (value, timestamp) {
            return moment(timestamp).format("HH:mm");
          }
        },
      },
      yaxis: {
        forceNiceScale: true,
        decimalsInFloat: 2,
      },
      tooltip: {
        shared: true,
        y: { formatter: this.tooltipFormatter('req/s') },
        intersect: false
      },
      legend: {
        show: true
      },
      dataLabels: {
        enabled: false
      }
    };
  }

  ngOnInit() {
    this.route.data.pipe(
      untilDestroyed(this),
      map(data => (data.dataset))
    ).subscribe(ds => this.dataset = ds);
  }

  ngAfterViewInit() {
    this.loadData(this.dataset)
  }

  private loadData(ds: Dataset) {
    // Fire async load of metaStats, which typicaly takes longer
    this.obelisk.getDatasetMetaStats(ds.id)
      .pipe(untilDestroyed(this))
      .subscribe(meta => {
        this.dataset.metaStats = meta;

        // Plot graphs
        const inTotalRate = meta?.ingestedEventsRate.map(obj => [obj.timestamp, nr(obj.value)]) as [number, number][] || [];
        const outTotalRate = meta?.consumedEventsRate.map(obj => [obj.timestamp, nr(obj.value)]) as [number, number][] || [];
        const outQueriesRate = meta?.queriesConsumedEventsRate?.map(obj => [obj.timestamp, nr(obj.value)]) as [number, number][] || [];
        const outStreamingRate = meta?.streamingConsumedEventsRate.map(obj => [obj.timestamp, nr(obj.value)]) as [number, number][] || [];
        const ingestApiRate = meta?.ingestApiRequestRate.map(obj => [obj.timestamp, nr(obj.value)]) as [number, number][] || [];
        const queryEventsApiRate = meta?.eventsQueryApiRequestRate.map(obj => [obj.timestamp, nr(obj.value)]) as [number, number][] || [];
        const queryStatsApiRate = meta?.statsQueryApiRequestRate.map(obj => [obj.timestamp, nr(obj.value)]) as [number, number][] || [];
        const activeStreams = meta?.activeStreams?.map(obj => [obj.timestamp, nr(obj.value)]) as [number, number][] || [];


        // SPARKLINE IN
        let ingestValue = Reduce.mean(inTotalRate);
        const ingestTitle = {
          subtitle: {
            text: 'mean: ' + (ingestValue < 1 ? Tx.round(ingestValue, 3) : Math.round(ingestValue)) + ' evt/s',
            offsetX: 10,
            style: {
              fontSize: '14px'
            }
          }
        };
        const ingestSeries = [{
          name: 'ingest',
          data: inTotalRate,
          color: Colors.lightGreen
        }];
        this.ingestChart.updateOptions(ingestTitle, false, false, false);
        this.ingestOpt.series = ingestSeries;

        // SPARKLINE OUT
        let outValue = Reduce.mean(outTotalRate);
        const consumeSeries = [];
        if (outQueriesRate.length > 0) {
          consumeSeries.push({
            name: 'queries',
            data: outQueriesRate,
            color: Colors.darkBlue
          });
        }
        if (outStreamingRate.length > 0) {
          consumeSeries.push({
            name: 'streams',
            data: outStreamingRate,
            color: Colors.lightBlue
          });
        }
        const consumeTitle = {
          subtitle: {
            text: 'mean: ' + (outValue < 1 ? Tx.round(outValue, 3) : Math.round(outValue)) + ' evt/s',
            offsetX: 10,
            style: {
              fontSize: '14px'
            }
          }
        };
        this.consumeChart.updateOptions(consumeTitle, false, false, false);
        this.consumeOpt.series = consumeSeries;


        // Stream
        const streamSeries = [
          {
            name: 'rate',
            data: outStreamingRate,
            type: 'area',
            color: Colors.lightBrown
          },
          {
            name: 'connected',
            data: activeStreams,
            type: 'line',
            color: Colors.darkBrown
          },
        ];
        const streamOpt: Partial<ChartComponent> = {
          yaxis: [
            {
              forceNiceScale: true,
              decimalsInFloat: 3,
            },
            {
              opposite: true,
              forceNiceScale: true,
              decimalsInFloat: 0,
            }
          ]
        }
        if (activeStreams?.length > 0) {
          const max = Reduce.max(outStreamingRate);
          streamOpt.yaxis[0].max = (max < 1 ) ? max + (max/3)  : max + 1;
          streamOpt.yaxis[1].max = Reduce.max(activeStreams) + 1;
          streamOpt.yaxis[1].tickAmount = streamOpt.yaxis[1].max;
        }
        this.streamChart.updateOptions(streamOpt, false, false, false);
        this.streamOpt.series = streamSeries;

        // Request
        const s = [];
        if (ingestApiRate.length > 0) {
          s.push({
            name: 'Ingest API',
            data: ingestApiRate,
            color: Colors.lightGreen
          });
        }
        if (queryEventsApiRate.length > 0) {
          s.push({
            name: 'Query Events API',
            data: queryEventsApiRate,
            color: Colors.darkBlue
          });
        }
        if (queryStatsApiRate.length > 0) {
          s.push({
            name: 'Query Stats API',
            data: queryStatsApiRate,
            color: Colors.lightBlue
          });
        }
        this.requestOpt.series = s;

        // Set justRefreshed state
        this.setJustRefreshed();
      });

  }

  private formatInOutLabels(val: number, opts?: any): string | string[] {
    return Tx.number(Math.abs(val));
  }

  private tooltipFormatter(suffix: string | null): (val: number, opts: any) => string {
    return function (val: number, opts?: any) {
      const v = Math.abs(val);
      let res = (v < 1 ? Tx.round(v, 3) : Tx.number(v, 0));
      if (suffix != null) {
        res += ` ${suffix}`;
      }
      return res + '';
    }
  };

  refreshData() {
    if (!this.justRefreshed) {
      this.loadData(this.dataset);
    }
  }

  private setJustRefreshed() {
    this.justRefreshed = true;
    this.lastRefreshed = Date.now();
    setTimeout(() => {
      this.justRefreshed = false;
    }, 3000);
  }

}

function nr(val: number) {
  if (isNaN(val)) {
    return 0;
  } else {
    return val < 1 ? Tx.round(val, 3) : Tx.round(val, 0);
  }
}
