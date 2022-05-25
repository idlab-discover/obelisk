import { Component, Input, OnInit } from '@angular/core';
import { ConfigService } from '@core/services';

@Component({
  selector: 'app-docs-link, docs-link',
  templateUrl: './docs-link.component.html',
  styleUrls: ['./docs-link.component.scss']
})
export class DocsLinkComponent implements OnInit {
  @Input() caption: string;
  @Input() help: string;
  @Input() path: string;
  @Input() theme: 'primary' | 'secondary' = 'primary';

  private host: string;

  constructor(
    private config: ConfigService
  ) { }

  ngOnInit(): void {
    this.host = this.config.getCfg().clientHost;
  }

  goToUrl() {
    window.open(this.host+this.path, '_blank');
  }

}
