import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ConfirmService, ResponseHandlerService, ToastService } from '@core/services';
import { ObeliskService } from '@core/services/obelisk.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { CreateHeaderComponent } from '@shared/components';
import { ObeliskDataSource } from '@shared/datasources/obelisk-data-source';
import { FilterEditorComponent } from '@shared/modals';
import { Dataset, FilterExpressionSchema, PermissionTuple, Permission_ALL, Role } from '@shared/model';
import { Utils } from '@shared/utils';
import { EMPTY } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-roles',
  templateUrl: './roles.component.html',
  styleUrls: ['./roles.component.scss'],
})
export class RolesComponent implements OnInit, OnDestroy {
  dataset: Partial<Dataset>;
  roleForm: FormGroup;
  searchForm: FormGroup;
  rolesSource: ObeliskDataSource<Partial<Role>>;

  permissions: PermissionTuple = Permission_ALL;

  private defaultFormValues: any;

  constructor(
    private obelisk: ObeliskService,
    private route: ActivatedRoute,
    private confirm: ConfirmService,
    private toast: ToastService,
    private modal: NgbModal,
    private respHandler: ResponseHandlerService,
    fb: FormBuilder
  ) {
    this.roleForm = fb.group({
      name: ['', Validators.required],
      description: [],
      permissions: [],
      readFilter: {}
    });

    // Set as default form values
    this.defaultFormValues = { ... this.roleForm.value };
  }

  ngOnInit() {
    this.route.data.pipe(
      untilDestroyed(this),
      map(data => data.dataset)
    ).subscribe(ds => {
      this.rolesSource = new ObeliskDataSource<Role>(this.obelisk.listDatasetRoles.bind(this.obelisk, ds.id));
      this.dataset = ds;
    });
  }

  ngOnDestroy() {
    this.rolesSource.cleanUp();
  }

  private resetForm() {
    this.roleForm.setValue({ ...this.defaultFormValues, readFilter: {} });
  }

  addRole(comp: CreateHeaderComponent<any>) {
    const { id, ...input } = this.roleForm.value;
    try {
      this.obelisk.createRole(this.dataset.id, input)
        .subscribe(res => this.respHandler.observe(res, {
          success: _ => {
            this.toast.success('Role created')
            this.rolesSource.invalidate();
            this.resetForm();
            comp.setCollapsed(true);
          }
        }));
    } catch {
      this.toast.error({ body: 'readFilter is not a valid JSON object', header: 'Error creating Role!' });
    }
  }

  remove(event: MouseEvent, roleId: string) {
    event.preventDefault();
    event.stopPropagation();
    this.confirm.areYouSure('Remove this role, even when there might be users assigned to it?').pipe(
      switchMap(ok => ok ? this.obelisk.removeRole(this.dataset.id, roleId) : EMPTY)
    )
      .subscribe(res => this.respHandler.observe(res, {
        success: _ => {
          this.toast.success('Role removed')
          this.rolesSource.invalidate();
          this.resetForm();
        }
      }));
  }

  get dLength(): number {
    return this.roleForm?.get('description')?.value?.length || 0;
  }

  openFilterPanel() {
    const ref = this.modal.open(FilterEditorComponent, { size: 'xl', backdrop: 'static', windowClass: 'special' });
    ref.componentInstance.init(this.roleForm.get('readFilter').value, FilterExpressionSchema);

    ref.result.then(
      newFilter => {
        this.roleForm.get('readFilter').setValue(newFilter);
      },
      Utils.doNothing);
  }

  stopPropagate(event: MouseEvent) {
    event.preventDefault();
    event.stopPropagation();
  }

  get filterActive() {
    return JSON.stringify(this.roleForm?.get('readFilter')?.value) != '{}';
  }

}
