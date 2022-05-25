import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ConfirmService, DatasetCommService, ObeliskService, ResponseHandlerService, ToastService } from '@core/services';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { ObeliskDataSource } from '@shared/datasources';
import { ApproveAccessRequestComponent } from '@shared/modals';
import { AccessRequest } from '@shared/model';
import { EMPTY } from 'rxjs';
import { switchMap } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-access-requests',
  templateUrl: './access-requests.component.html',
  styleUrls: ['./access-requests.component.scss']
})
export class AccessRequestsComponent implements OnInit, OnDestroy {
  accessRequestSource: ObeliskDataSource<Partial<AccessRequest>>;
  private dsId: string;

  constructor(
    private route: ActivatedRoute,
    private obelisk: ObeliskService,
    private comm: DatasetCommService,
    private modal: NgbModal,
    private confirm: ConfirmService,
    private toast: ToastService,
    private respHandler: ResponseHandlerService
  ) { }

  ngOnInit(): void {
    this.route.data
      .pipe(untilDestroyed(this))
      .subscribe(data => {
        this.dsId = data.dataset.id;
        this.accessRequestSource = new ObeliskDataSource(this.obelisk.listDatasetAccessReqeuests.bind(this.obelisk, data.dataset.id));
      });
  }

  ngOnDestroy() {
    this.accessRequestSource.cleanUp();
  }

  openAccessRequest(ar: AccessRequest) {
    const ref = this.modal.open(ApproveAccessRequestComponent, { size: 'lg', backdrop: 'static' });
    ref.componentInstance.initFromAccessRequest(ar);
    ref.result.then(result => {
      if (result.action === 'decline') {
        this.confirm.areYouSure("Do you really want to decline this Access request?")
          .pipe(switchMap(ok => ok ? this.obelisk.removeDatasetAccessRequest(this.dsId, ar.id) : EMPTY))
          .subscribe(res => this.respHandler.observe(res, {
            success: _ => {
              this.toast.show("Access request declined");
              this.refresh();
            }
          }));
      } else if (result.action === 'approve') {
        const roleIds = result.roles?.map(r => r.id) || [];
        this.obelisk.acceptAccessRequest(this.dsId, ar.id, roleIds)
          .subscribe(res => this.respHandler.observe(res, {
            success: _ => {
              this.toast.success("Access request accepted");
              this.refresh();
            }
          }));
      }
    });
  }


  private refresh() {
    this.accessRequestSource.invalidate();
    this.obelisk.countDatasetAccessReqeuests(this.dsId).subscribe(nr => this.comm.send(this.dsId, { accessRequests: nr }));
  }
}
