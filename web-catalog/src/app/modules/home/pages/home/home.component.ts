import { AfterViewInit, Component, OnDestroy, OnInit } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { AuthService, ConfigService, HeaderService, ObeliskService } from '@core/services';
import { Customization, CustomizationService } from '@core/services/customization.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { ObeliskDataSource } from '@shared/datasources';
import { InfoAnnouncementComponent } from '@shared/modals';
import { Announcement, GlobalMetaStats, ServiceStatus } from '@shared/model/types';
import { AgoPipe } from '@shared/pipes';
import { Colors, Utils } from '@shared/utils';
import { forkJoin, of, timer, zip } from 'rxjs';
import { catchError, switchMapTo, tap } from 'rxjs/operators';

const REFRESH_MS = 60 * 1000;

@UntilDestroy()
@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit, AfterViewInit, OnDestroy {
  title: string;
  stats: GlobalMetaStats;
  newsDatasource: ObeliskDataSource<Announcement>;
  docs = {
    auth: {
      caption: 'Authentication',
      path: '/docs/guides/auth.html',
      help: 'Learn how to authenticate your Client application or your Client’s users with Obelisk'
    },
    filters: {
      caption: 'Filters',
      path: '/docs/tech_reference/filters.html',
      help: 'Learn how to make ue of filter expressions when querying data and metata'
    },
    v2: {
      caption: 'Obelisk v2 Docs',
      path: '/api/v2/docs',
      help: 'Find the old Obelisk v2 docs here'
    }
  };
  status: ServiceStatus;
  datasets: any[] = [];
  Colors = Colors;

  private cz: Customization;
  private lastUpdate: number;

  constructor(
    czs: CustomizationService,
    private agoPipe: AgoPipe,
    private header: HeaderService,
    private router: Router,
    private san: DomSanitizer,
    private obelisk: ObeliskService,
    private auth: AuthService,
    private modal: NgbModal,
    private config: ConfigService
  ) {
    this.cz = czs.load();
    this.header.setTitle(this.cz.brandName);
    this.newsDatasource = new ObeliskDataSource(this.obelisk.listAnnouncements.bind(this.obelisk));
  }

  ngOnInit() {
    this.header.setSidebarState('none');
    this.title = this.cz.brandName;
    // this.obelisk.getGlobalMetaStats().subscribe(stats => this.stats = stats);
    this.obelisk.listPublishedDatasets({ limit: 40 }).subscribe(page => {
      const total = page.items.length;
      const selected = [];
      while (selected.length < 3 && selected.length != page.items.length) {
        const ds = page.items[Math.floor(Math.random() * total)];
        if (selected.every(d => d.id != ds.id)) {
          selected.push(ds);
        }
      }
      this.datasets = selected;
    });
    let i = 0;
    timer(0, REFRESH_MS).pipe(
      untilDestroyed(this),
      switchMapTo(forkJoin({
        stats: this.obelisk.getGlobalMetaStats().pipe(tap(stats => this.stats = stats), catchError(err => of(err))),
        status: this.obelisk.getStatusGlobal().pipe(tap(status => {
          this.status = status;
          this.lastUpdate = Date.now();
        }), catchError(err => of(err)))
      }))
    )
      .subscribe(({ stats, status }) => { });
  }

  ngOnDestroy() {
    this.newsDatasource.cleanUp();
  }

  ngAfterViewInit() {

  }

  goToDataset(dataset: any) {
    this.router.navigate(['ds', dataset.id], {
      state: dataset
    });
  }

  get loggedIn() {
    return this.auth.getClient().loggedIn();
  }

  getBackgroundImgUrl(ds: any) {
    return this.san.bypassSecurityTrustStyle('url(./assets/img/project/' + ds.name + '.jpg), url(./assets/img/project/_fallback.png)');
  }

  get ingestRate() {
    return this.stats?.ingestedEventsRate ? this.stats?.ingestedEventsRate.map(record => record.value) : [];
  }

  get ingestTime() {
    return this.stats?.ingestedEventsRate ? this.stats?.ingestedEventsRate[this.stats.ingestedEventsRate.length - 1]?.timestamp : null;
  }

  get queryRate() {
    return this.stats?.consumedEventsRate ? this.stats?.consumedEventsRate.map(record => record.value) : [];
  }

  get queryTime() {
    return this.stats?.consumedEventsRate ? this.stats?.consumedEventsRate[this.stats?.consumedEventsRate.length - 1]?.timestamp : null;
  }

  goToApiConsole() {
    const host = this.config.getCfg().clientHost;
    const apiConsoleUri = host + '/apiconsole'
    const isRunningLocal = host.startsWith('http://localhost') || host.startsWith('http://127.0.0.1');
    window.open(isRunningLocal ? 'http://localhost:4200' : apiConsoleUri, '_blank');
  }

  goToApiReference() {
    window.open('https://obelisk.docs.apiary.io/', '_blank');
  }

  openAnnouncement(announcement: Announcement) {
    const ref = this.modal.open(InfoAnnouncementComponent, { size: 'lg', scrollable: true });
    ref.componentInstance.initFromAnnouncement(announcement);
  }

  get history() {
    if (this.status?.history == null) {
      return [];
    } else {
      return this.status.history
    }
  }

  get statusTooltipMsg() {
    return `Status history of last 30 mins.<br><small>Updated ${this.agoPipe.transform(this.lastUpdate)}</small>`
  }
}
