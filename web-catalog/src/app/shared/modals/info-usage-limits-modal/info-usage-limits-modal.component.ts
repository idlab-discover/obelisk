import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { UsageLimit } from '@shared/model';

@Component({
  selector: 'app-info-usage-limits-modal',
  templateUrl: './info-usage-limits-modal.component.html',
  styleUrls: ['./info-usage-limits-modal.component.scss']
})
export class InfoUsageLimitsModalComponent implements OnInit {
  info: UsageLimit

  constructor(public activeModal: NgbActiveModal) {

  }

  ngOnInit(): void {
  }

}
