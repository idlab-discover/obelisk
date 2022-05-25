import { Component, Host, Input, OnDestroy, OnInit } from '@angular/core';
import { inOut } from '@core/animations';
import { Subscription } from 'rxjs';
import { SidebarComponent } from '../sidebar/sidebar.component';

@Component({
  selector: 'sb-label',
  templateUrl: './sidebar-label.component.html',
  styleUrls: ['./sidebar-label.component.scss'],
  animations: [
    inOut
  ]
})
export class SidebarLabelComponent implements OnInit, OnDestroy {
  @Input() text: string;
  expanded: boolean = false;
  private sub: Subscription = null;

  constructor(@Host() private sidebar: SidebarComponent) { }

  ngOnInit(): void {
    this.sub = this.sidebar.expanded$.subscribe(expanded => this.expanded = expanded)
  }

  ngOnDestroy(): void {
    if (this.sub && !this.sub.closed) {
      this.sub.unsubscribe();
      this.sub = null;
    }
  }

}
