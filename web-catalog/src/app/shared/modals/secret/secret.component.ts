import { Clipboard } from '@angular/cdk/clipboard';
import { Component, OnInit } from '@angular/core';
import { ToastService } from '@core/services/toast.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-secret',
  templateUrl: './secret.component.html',
  styleUrls: ['./secret.component.scss']
})
export class SecretComponent implements OnInit {
  name: string;
  clientId: string;
  clientSecret: string;

  constructor(
    public activeModal: NgbActiveModal,
    private clipboard: Clipboard,
    private toast: ToastService
  ) { }

  ngOnInit(): void {
    this.copyAsText();
  }

  copyClientSecret() {
    this.clipboard.copy(this.clientSecret);
    this.toast.show("ClientSecret copied");
  }

  copyClientId() {
    this.clipboard.copy(this.clientId);
    this.toast.show("ClientId copied");
  }

  copyAsJson() {
    const json = {
      clientId: this.clientId,
      clientSecret: this.clientSecret
    }
    this.clipboard.copy(JSON.stringify(json, null, 2));
    this.toast.show("Credentials copied as Json");
  }
  
  copyAsText() {
    const txt = 
    `clientId: ${this.clientId}\nclientSecret: ${this.clientSecret}\n`;
    this.clipboard.copy(txt);
    this.toast.show("Credentials copied as Json");
  }
}
