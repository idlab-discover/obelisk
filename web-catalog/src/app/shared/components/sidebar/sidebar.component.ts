import { AfterViewInit, Component, ElementRef, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { HeaderService, SettingsService, SidebarState } from "@core/services";
import { animationFrameScheduler, Observable } from 'rxjs';
import { filter, map } from 'rxjs/operators';
@Component({
  selector: 'sidebar',
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class SidebarComponent implements OnInit, AfterViewInit {
  @ViewChild('sidebar') sidebar: ElementRef<HTMLDivElement>;

  constructor(
    private header: HeaderService,
    private settings: SettingsService
  ) { }

  ngAfterViewInit(): void {
    const that = this;
    animationFrameScheduler.schedule(() => {
      const sbState = that.settings.load()?.sidebarState ?? 'expanded';
      that.setState(sbState);
    }, 0);
  }

  ngOnInit(): void {
  }

  toggle() {
    const el = this.sidebar.nativeElement;
    if (el.clientWidth > 50) {
      this.setState('collapsed');
    } else {
      this.setState('expanded');
    }
  }

  setState(state: SidebarState) {
    const el = this.sidebar.nativeElement;
    switch (state) {
      case 'collapsed':
        el.style.width = '50px';
        this.header.setSidebarState('collapsed');
        this.settings.setSidebarState('collapsed')
        break;
      case 'expanded':
        el.style.width = '219px';
        this.header.setSidebarState('expanded');
        this.settings.setSidebarState('expanded');
        break;
    }
  }

  get expanded$(): Observable<boolean> {
    return this.header.streamSidebarStates().pipe(
      filter(state => state !== 'none'),
      map(state => {
        if (state === 'collapsed') {
          return false;
        } else if (state === 'expanded') {
          return true;
        } else {
          throw new Error('This should not happen!');
        }
      })
    );
  }
}
