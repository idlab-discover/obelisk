import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HeaderService, ToastService } from '@core/services';
import { ConfirmService } from '@core/services/confirm.service';
import { ObeliskService } from '@core/services/obelisk.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CreateHeaderComponent } from '@shared/components';
import { NgSelectDataSource } from '@shared/datasources/ng-select-data-source';
import { ObeliskDataSource } from '@shared/datasources/obelisk-data-source';
import { SecretComponent } from '@shared/modals/';
import { Client, ClientInput, Dataset, PermissionTuple, Permission_ALL } from '@shared/model/types';


@Component({
  selector: 'app-clients',
  templateUrl: './clients.component.html',
  styleUrls: ['./clients.component.scss']
})
export class ClientsComponent implements OnInit, OnDestroy {
  clientsSource: ObeliskDataSource<Partial<Client>>
  datasetsSource: NgSelectDataSource<Dataset>;
  clientForm: FormGroup;

  readonly FULL_RIGHTS: PermissionTuple = Permission_ALL;

  private defaultValues: any;

  constructor(
    private obelisk: ObeliskService,
    private header: HeaderService,
    private modal: NgbModal,
    fb: FormBuilder
  ) {
    this.clientForm = fb.group({
      name: ['', Validators.required],
      datasets: [[]],
      confidential: [false],
      onBehalfOfUser: [false],
      scope: [['READ']],
    });
    this.defaultValues = {...this.clientForm.value};
    this.datasetsSource = new NgSelectDataSource(this.obelisk.listMyDatasets.bind(this.obelisk));
    this.clientsSource = new ObeliskDataSource(this.obelisk.listMyClients.bind(this.obelisk));
  }

  ngOnInit(): void { 
    this.header.setTitle('My Clients');
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
    const restrictions = datasets.map(ds => ({ datasetId: ds, permissions: this.FULL_RIGHTS }));
    const input: ClientInput = {
      name,
      confidential,
      onBehalfOfUser,
      restrictions,
      scope,
      redirectURIs: [],
      properties:{}
    }
    this.obelisk.createClient(input).subscribe(resp => {
      if (resp.responseCode === 'SUCCESS') {
        const client = resp.item;
        this.clientForm.reset(this.defaultValues);
        this.clientsSource.invalidate();
        comp.setCollapsed(true);
        if (client.secret) {
          this.showConfidentialSecretModal(client);
        }
      }
    });
  }
  
  getDsRestrictions(client: Client) {
    return client.restrictions.map(r => r.dataset.name);
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
