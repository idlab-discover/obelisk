import { Component, OnDestroy, OnInit } from '@angular/core';
import { ConfirmService, HeaderService, ObeliskService, ResponseHandlerService, ToastService } from '@core/services';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ObeliskDataSource } from '@shared/datasources';
import { AccessRequestViewComponent } from '@shared/modals';
import { AccessRequest } from '@shared/model';
import { EMPTY } from 'rxjs';
import { switchMap } from 'rxjs/operators';

@Component({
  selector: 'app-access-requests',
  templateUrl: './access-requests.component.html',
  styleUrls: ['./access-requests.component.scss']
})
export class AccessRequestsComponent implements OnInit, OnDestroy {
  accessRequestSource: ObeliskDataSource<Partial<AccessRequest>>;

  constructor(
    private obelisk: ObeliskService,
    private toast: ToastService,
    private confirm: ConfirmService,
    private modal: NgbModal,
    private header: HeaderService,
    private respHandler: ResponseHandlerService
  ) {
    this.accessRequestSource = new ObeliskDataSource(this.obelisk.listMyAccessRequests.bind(this.obelisk, null));
  }

  ngOnInit(): void {
    this.header.setTitle('My Access Requests')
  }

  ngOnDestroy() {
    this.accessRequestSource.cleanUp();
  }

  remove(event: MouseEvent, ar: AccessRequest) {
    event.preventDefault();
    event.stopPropagation();
    this.confirm.areYouSure("Do you really want to revoke this Access request?")
      .pipe(switchMap(ok => ok ? this.obelisk.removeDatasetAccessRequest(ar.dataset.id, ar.id) : EMPTY))
      .subscribe(res => this.respHandler.observe(res, {
        success: _ => {
          this.toast.show("Access request revoked");
          this.accessRequestSource.invalidate();
        }
      }));
  }

  viewRequest(r: AccessRequest) {
    const ref = this.modal.open(AccessRequestViewComponent);
    ref.componentInstance.loadAccessRequest(r);
    ref.result.then(result => {
      this.obelisk.createDatasetAccessRequest(ref.componentInstance.dataset.id, result.type, result.message)
        .subscribe(res => this.respHandler.observe(res, {
          success: _ => {
            this.toast.show("Access request updated");
            this.accessRequestSource.invalidate();
          }
        }));
    });
  }
}
