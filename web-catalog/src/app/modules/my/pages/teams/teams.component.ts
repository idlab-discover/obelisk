import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HeaderService, ObeliskService, ResponseHandlerService, ToastService } from '@core/services';
import { CreateHeaderComponent } from '@shared/components';
import { ObeliskDataSource } from '@shared/datasources/obelisk-data-source';
import { Team } from '@shared/model';

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
    private header: HeaderService,
    private toast: ToastService,
    private respHandler: ResponseHandlerService,
    fb: FormBuilder
  ) {
    this.teamForm = fb.group({
      name: ['', Validators.required],
      description: []
    });

    // Set as default form values
    this.defaultFormValues = { ... this.teamForm.value };

    this.teamSource = new ObeliskDataSource(this.obelisk.listMyTeams.bind(this.obelisk));
  }

  ngOnInit(): void {
    this.header.setTitle('My Teams');
  }

  ngOnDestroy() {
    this.teamSource.cleanUp();
  }

  addTeam(comp: CreateHeaderComponent<any>): void {
    this.obelisk.createTeam(this.teamForm.value).subscribe(resp =>
      this.respHandler.observe(resp, {
        success: _ => {
          this.toast.show('Team created');
          this.teamForm.reset(this.defaultFormValues);
          this.teamSource.invalidate();
          comp.setCollapsed(true);
        }
      })
    );
  }

  get dLength(): number {
    return this.teamForm?.get('description')?.value?.length || 0;
  }


}
