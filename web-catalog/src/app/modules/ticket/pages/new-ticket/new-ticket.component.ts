import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ConfirmService, ObeliskService } from '@core/services';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MdPreviewComponent } from '@shared/modals';
import { switchMap } from 'rxjs/operators';
import { TicketService } from '../../ticket.service';

@Component({
  selector: 'app-new-ticket',
  templateUrl: './new-ticket.component.html',
  styleUrls: ['./new-ticket.component.scss']
})
export class NewTicketComponent implements OnInit {
  ticketForm: FormGroup;

  constructor(
    private confirm: ConfirmService,
    private obelisk: ObeliskService,
    private modals: NgbModal,
    private router: Router,
    private ticket: TicketService,
    fb: FormBuilder
  ) {
    this.ticketForm = fb.group({
      summary: [null, Validators.required],
      description: [null, Validators.required]
    });
  }

  ngOnInit(): void {
    Promise.resolve().then(_ => this.ticket.openTicketFrame());
  }

  reset() {
    this.ticketForm.reset();
  }

  preview() {
    const ref = this.modals.open(MdPreviewComponent, { size: 'lg' });
    ref.componentInstance.init(this.ticketForm.get('description').value, this.ticketForm.get('summary').value);
  }

  submitTicket() {
    if (this.ticketForm.invalid) {
      return;
    }
    const issue = this.ticketForm.value;
    this.obelisk.createPersonalIssue(issue).pipe(
      switchMap(_ => {
        this.ticket.removeTicketFrame();
        return this.confirm.prompt("You will be notified when someone is responding to your issue.", {
          title: "Issue created!",
          noLabel: "Show my tickets",
          yesLabel: "Ok",
          buttonStyle: 'yes/no'
        })
      })
    ).subscribe(justClose => {
      this.ticket.fireRefresh();
      if (!justClose) {
        this.router.navigate([{ outlets: { primary: 'my/tickets', x: null } }]);
      }
    });
  }

  maximizeTicketFrame() {
    if (this.ticket) {
      this.ticket?.setFrameSize('large');
    }
  }

  minimizeTicketFrame() {
    if (this.ticket) {
      this.ticket?.setFrameSize('small');
    }
  }

  isLarge() {
    return (this.ticket?.getFrameSize() == 'large') || false;
  }
}