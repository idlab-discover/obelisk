import { HttpClient } from '@angular/common/http';
import { AfterViewInit, Component, ElementRef, Input, OnDestroy, OnInit, Optional, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService, ConfigService, HeaderService, ObeliskService, RoleService, SidebarState } from '@core/services';
import { CustomizationService } from '@core/services/customization.service';
import { ObeliskAuth } from '@obelisk/auth';
import { ServiceStatus } from '@shared/model';
import { Utils } from '@shared/utils';
import { TicketService } from 'app/modules/ticket/ticket.service';
import { environment } from 'environments/environment';
import { Subscription } from 'rxjs';
import { switchMap, tap } from 'rxjs/operators';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
})
export class HeaderComponent implements OnInit, OnDestroy, AfterViewInit {
  @Input() datasetId: string | null;
  @Input() mode: Mode = 'title';
  @ViewChild('bg') bg: ElementRef<HTMLDivElement>;

  isMenuCollapsed = true;

  title: string;
  loggedIn: boolean = false;
  avatarUri: string;
  name: string;
  email: string;
  datasetName: string;
  version: string;
  isAdmin: boolean;
  status: ServiceStatus;


  private client: ObeliskAuth;
  private subs: Subscription[] = [];

  readonly SIDEBAR_EXPAND_STATE_CHANNEL = 'sidebarExpandState';

  constructor(
    private role: RoleService,
    private config: ConfigService,
    private auth: AuthService,
    private obelisk: ObeliskService,
    private router: Router,
    private http: HttpClient,
    @Optional() private ticket: TicketService,
    public header: HeaderService,
    cz: CustomizationService) {
    this.client = auth.getClient();
    this.title = cz.load().brandName;
  }

  ngOnInit() {
    this.version = environment.VERSION;
    this.auth.authReady$.pipe(
      switchMap(_ => this.obelisk.getProfile())
    )
      .subscribe(profile => {
        this.loggedIn = this.client.loggedIn();
        const pic = this.client.getTokens().idToken['picture'];
        this.name = this.client.getTokens().idToken['name'];
        this.email = this.client.getTokens().idToken['email'];
        if (!!pic) {
          this.avatarUri = pic;
        } else {
          this.avatarUri = Utils.generateAvatarImgUrl(profile.firstName, profile.lastName);
        }
      });
    this.role.isAdmin$().subscribe(isAdmin => this.isAdmin = isAdmin);
  }

  ngOnDestroy() {
    for (let i = this.subs.length; i >= 0; i--) {
      const sub = this.subs[i];
      if (sub && !sub.closed) {
        sub.unsubscribe();
        this.subs.splice(i, 1);
      }
    }
  }

  ngAfterViewInit(): void {
    this.subs.push(this.header.streamSidebarStates().subscribe(state => this.onSidebarStateChange(state)));
  }

  openLogout() {
    this.isMenuCollapsed = true;
    this.logout();
  }

  logout() {
    if (this.ticket) {
      this.ticket.removeTicketFrame();
    }
    this.http.get(this.auth.getClient().getLogoutUrl()).subscribe({
      next: _ => {
        this.auth.getClient().logout();
        this.router.navigate(['/']).then(_ => {
          location.reload();
        });
      },
      error: err => {
        this.auth.getClient().logout();
        this.router.navigate(['/']).then(_ => {
          location.reload();
        });
      }
    })
  }

  onSidebarStateChange(state: SidebarState) {
    if (this.bg) {
      switch (state) {
        case 'collapsed':
          this.bg.nativeElement.style.paddingLeft = '50px';
          break;
        case 'expanded':
          this.bg.nativeElement.style.paddingLeft = '219px';
          break;
        default:
        case 'none':
          this.bg.nativeElement.style.paddingLeft = '0px';
          break;
      }
    }
  }

  relativeLink(link: string) {
    return this.router.url + '/' + link;
  }

  goBackRelative(link: string) {
    return this.router.url.slice(0, -link.length);
  }

  isEditable() {
    return this.router.url.endsWith('/edit');
  }

  openDocsLink() {
    this.isMenuCollapsed = true;
    window.open(this.getDocsLink(), '_blank').focus();
  }

  getDocsLink() {
    const host = this.config.getCfg().clientHost;
    const docsUri = host + '/docs'
    const isRunningLocal = host.startsWith('http://localhost') || host.startsWith('http://127.0.0.1');
    return isRunningLocal ? 'http://localhost:8888' : docsUri;
  }

  getApiConsoleLink() {
    const host = this.config.getCfg().clientHost;
    const apiConsoleUri = host + '/apiconsole'
    const isRunningLocal = host.startsWith('http://localhost') || host.startsWith('http://127.0.0.1');
    return isRunningLocal ? 'http://localhost:4200' : apiConsoleUri;
  }

}

export type Mode = 'dataset' | 'title' | 'empty' | 'invisible';


