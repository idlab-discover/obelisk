import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ObeliskService, ToastService } from '@core/services';
import { NgSelectDataSource } from '@shared/datasources/ng-select-data-source';
import { UsageLimit, UsagePlan, UsagePlanInput } from '@shared/model';
import { FilterBuilder } from '@shared/utils';

import { map, switchMap } from 'rxjs/operators';

@Component({
  selector: 'app-usage-plan',
  templateUrl: './usage-plan.component.html',
  styleUrls: ['./usage-plan.component.scss']
})
export class UsagePlanComponent implements OnInit {
  private usagePlan: UsagePlanFormModel;
  upForm: FormGroup;
  usageLimitSource: NgSelectDataSource<UsageLimit>;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private obelisk: ObeliskService,
    private toast: ToastService,
    fb: FormBuilder
  ) {
    this.upForm = fb.group({
      id: [null, Validators.required],
      name: [null, Validators.required],
      defaultPlan: [],
      description: [],
      maxUsers: [null, Validators.required],
      userUsageLimitId: [null],
      maxClients: [null, Validators.required],
      clientUsageLimitId: [null],
    })

    this.usageLimitSource = new NgSelectDataSource(this.obelisk.listUsageLimits.bind(this.obelisk),{filterFn: term => FilterBuilder.regex_i('name',  term)});
  }

  ngOnInit(): void {
    // this.header.setTitle('Usage Plan details')
    this.route.paramMap.pipe(
      map(params => params.get('usagePlanId')),
      switchMap(id => this.obelisk.getUsagePlan(id))
    ).subscribe(up => {
      this.usagePlan = this.toFormModel(up);
      this.upForm.setValue(this.usagePlan);
    });
  }

  reset() {
    this.upForm.setValue(this.usagePlan);
  }

  save() {
    const { id, ...input } = this.upForm.value;
    delete input.defaultPlan;
    this.obelisk.editUsagePlan(id, input).subscribe(resp => {
      if (resp.responseCode === 'SUCCESS') {
        this.toast.success('Usage Plan changes saved');
        this.usagePlan = this.upForm.value;
        this.reset();
      } else {
        this.toast.error('Error saving Usage Plan!');
      }
    });
  }

  goToCreateUsageLimit() {
    this.router.navigate(['admin', 'usagelimit'], {
      fragment: 'create'
    })
  }

  private toFormModel(up: UsagePlan): UsagePlanFormModel {
    return {
      id: up.id,
      name: up.name,
      description: up.description,
      defaultPlan: up.defaultPlan,
      maxUsers: up.maxUsers,
      maxClients: up.maxClients,
      userUsageLimitId: up.userUsageLimitAssigned ? up.userUsageLimit?.id : null,
      clientUsageLimitId: up.clientUsageLimitAssigned ? up.clientUsageLimit?.id : null
    }
  }

  get dLength(): number {
    return this.upForm?.get('description')?.value?.length || 0;
  }
}

interface UsagePlanFormModel extends UsagePlanInput {
  id: string;
  defaultPlan: boolean;
}
