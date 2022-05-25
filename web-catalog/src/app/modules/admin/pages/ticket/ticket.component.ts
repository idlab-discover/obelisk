import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmService, ObeliskService } from '@core/services';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { MdPreviewComponent } from '@shared/modals';
import { Issue, IssueActivity, IssueState, User } from '@shared/model';
import { combineLatest, iif, of } from 'rxjs';
import { map, switchMapTo } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-ticket',
  templateUrl: './ticket.component.html',
  styleUrls: ['./ticket.component.scss']
})
export class TicketComponent implements OnInit {
  ticket: Issue;
  comments: IssueActivity[];
  me: User;
  stateSelectable: boolean = true;
  assignTxt: string;

  answerForm: FormGroup;

  constructor(
    private obelisk: ObeliskService,
    private route: ActivatedRoute,
    private modals: NgbModal,
    private router: Router,
    private confirm: ConfirmService,
    fb: FormBuilder
  ) {
    this.answerForm = fb.group({
      comment: '',
      changeState: null,
      internal: false
    });
    this.answerForm.get('internal').valueChanges.subscribe(checked => this.stateSelectable = !checked);
  }

  ngOnInit(): void {
    this.obelisk.getProfile().subscribe(user => this.me = user);
    this.route.paramMap.pipe(
      untilDestroyed(this),
      map(params => params.get('ticketId'))
    ).subscribe(id => this.loadTicket(id));
  }

  reset() {
    this.answerForm.reset();
  }

  preview() {
    const ref = this.modals.open(MdPreviewComponent, { size: 'lg' });
    ref.componentInstance.init(this.answerForm.get('comment').value);
  }

  reply() {
    if (this.answerForm.valid) {
      const body = this.answerForm.value;
      iif(() => this.assignMode == 'none' && this.comments?.length == 0, this.obelisk.editIssue(this.ticket?._id, { assignee: this.me.id }), of(true))
        .pipe(switchMapTo(this.obelisk.addIssueComment(this.ticket._id, body?.comment, body?.internal ? null : body?.changeState, body?.internal)))
        .subscribe(_ => {
          this.answerForm.reset();
          this.loadTicket(this.ticket._id);
        });
    }
  }

  ticketNotClosed() {
    return this.ticket?.state != 'CANCELLED' && this.ticket?.state != 'RESOLVED';
  }

  onRemove(event) {
    if (event.removed) {
      const idx = this.comments.findIndex(c => c._id == event.commentId);
      this.comments.splice(idx, 1);
    }
  }

  onRemoveIssue(event) {
    if (event.removed) {
      this.obelisk.removeIssue(this.ticket._id).subscribe(_ => {
        this.router.navigate(['admin', 'tickets']);
      });
    }
  }

  onCommentUpdated(event) {
    if (event.updated) {
      this.loadTicket(this.ticket._id);
    }
  }

  onIssueUpdated(event) {
    if (event.updated) {
      this.loadTicket(this.ticket._id);
    }
  }

  onResolve(event) {
    if (event.resolved) {
      this.obelisk.resolveIssue(this.ticket._id).subscribe(_ => {
        this.loadTicket(this.ticket._id);
      });
    }
  }

  private loadTicket(id: string) {
    combineLatest([
      this.obelisk.getIssue(id),
      this.obelisk.listIssueComments(id)
    ]).subscribe(([iss, comments]: [Issue, IssueActivity[]]) => {
      this.ticket = iss;
      this.comments = comments;
      this.setAssignMode();
    });
  }

  openIssue() {
    if (!this.ticketIsOpen()) {
      this.obelisk.addIssueComment(this.ticket._id, null, 'IN_PROGRESS', false).subscribe(_ => this.loadTicket(this.ticket._id));
    }
  }

  getStates(state?: IssueState): string[] {
    if (!state) {
      return [];
    }
    switch (state) {
      case 'WAITING_FOR_SUPPORT': return ['CANCELLED', 'IN_PROGRESS', 'WAITING_FOR_REPORTER', 'RESOLVED'];
      case 'WAITING_FOR_REPORTER': return ['CANCELLED', 'IN_PROGRESS', 'RESOLVED'];
      case 'IN_PROGRESS': return ['CANCELLED', 'WAITING_FOR_REPORTER', 'RESOLVED'];
      case 'RESOLVED': return ['IN_PROGRESS'];
      case 'CANCELLED': return ['IN_PROGRESS'];
      default: return [];
    }
  }

  ticketIsOpen() {
    return this.ticket?.state != 'CANCELLED' && this.ticket?.state != 'RESOLVED';
  }

  setAssignMode() {
    if (this.ticket?.assignee == null) {
      this.assignMode = 'none';
    } else {
      this.assignMode = (this.ticket?.assignee?.id == this.me?.id) ? 'me' : 'other';
    }
    switch (this.assignMode) {
      case 'me':
        this.assignTxt = "unassign myself";
        break;
      case 'other':
        const u = this.ticket?.assignee;
        this.assignTxt = `assigned to ${u.firstName} ${u.lastName}`;
        break;
      case 'none': {
        this.assignTxt = "assign to me";
        break;
      }

    }
  }

  isAnswerFormValid() {
    return (!this.answerForm.get('internal').value && this.answerForm.get('changeState').value != null) || (this.answerForm.get('comment').value != null && this.answerForm.get('comment').value?.length > 0);
  }

  private assignMode: 'none' | 'other' | 'me' = 'none';

  onAssign() {
    switch (this.assignMode) {
      case 'none':
        this.obelisk.editIssue(this.ticket?._id, { assignee: this.me.id }).subscribe(_ => this.loadTicket(this.ticket?._id));
        break;
      case 'me':
        this.obelisk.editIssue(this.ticket?._id, { assignee: null }).subscribe(_ => this.loadTicket(this.ticket?._id));
        break;
      case 'other':
        const u = this.ticket?.assignee;
        this.confirm.areYouSureThen(
          `${u.firstName} ${u.lastName} is already assigned to this ticket.<br>Are you sure you want to unassign him/her and assign this ticket to yourself?`,
          this.obelisk.editIssue(this.ticket?._id, { assignee: this.me.id }))
          .subscribe(_ => this.loadTicket(this.ticket?._id));
        break;
    }
  }


}
