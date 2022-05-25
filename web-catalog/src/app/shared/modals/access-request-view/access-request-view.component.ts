import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AccessRequest, Dataset, Team } from '@shared/model';

@Component({
  selector: 'app-access-request-view',
  templateUrl: './access-request-view.component.html',
  styleUrls: ['./access-request-view.component.scss']
})
export class AccessRequestViewComponent implements OnInit {
  teams: Team[];
  dataset: Dataset;
  requestForm: FormGroup

  viewMode: 'view' | 'input' = 'input';

  constructor(
    private activeModal: NgbActiveModal,
    private fb: FormBuilder
  ) {
    this.requestForm = fb.group({
      type: fb.group({
        READ: false,
        WRITE: false,
        MANAGE: false,
      }),
      message: ['']
    });
  }

  ngOnInit(): void {
  }

  close() {
    if (this.viewMode === 'input') {
      const values = this.requestForm.value;
      const result = {
        team: values.team || null,
        type: Object.entries(values.type).filter(e => e[1]).map(e => e[0]),
        message: values.message.trim()
      }
      this.activeModal.close({ input: result });
    } else if (this.viewMode === 'view') {
      this.activeModal.close({ view: 'revoke' });
    }
  }

  dismiss() {
    this.activeModal.dismiss();
  }

  get type() {
    return this.requestForm.get('type');
  }

  initForUser(dataset: Dataset) {
    this.dataset = dataset;
  }

  initForTeam(dataset: Dataset, teams: Team[]) {
    this.dataset = dataset;
    this.teams = teams;
    this.prepareFormForTeam();
  }

  loadAccessRequest(r: AccessRequest) {
    this.viewMode = 'view';
    this.dataset = r.dataset as Dataset;
    const type = {
      READ: r.type?.includes('READ') || false,
      WRITE: r.type?.includes('WRITE') || false,
      MANAGE: r.type?.includes('MANAGE') || false
    };
    const input = {
      team: r.team || null,
      type,
      message: r.message
    }
    if (input.team) {
      this.teams = [input.team as Team];
      this.prepareFormForTeam();
    }
    this.requestForm.reset(input);
    this.requestForm.disable();
  }

  teamCompareFn() {
    return (t1,t2) => t1?.id === t2?.id;
  }

  private prepareFormForTeam() {
    this.requestForm.addControl('team', this.fb.control(null, Validators.required));
  }

}
