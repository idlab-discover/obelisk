import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ObeliskService } from '@core/services';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { UntilDestroy } from '@ngneat/until-destroy';
import { NgSelectDataSource } from '@shared/datasources';
import { AccessRequest, Role } from '@shared/model';

@UntilDestroy()
@Component({
  selector: 'app-approve-access-request',
  templateUrl: './approve-access-request.component.html',
  styleUrls: ['./approve-access-request.component.scss']
})
export class ApproveAccessRequestComponent implements OnInit {
  ar: AccessRequest;
  roleSource: NgSelectDataSource<Role>;
  roleForm: FormGroup;

  constructor(
    private activeModal: NgbActiveModal,
    private obelisk: ObeliskService,
    fb: FormBuilder
  ) {
    this.roleForm = fb.group({
      roles: [[]]
    });
  }

  ngOnInit(): void {

  }

  initFromAccessRequest(ar: AccessRequest) {
    this.ar = ar;
    this.roleSource = new NgSelectDataSource(this.obelisk.listDatasetRoles.bind(this.obelisk, ar.dataset.id), { minChars: 0 });
  }

  decline() {
    this.activeModal.close({
      action: 'decline'
    });
  }

  close() {
    this.activeModal.close({
      action: 'approve',
      roles: this.roleForm.get('roles').value
    });
  }

  dismiss() {
    this.activeModal.dismiss();
  }

  get title() {
    return this.ar?.team ? 'Team' : 'User';
  }
}
