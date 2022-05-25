import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AuthService, ConfirmService, ObeliskService } from '@core/services';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MdPreviewComponent } from '@shared/modals';
import { Issue, User } from '@shared/model';

@Component({
  selector: 'app-ticket-header, ticket-header',
  templateUrl: './ticket-header.component.html',
  styleUrls: ['./ticket-header.component.scss']
})
export class TicketHeaderComponent implements OnInit {
  @Input() ticket: Issue;
  @Input() personal: boolean = true;
  @Output() resolved: EventEmitter<any> = new EventEmitter<any>();
  @Output() removed: EventEmitter<any> = new EventEmitter<any>();
  @Output() updated: EventEmitter<any> = new EventEmitter<any>()
  editForm: FormGroup;
  edit: boolean = false;

  me: Partial<User>;

  constructor(
    private auth: AuthService,
    private modal: NgbModal,
    private confirm: ConfirmService,
    private obelisk: ObeliskService,
    fb: FormBuilder
  ) {
    this.editForm = fb.group({
      description: [null, Validators.required],
      summary: [null, Validators.required]
    });
  }

  ngOnInit(): void {
    const idTok = this.auth.getClient().getTokens().idToken;
    this.me = {
      firstName: idTok.firstName,
      lastName: idTok.lastName
    };
  }

  resolveIssue() {
    this.resolved.emit({ resolved: true });
  }

  removeIssue() {
    this.confirm.areYouSure(
      'Do you really want to remove this comment?<br><b>This is permanent!</b>',
    ).subscribe(yes => {
      if (yes) {
        this.removed.emit({ removed: true });
      }
    });
  }

  editIssue() {
    this.edit = !this.edit;
    if (this.edit) {
      this.editForm.reset(this.ticket);
    }
  }

  updateIssue() {
    if (this.editForm.valid) {
      const obs = this.personal ? this.obelisk.editPersonalIssue(this.ticket._id, this.editForm.value) : this.obelisk.editIssue(this.ticket._id, this.editForm.value);
      obs.subscribe(_ => {
        this.updated.emit({ updated: true });
        this.edit = false;
      });
    }
  }

  showPreview() {
    const ref = this.modal.open(MdPreviewComponent, {size: 'lg'});
    ref.componentInstance.init(this.editForm.get('description').value, this.editForm.get('summary').value);
  }

  reset() {
    this.editForm.reset(this.ticket);
  }

  ticketNotClosed() {
    return this.ticket?.state != 'CANCELLED' && this.ticket?.state != 'RESOLVED';
  }
}
