import { Component, Input, OnInit } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { InfoUsageLimitsModalComponent } from '@shared/modals';
import { UsageLimit } from '@shared/model';

@Component({
  selector: 'info-usage-limits',
  templateUrl: './info-usage-limits.component.html',
  styleUrls: ['./info-usage-limits.component.scss']
})
export class InfoUsageLimitsComponent implements OnInit {
  @Input('info') info: UsageLimit

  constructor(
    private modal: NgbModal
  ) { }

  ngOnInit(): void {

  }

  open() {
    const ref = this.modal.open(InfoUsageLimitsModalComponent);
    ref.componentInstance.info = this.info;
  }

}
