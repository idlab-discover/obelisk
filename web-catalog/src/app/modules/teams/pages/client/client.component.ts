import { Component, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmService, ObeliskService, ResponseHandlerService, RoleService, ToastService } from '@core/services';
import { NgbDate, NgbInputDatepicker, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { NgSelectDataSource } from '@shared/datasources';
import { FilterEditorComponent, SecretComponent } from '@shared/modals';
import { Client, DataRemovalRequestInput, Dataset, FilterExpressionSchema, Metric, Permission, RestrictionInput } from '@shared/model';
import { FilterBuilder, Utils } from '@shared/utils';
import moment from 'moment';
import { combineLatest, defer } from 'rxjs';
import { map, switchMap, tap } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-client',
  templateUrl: './client.component.html',
  styleUrls: ['./client.component.scss']
})
export class ClientComponent implements OnInit {
  @ViewChild('datepicker') datepicker: NgbInputDatepicker;
  client: Partial<Client>;
  scopePermissionForm: FormGroup;
  restrictionPermissionForm: FormGroup;
  redirectURIForm: FormGroup;

  private teamId: string;
  private canManage: boolean = false;
  private owner: boolean = false;

  datasetSource: NgSelectDataSource<Dataset>;
  metricSource: NgSelectDataSource<Metric>;

  removalForm: FormGroup;

  hoveredDate: NgbDate | null = null;
  fromDate: NgbDate | null;
  toDate: NgbDate | null;


  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private obelisk: ObeliskService,
    private respHandler: ResponseHandlerService,
    private modal: NgbModal,
    private toast: ToastService,
    private confirm: ConfirmService,
    private fb: FormBuilder,
    private role: RoleService
  ) {
    this.removalForm = fb.group({
      dataset: [null, Validators.required],
      metrics: [null, Validators.required],
      timespan: ['', [
        Validators.pattern(/(^[0-3]\d\/[0-1]\d\/[1-2]\d\d\d - [0-3]\d\/[0-1]\d\/[1-2]\d\d\d$)|(^\* - \*$)/)
      ]],
      filter: [{}],
    })
  }

  ngOnInit(): void {
    combineLatest([
      this.route.paramMap.pipe(map(p => p.get('clientId'))),
      this.route.data.pipe(map(data => data.team.id), tap(id => this.teamId = id)),
    ])
      .pipe(
        untilDestroyed(this),
        switchMap(([clientId, teamId]) => this.obelisk.getTeamClient(teamId, clientId).pipe(tap(client => this.loadTeamClient(client)))),
        switchMap(client => this.role.canManageTeam$(this.teamId).pipe(map(canManage => [client, canManage]))),
        switchMap(([client, canManage]: [Client, boolean]) => this.role.myId$().pipe(map(myId => myId == client?.user?.id), map(owner => [canManage, owner]))),
      )
      .subscribe(([canManage, owner]) => {
        this.canManage = canManage;
        this.owner = owner;
        this.datasetSource = new NgSelectDataSource(this.obelisk.listTeamDatasets.bind(this.obelisk, this.teamId));
      });

    this.removalForm.get('dataset').valueChanges.subscribe(ds => this.loadMetrics(ds.id));
  }

  removeURI(uri: string) {
    const uris = this.client.redirectURIs;
    const newURIs = uris.filter(u => u !== uri);
    this.obelisk.setClientRedirectURIs(this.client.id, newURIs).subscribe(res => {
      if (res.responseCode === 'SUCCESS') {
        this.toast.success("RedirectURI removed");
        this.client.redirectURIs = res.item.redirectURIs;
      } else {
        this.toast.error("Unable to remove redirectURI!")
      }
    });
  }

  remove() {
    this.confirm.areYouSureThen(
      'Are you sure you want to delete this client?',
      this.obelisk.removeClient(this.client.id)
    )
      .subscribe(res => {
        if (res.responseCode === 'SUCCESS') {
          this.toast.success('Client removed');
          this.router.navigate(['..'], { relativeTo: this.route });
        } else {
          this.toast.error('Could not remove Client!');
        }
      });
  }

  addURI() {
    const uris = this.client.redirectURIs;
    const uri = this.redirectURIForm.get('uri').value;
    const newURIs = [...uris, uri];
    this.obelisk.setClientRedirectURIs(this.client.id, newURIs).subscribe(res => {
      if (res.responseCode === 'SUCCESS') {
        this.toast.success("RedirectURI added");
        this.client.redirectURIs = res.item.redirectURIs;
        this.redirectURIForm.reset();
      } else {
        this.toast.error("Unable to add redirectURI!")
      }
    });
  }

  regenerateSecret() {
    const prompt = 'Are you sure you wish to regenerate this secret?<br>Replacing this secret is <b>irreversable</b>!';
    const opt = { noLabel: "Cancel" }
    this.confirm.areYouSureThen(prompt, this.obelisk.generateSecretForClient(this.client.id), opt)
      .subscribe(secret => {
        const client = { name: this.client.name, id: this.client.id, secret: secret } as Client;
        this.showConfidentialSecretModal(client);
      });
  }

  get canDelete() {
    return this.canManage || this.owner;
    // return combineLatest([
    //   this.role.canManageTeam$(this.teamId, true).pipe(tak),
    //   )
    //   .pipe(map(([manage, owner]) => manage || owner));
  }

  private showConfidentialSecretModal(client: Client) {
    const ref = this.modal.open(SecretComponent, {
      backdrop: 'static'
    });
    ref.componentInstance.name = client.name;
    ref.componentInstance.clientId = client.id;
    ref.componentInstance.clientSecret = client.secret;
  }

  private loadTeamClient(client: Client) {
    this.client = client;
    this.scopePermissionForm = this.fb.group({
      read: client.scope.includes('READ'),
      write: client.scope.includes('WRITE'),
      manage: client.scope.includes('MANAGE'),
    });

    const form = this.fb.group({});
    client.restrictions.forEach(r => {
      form.addControl(r.dataset.id + '##READ', this.fb.control(r.permissions.includes('READ')));
      form.addControl(r.dataset.id + '##WRITE', this.fb.control(r.permissions.includes('WRITE')));
      form.addControl(r.dataset.id + '##MANAGE', this.fb.control(r.permissions.includes('MANAGE')));
    });
    this.restrictionPermissionForm = form;

    this.redirectURIForm = this.fb.group({
      uri: [null, Validators.required]
    });
    this.scopePermissionForm.valueChanges.subscribe({
      next: change => {
        let scope = Object.getOwnPropertyNames(change)
          .filter(key => change[key])
          .map<Permission>(key => key.toUpperCase() as Permission);
        this.obelisk.setClientScope(client.id, scope).subscribe(res => {
          if (res.responseCode === 'SUCCESS') {
            this.toast.success("Scope saved");
            this.client.scope = res.item.scope;
          } else {
            this.toast.error("Unable to save scope!")
          }
        });
      }
    });

    this.restrictionPermissionForm.valueChanges.subscribe({
      next: change => {
        let scope = Object.getOwnPropertyNames(change)
          .filter(key => change[key])
          .map<Permission>(key => key.toUpperCase() as Permission);

        this.obelisk.setClientRestrictions(client.id, this.changeToRestrictions(change)).subscribe(res => {
          if (res.responseCode === 'SUCCESS') {
            this.toast.success("Scope saved");
            this.client.scope = res.item.scope;
          } else {
            this.toast.error("Unable to save scope!")
            this.loadTeamClient(client);
          }
        });
      }
    });
  }

  private changeToRestrictions(change: any): RestrictionInput[] {
    const restrictions = {};
    Object.entries<boolean>(change).forEach(entry => {
      if (entry[1]) {
        const [id, grant] = entry[0].split('##');
        if (!(id in restrictions)) {
          restrictions[id] = [];
        }
        restrictions[id].push(grant);
      }
    });
    return Object.entries(restrictions).map((entry: [string, Permission[]]) => ({ datasetId: entry[0], permissions: entry[1] }));
  }

  private loadMetrics(datasetId: string) {
    this.removalForm.get('metrics').reset();
    this.metricSource = new NgSelectDataSource<Metric>(this.obelisk.listMetrics.bind(this.obelisk, datasetId), { filterFn: t => FilterBuilder.regex_i("id", t) });
  }


  deleteData() {
    if (this.removalForm.valid) {
      const timespan = (this.removalForm.get('timespan').value as string)?.replace(/\s+/, '').split('-');
      const from = timespan[0].trim() == '*' ? 0 : moment(timespan[0], 'DD/MM/YYYY').valueOf();
      const to = timespan[1].trim() == '*' ? moment().valueOf() : moment(timespan[1], 'DD/MM/YYYY').valueOf();
      const datasets = [this.fDataset.value?.id];
      const metrics = this.fMetrics.value;
      const dataRange = { datasets, metrics }
      const filter = this.fFilter.value || {};
      const request: DataRemovalRequestInput = {
        dataRange,
        filter,
        from,
        to,
      };
      const actualObs = defer(() => this.confirm.areYouSureThen('Are you really sure? The deleted data cannot be restored!', this.obelisk.removeTeamClientData(this.teamId, this.client.id, request)))
      this.confirm.areYouSureThen('Are you sure you want to <b>permanently</b> delete all of this team client\'s data? <b>This is irreversible!!</b>', actualObs)
        .subscribe(res => this.respHandler.observe(res, {
          success: _ => {
            this.toast.show('Delete request sent')
            this.removalForm.reset(
              { dataset: null, metrics: null, timespan: '', filter: {} }, { emitEvent: false }
            );
          }
        }));
    }
  }

  toggleDate($event?: MouseEvent) {
    this.datepicker.toggle();
    $event.preventDefault();
    $event.stopPropagation();
  }

  onDateSelection(date: NgbDate) {
    if (!this.fromDate && !this.toDate) {
      this.fromDate = date;
    } else if (this.fromDate && !this.toDate && date && date.after(this.fromDate)) {
      this.toDate = date;
    } else {
      this.toDate = null;
      this.fromDate = date;
    }
    const fromTo = this.ngbDateToString(this.fromDate) + ' - ' + this.ngbDateToString(this.toDate);
    this.removalForm.get('timespan').setValue(fromTo)
  }

  isHovered(date: NgbDate) {
    return this.fromDate && !this.toDate && this.hoveredDate && date.after(this.fromDate) && date.before(this.hoveredDate);
  }

  isInside(date: NgbDate) {
    return this.toDate && date.after(this.fromDate) && date.before(this.toDate);
  }

  isRange(date: NgbDate) {
    return date.equals(this.fromDate) || (this.toDate && date.equals(this.toDate)) || this.isInside(date) || this.isHovered(date);
  }

  ignore($event: MouseEvent) {
    $event.preventDefault();
    $event.stopPropagation();
  }

  setAllTime($event: MouseEvent) {
    $event.preventDefault();
    $event.stopPropagation();
    this.fTimespan.setValue('* - *');
  }

  private ngbDateToString(date: NgbDate) {
    if (date) {
      const d = date.day.toString().padStart(2, '0');
      const m = date.month.toString().padStart(2, '0');
      const y = date.year.toString();
      return `${d}/${m}/${y}`
    } else {
      return '??/??/????';
    }
  }

  selectAllMetrics() {
    const sub = this.metricSource.items$.subscribe(items => this.removalForm.get('metrics').setValue(items.map(m => m.id)))
    this.metricSource.unpageFully();
    sub.unsubscribe();
  }

  deselectAllMetrics() {
    this.removalForm.get('metrics').reset();
  }

  openFilterPanel() {
    const ref = this.modal.open(FilterEditorComponent, { size: 'xl', backdrop: 'static', windowClass: 'special' });
    ref.componentInstance.init(this.removalForm.get('filter').value, FilterExpressionSchema);

    ref.result.then(
      newFilter => {
        this.removalForm.get('filter').setValue(newFilter);
      },
      Utils.doNothing);
  }

  get fTimespan() {
    return this.removalForm.get('timespan');
  }

  get fDataset() {
    return this.removalForm.get('dataset');
  }

  get fMetrics() {
    return this.removalForm.get('metrics');
  }

  get fFilter() {
    return this.removalForm.get('filter');
  }

  get filterActive() {
    return JSON.stringify(this.fFilter?.value)?.trim() != '{}';
  }

}
