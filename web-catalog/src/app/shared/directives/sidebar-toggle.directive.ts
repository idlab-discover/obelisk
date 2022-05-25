import { Directive, ElementRef, Host, HostBinding, HostListener, Input, Output } from '@angular/core';
import { SettingsService } from '@core/services';
import { SidebarComponent } from '@shared/components';

@Directive({
  selector: '[sbToggle]'
})
export class SidebarToggleDirective {

  constructor(@Host() private sidebar: SidebarComponent) {  }

  @HostListener('click') onClick() {
    this.sidebar.toggle();
  }

}