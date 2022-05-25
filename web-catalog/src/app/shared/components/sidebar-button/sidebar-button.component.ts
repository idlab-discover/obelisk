import { AfterViewInit, Component, ElementRef, Host, Input, OnDestroy, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { Subscription } from 'rxjs';
import { SidebarComponent } from "../sidebar/sidebar.component";

@Component({
  selector: 'sb-btn',
  templateUrl: './sidebar-button.component.html',
  styleUrls: ['./sidebar-button.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class SidebarButtonComponent implements OnInit, OnDestroy {
  @Input() help: string = null;
  @Input() link: string = null;

  hide: boolean = false;
  activeClass: string = 'active';
  private sub: Subscription = null;

  constructor(@Host() private sidebar: SidebarComponent) { }

  ngOnInit(): void {
    if (this.help) {
      this.sub = this.sidebar.expanded$.subscribe(expanded => this.hide = expanded);
    }
    if (this.link) {
      this.activeClass = 'active';
    }else {
      this.activeClass = '';
    }
  }

  ngOnDestroy(): void {
    if (this.sub && !this.sub.closed) {
      this.sub.unsubscribe();
      this.sub = null;
    }
  }

}
