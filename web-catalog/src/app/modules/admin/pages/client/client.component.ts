import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmService, ToastService } from '@core/services';
import { ObeliskService } from '@core/services/obelisk.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { SecretComponent } from '@shared/modals';
import { Client, Permission, RestrictionInput } from '@shared/model/types';
import { map } from 'rxjs/operators';


@Component({
  selector: 'app-client',
  templateUrl: './client.component.html',
  styleUrls: ['./client.component.scss']
})
export class ClientComponent implements OnInit {
  client: Partial<Client>;
  scopePermissionForm: FormGroup;
  restrictionPermissionForm: FormGroup;
  redirectURIForm: FormGroup;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private confirm: ConfirmService,
    private obelisk: ObeliskService,
    private modal: NgbModal,
    private toast: ToastService,
    private fb: FormBuilder) {
  }

  ngOnInit(): void {
    // this.header.setTitle('Client details');
    this.route.paramMap.pipe(map(p => p.get('clientId'))).subscribe(clientId => {
      this.loadClient(clientId);
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

  private showConfidentialSecretModal(client: Client) {
    const ref = this.modal.open(SecretComponent, {
      backdrop: 'static'
    });
    ref.componentInstance.name = client.name;
    ref.componentInstance.clientId = client.id;
    ref.componentInstance.clientSecret = client.secret;
  }

  private loadClient(clientId: string) {
    this.obelisk.getClientAsAdmin(clientId).subscribe(client => {
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
          this.obelisk.setClientScope(clientId, scope).subscribe(res => {
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

          this.obelisk.setClientRestrictions(clientId, this.changeToRestrictions(change)).subscribe(res => {
            if (res.responseCode === 'SUCCESS') {
              this.toast.success("Scope saved");
              this.client.scope = res.item.scope;
            } else {
              this.toast.error("Unable to save scope!")
              this.loadClient(this.client.id);
            }
          });
        }
      });
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
}
