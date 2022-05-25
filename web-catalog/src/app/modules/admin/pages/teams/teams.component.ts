import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ObeliskService } from '@core/services';
import { CreateHeaderComponent } from '@shared/components';
import { ObeliskDataSource } from '@shared/datasources/obelisk-data-source';
import { Team } from '@shared/model';
import { debounceTime } from 'rxjs/operators';

@Component({
  selector: 'app-teams',
  templateUrl: './teams.component.html',
  styleUrls: ['./teams.component.scss']
})
export class TeamsComponent implements OnInit, OnDestroy {
  teamForm: FormGroup;
  teamSource: ObeliskDataSource<Partial<Team>>;

  private defaultFormValues: any;

  constructor(
    private obelisk: ObeliskService,
    fb: FormBuilder
  ) {
    this.teamForm = fb.group({
      name: ['', Validators.required],
      description: []
    });

    // Set as default form values
    this.defaultFormValues = { ... this.teamForm.value };

    this.teamSource = new ObeliskDataSource(this.obelisk.listTeams.bind(this.obelisk));
  }

  ngOnInit(): void {
    // this.header.setTitle('Teams')
  }

  ngOnDestroy() {
    this.teamSource.cleanUp();
  }

  addTeam(comp: CreateHeaderComponent<any>): void {
    this.obelisk.createTeam(this.teamForm.value).subscribe(resp => {
      if (resp.responseCode === 'SUCCESS') {
        this.teamForm.reset(this.defaultFormValues);
        this.teamSource.invalidate();
        comp.setCollapsed(true);
      }
    })
  }

  removeTeam($event: MouseEvent, id: string): void {

  }

  get dLength(): number {
    return this.teamForm?.get('description')?.value?.length || 0;
  }

}
