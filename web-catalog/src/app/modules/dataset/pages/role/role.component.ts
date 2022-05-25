import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, NavigationExtras, Router } from '@angular/router';
import { ConfirmService, ObeliskService, ResponseHandlerService, ToastService } from '@core/services';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { CreateHeaderComponent } from '@shared/components';
import { ObeliskDataSource } from '@shared/datasources';
import { FilterEditorComponent } from '@shared/modals';
import { Dataset, FilterExpressionSchema, PermissionTuple, Permission_ALL, Response, Role, Team, User } from '@shared/model';
import { FilterBuilder, Utils } from '@shared/utils';
import { asapScheduler, combineLatest, scheduled } from 'rxjs';
import { concatAll, debounceTime, map, switchMap, toArray } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-role',
  templateUrl: './role.component.html',
  styleUrls: ['./role.component.scss']
})
export class RoleComponent implements OnInit, OnDestroy {
  role: Role;
  dataset: Partial<Dataset>;
  roleForm: FormGroup;
  userSearchForm: FormGroup;
  teamSearchForm: FormGroup;
  rolesSource: ObeliskDataSource<Partial<Role>>;
  userSource: ObeliskDataSource<User>;
  teamSource: ObeliskDataSource<Team>;

  permissions: PermissionTuple = Permission_ALL;

  private defaultFormValues: any;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private obelisk: ObeliskService,
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
    this.userSearchForm = fb.group({
      filter: []
    });
    this.teamSearchForm = fb.group({
      filter: []
    });
  }

  ngOnInit(): void {
    const userFilter = (term) => {
      const fb = FilterBuilder;
      return fb.or(
        fb.regex_i('firstName', term),
        fb.regex_i('lastName', term),
        fb.regex_i('email', term),
      );
    };
    this.userSearchForm.get('filter').valueChanges.pipe(
      untilDestroyed(this),
      debounceTime(200)
    ).subscribe((txt: string) => this.userSource.queryRemote$.next(txt.trim()));
    this.teamSearchForm.get('filter').valueChanges.pipe(
      untilDestroyed(this),
      debounceTime(200)
    ).subscribe((txt: string) => this.teamSource.queryRemote$.next(txt.trim()));
    combineLatest([
      this.route.paramMap.pipe(map(params => params.get('roleId'))),
      this.route.data.pipe(map(data => data.dataset))
    ]).pipe(
      untilDestroyed(this),
      switchMap(([roleId, ds]) => this.obelisk.getDatasetRole(ds.id, roleId).pipe(map(role => [role, ds])))
    ).subscribe(([role, ds]) => {
      this.role = role;
      this.dataset = ds;
      this.userSource = new ObeliskDataSource(this.obelisk.listDatasetRoleUsers.bind(this.obelisk, this.dataset.id, role.id), { filterFn: userFilter });
      this.teamSource = new ObeliskDataSource(this.obelisk.listDatasetRoleTeams.bind(this.obelisk, this.dataset.id, role.id), { filterFn: term => FilterBuilder.regex_i('name', term) });
      const value = {
        name: role.name,
        description: role.description,
        permissions: role.grant.permissions,
        readFilter: role.grant.readFilter
      };
      this.defaultFormValues = { ...value };
      this.roleForm.reset(value);
      // If any readfilter is set, pick custom filter mode
    });
  }

  ngOnDestroy() {
    this.userSource.cleanUp();
    this.teamSource.cleanUp();
  }

  save(comp: CreateHeaderComponent<any>) {
    const { id, ...input } = this.roleForm.value;

    const did = this.dataset.id;
    const rid = this.role.id;
    const arr = [
      this.obelisk.setDatasetRoleName(did, rid, input.name),
      this.obelisk.setDatasetRoleDescription(did, rid, input.description),
      this.obelisk.setDatasetRolePermissions(did, rid, input.permissions),
      this.obelisk.setDatasetRoleReadFilter(did, rid, input.readFilter),
    ]
    scheduled(arr, asapScheduler).pipe(concatAll(), toArray()).subscribe(arr => {
      const success = arr.reduce((acc, curr) => acc && ('SUCCESS' === curr.responseCode), true);
      if (success) {
        this.toast.success('Role updated')
        this.obelisk.getDatasetRole(did, rid).subscribe(role => this.role = role);
        this.resetForm();
        comp.setCollapsed(true);
      } else {
        this.toast.error('Error while updating role!')
      }
    });
  }

  remove() {
    this.confirm.areYouSureThen<Response<Role>>(
      'Remove this role, even when there might be users or teams assigned to it?',
      this.obelisk.removeRole(this.dataset.id, this.role.id)
    )
      .subscribe(res => this.respHandler.observe(res, {
        success: _ => {
          this.toast.success('Role removed')
          this.resetForm();
          this.router.navigate([".."], { relativeTo: this.route });
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

  resetForm() {
    this.roleForm.reset(this.defaultFormValues);
  }

  stopPropagate(event: MouseEvent) {
    event.preventDefault();
    event.stopPropagation();
  }

  goToUserAccessControl(user: User) {
    const opt: NavigationExtras = {
      relativeTo: this.route,
      fragment: 'u' + user.id
    }
    this.router.navigate(['../../access'], opt);
  }

  goToTeamAccessControl(team: Team) {
    const opt: NavigationExtras = {
      relativeTo: this.route,
      fragment: 't' + team.id
    }
    this.router.navigate(['../../access'], opt);
  }

  get filterActive() {
    return JSON.stringify(this.roleForm?.get('readFilter')?.value) != '{}';
  }

}