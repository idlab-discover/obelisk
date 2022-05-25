import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-error',
  templateUrl: './error.component.html',
  styleUrls: ['./error.component.scss']
})
export class ErrorComponent implements OnInit {
  message: string;
  errorCode: string;

  constructor(private activeModal: NgbActiveModal) { }

  ngOnInit(): void {
  }

  init(responseCode: string, message: string) {
    this.errorCode = responseCode;
    this.message = message;
  }

  confirm() {
    this.activeModal.close(true);
  }
}
