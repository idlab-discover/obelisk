import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ConfirmService, ObeliskService, ToastService } from '@core/services';
import { CreateHeaderComponent } from '@shared/components';
import { NgSelectDataSource } from '@shared/datasources/ng-select-data-source';
import { ObeliskDataSource } from '@shared/datasources/obelisk-data-source';
import { UsageLimit, UsagePlan } from '@shared/model';
import { EMPTY } from 'rxjs';
import { switchMap } from 'rxjs/operators';


@Component({
  selector: 'app-usage-plans',
  templateUrl: './usage-plans.component.html',
  styleUrls: ['./usage-plans.component.scss']
})
export class UsagePlansComponent implements OnInit, OnDestroy {
  usagePlanForm: FormGroup;
  usagePlansSource: ObeliskDataSource<Partial<UsagePlan>>;
  usageLimitSource: NgSelectDataSource<UsageLimit>;

  private defaultFormValues: any;

  constructor(
    private obelisk: ObeliskService,
    private router: Router,
    private confirm: ConfirmService,
    private toast: ToastService,
    fb: FormBuilder
  ) {
    
    this.usagePlanForm = fb.group({
      name: ['', Validators.required],
      description: [],
      maxUsers:  [0, Validators.required],
      userUsageLimitId: [null],
      maxClients:  [0, Validators.required],
      clientUsageLimitId: [null],
    });

    // Set as default form values
    this.defaultFormValues = {... this.usagePlanForm.value};
    this.usagePlansSource = new ObeliskDataSource(this.obelisk.listUsagePlans.bind(this.obelisk));
    this.usageLimitSource = new NgSelectDataSource(this.obelisk.listUsageLimits.bind(this.obelisk));
  }

  ngOnInit(): void {
    // this.header.setTitle('Usage Plans');
  }

  ngOnDestroy() {
    this.usageLimitSource.cleanUp();
    this.usagePlansSource.cleanUp();
  }

  addUsagePlan(comp: CreateHeaderComponent<any>): void {
    this.obelisk.createUsagePlan(this.usagePlanForm.value).subscribe(resp => {
      if (resp.responseCode === 'SUCCESS') {
        this.usagePlanForm.reset(this.defaultFormValues);
        this.usagePlansSource.invalidate();
        comp.setCollapsed(true);
      }
    })
  }
  
  removeUsagePlan(event, usagePlanId) {
    event.preventDefault();
    event.stopPropagation();
    this.confirm.areYouSure('Are you sure you want to delete this Usage Plan?<br><i>It could be in use by other teams and/or users!<br><small>In that case they will revert to default values.</small></i>')
      .pipe(switchMap(ok => ok ? this.obelisk.removeUsagePlan(usagePlanId) : EMPTY))
      .subscribe(res => {
        if (res.responseCode === 'SUCCESS') {
          this.usagePlansSource.invalidate();
          this.toast.success('Usage Plan removed');
        } else {
          this.toast.error('Error removing Usage Plan!');
        }
      });
  }

  goToCreateUsageLimit() {
    this.router.navigate(['admin', 'usagelimit'], {
      fragment: 'create'
    })
  }

  markAsDefault($event: MouseEvent, id:string) {
    $event.preventDefault();
    $event.stopPropagation();
    this.obelisk.markUsagePlanAsDefault(id).subscribe(res => {
      if (res.responseCode === 'SUCCESS') {
        this.usagePlansSource.invalidate();
        this.toast.success('Marked as default');
      } else {
        this.toast.error('Error marking as default!');
      }
    })
  }

  isHovering(el: HTMLElement) {
    return el.matches(':hover');
  }

  get dLength(): number {
    return this.usagePlanForm?.get('description')?.value?.length || 0;
  }
}
