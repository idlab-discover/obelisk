import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ConfirmService, ObeliskService, RoleService } from '@core/services';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MdPreviewComponent } from '@shared/modals';
import { Issue, IssueActivity, User } from '@shared/model';

@Component({
  selector: 'app-ticket-comment, ticket-comment',
  templateUrl: './ticket-comment.component.html',
  styleUrls: ['./ticket-comment.component.scss']
})
export class TicketCommentComponent implements OnInit {
  @Input() comment: IssueActivity
  @Input() ticket: Issue
  @Input() personal: boolean = true;
  @Output() removed: EventEmitter<any> = new EventEmitter<any>();
  @Output() updated: EventEmitter<any> = new EventEmitter<any>()
  user: Partial<User>
  indentClass: string = 'indentNone';
  isComment: boolean = false;
  isActivity: boolean = false;
  edit: boolean = false;
  editForm: FormGroup;

  private me: User;

  constructor(
    private modal: NgbModal,
    private confirm: ConfirmService,
    private obelisk: ObeliskService,
    fb: FormBuilder
  ) {
    this.editForm = fb.group({
      comment: [null, Validators.required]
    });
  }

  ngOnInit(): void {
    this.obelisk.getProfile().subscribe(me => this.setIndent(me));
    this.isActivity = 'changeState' in this.comment;
    this.isComment = (this.comment.comment?.trim().length > 0) ?? false;
  }

  setIndent(user: User) {
    const id = user.id;
    this.user = user;
    if (this.isComment && this.comment?.author?.id !== this.ticket?.reporter?.id) {
      this.indentClass = 'indentCommentMe';
    } else if (this.isComment) {
      this.indentClass = 'indentNone';
    } else if (!this.isComment && this.isActivity) {
      this.indentClass = 'indentState';
    }
  }

  removeComment() {
    const obs = this.personal ? this.obelisk.removePersonalIssueComment(this.ticket._id, this.comment._id) : this.obelisk.removeIssueComment(this.ticket._id, this.comment._id);
    this.confirm.areYouSureThen(
      'Do you really want to remove this comment?<br><b>This is permanent!</b>', obs
    ).subscribe(_ => {
      this.removed.emit({ removed: true, commentId: this.comment._id });
    });
  }

  editComment() {
    this.edit = !this.edit;
    if (this.edit) {
      this.editForm.reset(this.comment);
    }
  }

  updateComment() {
    if (this.editForm.valid) {
      const comment = this.editForm.get('comment').value;
      const obs = this.personal ? this.obelisk.updatePersonalIssueComment(this.ticket._id, this.comment._id, comment) : this.obelisk.updateIssueComment(this.ticket._id, this.comment._id, comment);
      obs.subscribe(_ => {
        this.updated.emit({ updated: true, commentId: this.comment._id });
        this.edit = false;
      });
    }

  }

  ticketNotClosed() {
    return this.ticket?.state != 'CANCELLED' && this.ticket?.state != 'RESOLVED';
  }

  isMyComment() {
    return (this.comment?.author?.id == this.user?.id) || this.user?.platformManager;
  }

  showPreview() {
    const ref = this.modal.open(MdPreviewComponent, {size: 'lg'});
    ref.componentInstance.init(this.editForm.get('comment').value);
  }

  reset() {
    this.editForm.reset(this.comment);
  }
}
