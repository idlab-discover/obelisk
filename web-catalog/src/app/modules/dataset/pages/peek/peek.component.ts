import { DOCUMENT } from '@angular/common';
import { Component, Inject, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmService, ObeliskService, ResponseHandlerService, RoleService, ToastService } from '@core/services';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { AccessRequestViewComponent } from '@shared/modals';
import { AccessRequest, Dataset, Team } from '@shared/model';
import { Colors, Reduce, Tx, Utils } from '@shared/utils';
import moment from 'moment';
import { ChartComponent } from 'ng-apexcharts';
import { EMPTY, Observable, of, zip } from 'rxjs';
import { map, switchMap, tap } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-peek',
  templateUrl: './peek.component.html',
  styleUrls: ['./peek.component.scss']
})
export class PeekComponent implements OnInit {
  @ViewChild('ingestChart', { static: false }) ingestChart: ChartComponent;
  @ViewChild('consumeChart', { static: false }) consumeChart: ChartComponent;
  @ViewChild('streamChart', { static: false }) streamChart: ChartComponent;
  @ViewChild('requestChart', { static: false }) requestChart: ChartComponent;
  @ViewChild('featuredChart', { static: false }) featuredChart: ChartComponent;
  ingestOpt: Partial<ChartComponent>;
  consumeOpt: Partial<ChartComponent>;

  justRefreshed: boolean = false;

  dataset: Partial<Dataset>;
  pendingAccessRequests: AccessRequest[] = [];
  managedTeams: Team[];
  viableTeams: Team[] = [];
  alertIsClosed = false;
  pendingForUser: boolean = false;

  hasWriteButNoRead: boolean = false;


  constructor(
    private obelisk: ObeliskService,
    private route: ActivatedRoute,
    private toast: ToastService,
    private role: RoleService,
    private modal: NgbModal,
    private router: Router,
    private confirm: ConfirmService,
    private respHandler: ResponseHandlerService,
    @Inject(DOCUMENT) private document: Document
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
          formatter: this.tooltipFormatter
        }
      },
      fill: { opacity: 0.3 },
      yaxis: { min: 0, show: false },
      title: {
        text: 'Data in',
        offsetX: 0,
        style: {
          fontSize: '20px'
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
          formatter: this.tooltipFormatter
        }
      },
      fill: { opacity: 0.3 },
      yaxis: { min: 0, show: false },
      title: {
        text: 'Data out',
        offsetX: 0,
        style: {
          fontSize: '20px'
        }
      },
    };
  }

  ngOnInit() {
    
  }

  ngAfterViewInit() {
    this.route.data.pipe(
      untilDestroyed(this),
      map(data => data.dataset),
      switchMap(ds => this.setRequestMode(ds as Dataset))
    ).subscribe(ds => {
      this.dataset = ds
      this.loadData(this.dataset)
    });
  }

  onCloseAlert() {
    this.alertIsClosed = true;
  }

  private setRequestMode(ds: Dataset): Observable<Dataset> {
    this.role.isAdmin$().pipe(
      switchMap(isAdmin => isAdmin ? of(false) : this.role.getDatasetRights$(ds.id).pipe(map(rights => rights?.indexOf('WRITE') > -1 && rights?.indexOf('READ') == 0)))
    )
      .subscribe(ok => this.hasWriteButNoRead = ok);

    const pendingReqs$ = Utils.pagesToArray<AccessRequest>(this.obelisk.listMyAccessRequests.bind(this.obelisk, ds.id));
    const managedTeams$ = Utils.pagesToArray<Team>(this.obelisk.listMyTeams.bind(this.obelisk))
      .pipe(map(teams => teams.filter(t => t.user.manager)));
    return zip(pendingReqs$, managedTeams$).pipe(
      switchMap(([pendingReqs, managedTeams]) => {
        // First reset
        this.pendingAccessRequests = [];
        this.viableTeams = [];
        this.pendingForUser = false;
        if (pendingReqs.length > 0) {
          // Pending requests
          this.pendingAccessRequests = pendingReqs;
          const pendingTeamReqsIds = pendingReqs.filter(pr => pr?.team?.id != null).map(pr => pr.team.id);
          this.viableTeams = managedTeams.filter(t => !pendingTeamReqsIds.includes(t.id));
          this.pendingForUser = pendingReqs.filter(pr => pr.team == null).length > 0;
        } else if (managedTeams.length > 0) {
          // No pending requests but managed teams
          this.managedTeams = managedTeams;
          this.viableTeams = managedTeams;
          this.pendingForUser = false;
        } else {
          // No pending requests and no managed teams
          this.viableTeams = [];
          this.pendingForUser = false;
        }
        return of(ds);
      }));
  }

  private loadData(ds: Partial<Dataset>) {
    this.obelisk.getDatasetMetaStats(ds.id)
      .pipe(untilDestroyed(this))
      .subscribe(meta => {
        this.dataset.metaStats = meta;

        // Plot graphs
        const inTotalRate = meta?.ingestedEventsRate.map(obj => [obj.timestamp, nr(obj.value)]) as [number, number][] || [];
        const outTotalRate = meta?.consumedEventsRate.map(obj => [obj.timestamp, nr(obj.value)]) as [number, number][] || [];
        const outQueriesRate = meta?.queriesConsumedEventsRate?.map(obj => [obj.timestamp, nr(obj.value)]) as [number, number][] || [];
        const outStreamingRate = meta?.streamingConsumedEventsRate.map(obj => [obj.timestamp, nr(obj.value)]) as [number, number][] || [];

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

        // Set justRefreshed state
        this.setJustRefreshed();
      });

  }

  refreshData() {
    if (!this.justRefreshed) {
      this.obelisk.getDatasetPeek(this.dataset.id).subscribe(ds => this.loadData(ds));
    }
  }

  scrollDown() {
    this.document.getElementById('requestAccessButton').scrollIntoView(true);
  }

  requestAccessAsUser() {
    const ref = this.modal.open(AccessRequestViewComponent);
    ref.componentInstance.initForUser(this.dataset);
    ref.result.then(result => {
      this.obelisk.createDatasetAccessRequest(this.dataset.id, result.input.type, result.input.message)
        .subscribe(res => this.respHandler.observe(res, {
          success: _ => {
            this.toast.show("Access request sent");
            this.refreshData();
          }
        }));
    }, Utils.doNothing());
  }

  requestAccessAsTeam() {
    const ref = this.modal.open(AccessRequestViewComponent);
    ref.componentInstance.initForTeam(this.dataset, this.viableTeams);
    ref.result.then(result => {
      this.obelisk.createDatasetTeamAccessRequest(this.dataset.id, result.input.team, result.input.type, result.input.message)
        .subscribe(res => this.respHandler.observe(res, {
          success: _ => {
            this.toast.show("Access request sent");
            this.refreshData();
          }
        }));
    }, Utils.doNothing());
  }

  showPendingAccessRequest(ar: AccessRequest) {
    const ref = this.modal.open(AccessRequestViewComponent);
    ref.componentInstance.loadAccessRequest(ar);
    ref.result.then(result => {
      if (result.view === 'revoke') {
        this.confirm.areYouSure("Do you really want to revoke this Access request?")
          .pipe(switchMap(ok => ok ? this.obelisk.removeDatasetAccessRequest(ar.dataset.id, ar.id) : EMPTY))
          .subscribe(res => this.respHandler.observe(res, {
            success: _ => {
              this.toast.show("Access request revoked");
              this.refreshData();
            }
          }));
      }
    }, Utils.doNothing());
  }

  goToMyAccessRequests() {
    this.router.navigate(['my', 'accessrequests']);
  }

  get nrOfPendingReqs() {
    return this.pendingAccessRequests?.length || 0;
  }

  get viableTeamsLeft() {
    return (this.viableTeams?.length || 0) > 0;
  }

  private setJustRefreshed() {
    this.justRefreshed = true;
    setTimeout(() => {
      this.justRefreshed = false;
    }, 3000);
  }

  private readonly defaultLineOptions: Partial<ChartComponent> = {
    series: [],
    noData: {
      text: 'Loading ...'
    },
    chart: {
      height: 160, type: "line", toolbar: {
        tools: {
          download: true, selection: false, zoom: false, zoomin: false, zoomout: false, pan: false, reset: false
        },
      }
    },
    stroke: { curve: "smooth", width: 2 },
    title: { text: "Data", style: { "fontFamily": "unset" } },
    markers: { size: 0 },
    theme: { mode: "light", palette: "palette5", },
    xaxis: {
      type: "datetime", tooltip: { enabled: false },
      labels: {
        formatter: function (value, timestamp) {
          return moment(timestamp).format("HH:mm ss");
        }
      },
    },
    yaxis: { decimalsInFloat: 0, },
    tooltip: {
      shared: false,
      x: {
        formatter: function (val) {
          return moment(val).format("HH:mm:ss");
        }
      }
    },
    legend: { show: false }
  };

  private tooltipFormatter(val: number, opts?: any) {
    const v = Math.abs(val);
    return (v < 1 ? Tx.round(v, 3) : Tx.number(v, 0)) + ' evt/s';
  }
}

function nr(val: number) {
  if (isNaN(val)) {
    return 0;
  } else {
    return val < 1 ? Tx.round(val, 3) : Tx.round(val, 0);
  }
}
