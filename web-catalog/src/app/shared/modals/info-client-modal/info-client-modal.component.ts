import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Client } from '@shared/model';
import { CheckmarkPipe } from '@shared/pipes';

@Component({
  selector: 'app-info-client-modal',
  templateUrl: './info-client-modal.component.html',
  styleUrls: ['./info-client-modal.component.scss'],
  providers: [CheckmarkPipe]
})
export class InfoClientModalComponent implements OnInit {
  info: Client

  constructor(public activeModal: NgbActiveModal) {
  }

  ngOnInit(): void {
  }

}
