import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ObeliskService, RoleService, ToastService } from '@core/services';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CreateHeaderComponent } from '@shared/components';
import { NgSelectDataSource, ObeliskDataSource } from '@shared/datasources';
import { SecretComponent } from '@shared/modals';
import { Client, ClientInput, Dataset, PermissionTuple, Permission_ALL, Team } from '@shared/model';
import { switchMap } from 'rxjs/operators';

@Component({
  selector: 'app-clients',
  templateUrl: './clients.component.html',
  styleUrls: ['./clients.component.scss']
})
export class ClientsComponent implements OnInit, OnDestroy {
  clientsSource: ObeliskDataSource<Partial<Client>>
  datasetsSource: NgSelectDataSource<Dataset>;
  clientForm: FormGroup;
  maxClientsReached: boolean = false;

  private teamId: string;
  private defaultValues: any;

  readonly FULL_RIGHTS: PermissionTuple = Permission_ALL;

  constructor(
    private obelisk: ObeliskService,
    private toast: ToastService,
    private route: ActivatedRoute,
    private modal: NgbModal,
    private role: RoleService,
    fb: FormBuilder
  ) {
    this.clientForm = fb.group({
      name: ['', Validators.required],
      datasets: [[]],
      confidential: [false],
      onBehalfOfUser: [false],
      scope: [['READ']],
    });
    this.defaultValues = { ... this.clientForm.value };
  }

  ngOnInit(): void {
    this.route.data.subscribe(data => {
      const team = data.team as Team;
      this.teamId = team.id;
      this.maxClientsReached = team.clientsRemaining === 0;
      this.datasetsSource = new NgSelectDataSource(this.obelisk.listTeamDatasets.bind(this.obelisk, data.team.id));
      this.clientsSource = new ObeliskDataSource(this.obelisk.listTeamClients.bind(this.obelisk, data.team.id));
    });
  }

  ngOnDestroy() {
    this.datasetsSource.cleanUp();
    this.clientsSource.cleanUp();
  }

  add(comp: CreateHeaderComponent<any>) {
    const name = this.clientForm.get('name').value;
    const confidential = this.clientForm.get('confidential').value;
    const onBehalfOfUser = this.clientForm.get('onBehalfOfUser').value;
    const datasets = this.clientForm.get('datasets').value;
    const scope = this.clientForm.get('scope').value;
    const restrictions = datasets?.map(ds => ({ datasetId: ds, permissions: this.FULL_RIGHTS }));
    const input: ClientInput = {
      name,
      confidential,
      onBehalfOfUser,
      restrictions,
      scope,
      redirectURIs: [],
      properties: {}
    }
    this.obelisk.createTeamClient(this.teamId, input).subscribe(resp => {
      if (resp.responseCode === 'SUCCESS') {
        const client = resp.item;
        if (client.secret) {
          this.showConfidentialSecretModal(client);
        }
        this.clientForm.reset(this.defaultValues);
        this.clientsSource.invalidate();
        comp.setCollapsed(true);
        this.reloadTeamData();
        this.toast.show('Client created');
      } else {
        this.toast.error('Error while creating client!');
      }
    });
  }

  getDsRestrictions(client: Client) {
    return client.restrictions.map(r => r.dataset.name);
  }

  private reloadTeamData() {
    this.role.isAdmin$().pipe(switchMap(asAdmin => this.obelisk.getTeam(this.teamId, asAdmin))).subscribe(team => {
      this.teamId = team.id;
      this.maxClientsReached = team.clientsRemaining === 0;
    })
  }

  private showConfidentialSecretModal(client: Partial<Client>) {
    const ref = this.modal.open(SecretComponent, {
      backdrop: 'static'
    });
    ref.componentInstance.name = client.name;
    ref.componentInstance.clientId = client.id;
    ref.componentInstance.clientSecret = client.secret;
  }

}
