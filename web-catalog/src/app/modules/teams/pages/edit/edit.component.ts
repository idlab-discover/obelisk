import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmService, ObeliskService, ResponseHandlerService, RoleService, ToastService } from '@core/services';
import { NgSelectDataSource } from '@shared/datasources/ng-select-data-source';
import { Team, UsagePlan } from '@shared/model';
import { asapScheduler, asyncScheduler, concat } from 'rxjs';
import { AsyncScheduler } from 'rxjs/internal/scheduler/AsyncScheduler';
import { toArray } from 'rxjs/operators';


@Component({
  selector: 'app-edit',
  templateUrl: './edit.component.html',
  styleUrls: ['./edit.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class EditComponent implements OnInit {
  team: Partial<Team>;
  generalForm: FormGroup;
  rateLimitForm: FormGroup;

  collapseGeneral = true;
  collapseRateLimit = true;

  usagePlanSource: NgSelectDataSource<UsagePlan>;
  up: UsagePlan;

  isAdmin: boolean;

  private defaultFormValues: any;

  constructor(
    private route: ActivatedRoute,
    private obelisk: ObeliskService,
    private confirm: ConfirmService,
    private router: Router,
    private toast: ToastService,
    private role: RoleService,
    private respHandler: ResponseHandlerService,
    fb: FormBuilder
  ) {
    this.generalForm = fb.group({
      id: [null, Validators.required],
      name: [null, Validators.required],
      description: [],
    });
    this.rateLimitForm = fb.group({
      usagePlanId: [],
    });
  }

  ngOnInit(): void {
    this.route.data.subscribe(data => {
      this.loadData(data.team);
    });
  }

  private loadData(team: Team) {
    this.team = this.toFormModel(team);
    this.generalForm.setValue(this.team);
    this.role.isAdmin$().subscribe(isAdmin => {
      this.isAdmin = isAdmin;
      if (isAdmin) {
        this.usagePlanSource = new NgSelectDataSource(this.obelisk.listUsagePlans.bind(this.obelisk));
        asapScheduler.schedule(() => {
          this.rateLimitForm.setValue({
            usagePlanId: team.usagePlanAssigned ? { id: team.usagePlan?.id } : null
          });
        }, 200);
      }
    });
  }

  private toFormModel(team: Partial<Team>): any {
    return {
      id: team.id,
      name: team.name,
      description: team.description,
    }
  }

  removeTeam() {
    this.confirm.areYouSureThen(
      'Are you sure you want to remove this Team?<br><b>This action is irreversiable!</b>',
      this.obelisk.removeTeam(this.team.id),
      { noLabel: 'Cancel' }
    ).subscribe(res => this.respHandler.observe(res, {
      success: _ => {
        this.toast.show('Team removed');
        this.router.navigate(['my', 'teams']);
      }
    }));
  }

  goToCreateUsagePlan() {
    this.router.navigate(['admin', 'usageplan'], {
      fragment: 'create'
    })
  }

  reset(): void {
    this.generalForm.reset(this.team);
  }

  saveGeneral(): void {
    const id = this.generalForm.get('id').value;
    const name = this.generalForm.get('name').value;
    const description = this.generalForm.get('description').value;
    concat(
      this.obelisk.setTeamName(id, name),
      this.obelisk.setTeamDescription(id, description)
    )
      .pipe(toArray())
      .subscribe(responses => {
        if (responses.every(r => 'SUCCESS' === r.responseCode)) {
          this.toast.success('Team details saved');
          this.refresh();
        } else {
          this.toast.error('Error while saving team details!')
        }
      })
  }

  saveRateLimit() {
    const usagePlanId = this.rateLimitForm.get('usagePlanId').value;
    this.obelisk.setTeamUsagePlan(this.team.id, usagePlanId)
      .subscribe(res => this.respHandler.observe(res, {
        success: _ => {
          this.toast.success('Saved usage plan')
          this.refresh();
        }
      }));
  }

  private refresh() {
    this.obelisk.getTeam(this.team.id, true).subscribe(team => this.loadData(team));
  }



}
