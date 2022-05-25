import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-are-you-sure',
  templateUrl: './are-you-sure.component.html',
  styleUrls: ['./are-you-sure.component.scss']
})
export class AreYouSureComponent implements OnInit {
  public message: string;
  public options: AreYouSureOptions = {
    yesLabel: "Yes",
    noLabel: "No"
  }

  constructor( private activeModal: NgbActiveModal) { }

  ngOnInit(): void {
    
  }

  init(message: string, options?: AreYouSureOptions) {
    this.message = message;
    Object.assign(this.options, options);
  }

  hide() {
    this.activeModal.dismiss();
  }

  confirm() {
    this.activeModal.close(true);
  }
 
  decline() {
    this.activeModal.close(false);
  }

}


export interface AreYouSureOptions {
  yesLabel?: string;
  noLabel?: string;
}
