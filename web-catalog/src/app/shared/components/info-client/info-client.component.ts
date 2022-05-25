import { Component, Input, OnInit } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { InfoClientModalComponent } from '@shared/modals';
import { UsageLimit } from '@shared/model';

@Component({
  selector: 'info-client',
  templateUrl: './info-client.component.html',
  styleUrls: ['./info-client.component.scss'],  
})
export class InfoClientComponent implements OnInit {
  @Input('info') info: UsageLimit
  @Input('asIcon') icon: string = null;

  constructor(
    private modal: NgbModal
  ) { }

  ngOnInit(): void {

  }

  open(event: MouseEvent) {
    event.preventDefault();
    event.stopPropagation();
    const ref = this.modal.open(InfoClientModalComponent, {

    });
    ref.componentInstance.info = this.info;
  }

}