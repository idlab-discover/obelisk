import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-prompt',
  templateUrl: './prompt.component.html',
  styleUrls: ['./prompt.component.scss']
})
export class PromptComponent implements OnInit {
  public message: string;
  public options: PromptOptions = {
    yesLabel: "Yes",
    noLabel: "No",
    yesClassName: 'btn-secondary',
    noClassName: 'btn-outline-secondary',
    buttonStyle: 'yes'
  }

  constructor( private activeModal: NgbActiveModal) { }

  ngOnInit(): void {
    
  }

  init(message: string, options?: PromptOptions) {
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


export interface PromptOptions {
  title?: string;
  yesLabel?: string;
  noLabel?: string;
  yesClassName?: string;
  noClassName?: string;
  buttonStyle: 'yes/no'| 'yes';

}
