import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ConfirmService, HeaderService, ObeliskService, ToastService } from '@core/services';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { CreateHeaderComponent } from '@shared/components';
import { ObeliskDataSource } from '@shared/datasources';
import { Dataset, Role, Team } from '@shared/model';
import { Utils } from '@shared/utils';
import { EMPTY } from 'rxjs';
import { find, map, switchMap, tap } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-team-datasets',
  templateUrl: './team-datasets.component.html',
  styleUrls: ['./team-datasets.component.scss']
})
export class TeamDatasetsComponent implements OnInit {
  dsForm: FormGroup;
  datasetSource: ObeliskDataSource<Partial<Dataset>>;

  private team: Team;

  constructor(
    private obelisk: ObeliskService,
    private confirm: ConfirmService,
    private toast: ToastService,
    private route: ActivatedRoute,
    private header: HeaderService,
    fb: FormBuilder
  ) {
    this.dsForm = fb.group({
      name: ['', Validators.required],
      description: [],
    });
    this.route.data.pipe(
      untilDestroyed(this),
      map(data => data.team),
      )
      .subscribe((team) => {;
        this.team = team;
        this.datasetSource = new ObeliskDataSource(this.obelisk.listTeamDatasets.bind(this.obelisk, team.id));
      });
   }

  ngOnInit(): void {
  }

  ngOnDestroy() {
    this.datasetSource.cleanUp();
  }
  
  add(comp: CreateHeaderComponent<any>): void {
    const name = this.dsForm.get('name').value;
    const ownerId = null;
    this.obelisk.createDataset(name, '', ownerId).pipe(
      switchMap(res => Utils.pagesToArray<Role>(this.obelisk.listDatasetRoles.bind(this.obelisk, res.item.id)).pipe(
        map(roles => roles.find(role => role.grant.permissions.includes('READ') && role.grant.permissions.length === 1)),
        map(role => [res.item.id, role] as [string, Role])
      )),
      switchMap(([datasetId, role]) => this.obelisk.setDatasetTeamRoles(datasetId, this.team.id, [role.id]))
    )
    .subscribe(ok => {
      this.dsForm.reset();
      this.datasetSource.invalidate()
      comp.setCollapsed(true);
    });
  }

  lock($event: MouseEvent, datasetId: string) {
    $event.preventDefault();
    $event.stopPropagation();
    this.confirm.areYouSure('Are you sure you want to remove this Dataset?')
    .pipe(switchMap(ok => ok ? this.obelisk.archiveDataset(datasetId) : EMPTY))
      .subscribe(res => {
        if (res.responseCode === 'SUCCESS') {
          this.datasetSource.invalidate();
          this.toast.success('Dataset locked');
        } else {
          this.toast.error('Could not lock dataset!');
        }
      });
  }

  unlock($event: MouseEvent, datasetId: string) {
    $event.preventDefault();
    $event.stopPropagation();
    this.confirm.areYouSure('Are you sure you want to remove this Dataset?')
    .pipe(switchMap(ok => ok ? this.obelisk.unarchiveDataset(datasetId) : EMPTY))
      .subscribe(res => {
        if (res.responseCode === 'SUCCESS') {
          this.datasetSource.invalidate();
          this.toast.success('Dataset locked');
        } else {
          this.toast.error('Could not lock dataset!');
        }
      });
  }

  get dLength(): number {
    return this.dsForm?.get('description')?.value?.length || 0;
  }
}
