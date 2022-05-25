import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ObeliskService } from '@core/services';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { MdPreviewComponent } from '@shared/modals';
import { Issue, IssueActivity } from '@shared/model';
import { combineLatest } from 'rxjs';
import { map, tap } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-ticket',
  templateUrl: './ticket.component.html',
  styleUrls: ['./ticket.component.scss']
})
export class TicketComponent implements OnInit {
  ticket: Issue;
  comments: IssueActivity[];

  answerForm: FormGroup;

  constructor(
    private obelisk: ObeliskService,
    private modals: NgbModal,
    private route: ActivatedRoute,
    fb: FormBuilder
  ) {
    this.answerForm = fb.group({
      comment: [null, Validators.required]
    });
  }

  ngOnInit(): void {
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

  reply(close: boolean = false) {
    if (this.answerForm.valid) {
      this.obelisk.addPersonalIssueComment(this.ticket._id, this.answerForm.value, close).subscribe(_ => {
        this.answerForm.reset();
        this.loadTicket(this.ticket._id);
      })
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

  onResolve(event) {
    if (event.resolved) {
      this.obelisk.resolvePersonalIssue(this.ticket._id).subscribe(_ => {
        this.loadTicket(this.ticket._id);
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
  
  private loadTicket(id: string) {
    combineLatest([
      this.obelisk.getPersonalIssue(id),
      this.obelisk.listPersonalIssueComments(id)
    ]).subscribe(([iss, comments]: [Issue, IssueActivity[]]) => {
      this.ticket = iss;
      this.comments = comments;
    });
  }

}
