import { Component, Host, Input, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { SidebarComponent } from '../sidebar/sidebar.component';

@Component({
  selector: 'sb-icon',
  templateUrl: './sidebar-icon.component.html',
  styleUrls: ['./sidebar-icon.component.scss']
})
export class SidebarIconComponent implements OnInit, OnDestroy {
  @Input() icon: string;
  @Input() iconExpanded: string = null;
  @Input() notify: boolean = false;
  @Input() count: number;

  currentIcon: string;
  private sub: Subscription = null;

  constructor(@Host() private sidebar: SidebarComponent) { }

  ngOnInit(): void {
    this.currentIcon = this.icon;
    if (this.iconExpanded) {
      this.sub = this.sidebar.expanded$.subscribe(expanded => {
        this.currentIcon = expanded ? this.iconExpanded : this.icon;
      });
    }
  }

  ngOnDestroy() {
    if (this.sub && !this.sub.closed) {
      this.sub.unsubscribe();
      this.sub = null;
    }
  }

}
