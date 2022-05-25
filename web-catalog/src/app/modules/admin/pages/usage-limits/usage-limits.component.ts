import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ConfirmService, ObeliskService, ToastService } from '@core/services';
import { CreateHeaderComponent } from '@shared/components';
import { ObeliskDataSource } from '@shared/datasources/obelisk-data-source';
import { UsageLimit } from '@shared/model';

import { EMPTY } from 'rxjs';
import { switchMap } from 'rxjs/operators';

@Component({
  selector: 'app-usage-limits',
  templateUrl: './usage-limits.component.html',
  styleUrls: ['./usage-limits.component.scss']
})
export class UsageLimitsComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild(CreateHeaderComponent) header;
  usageLimitForm: FormGroup;
  usageLimitsSource: ObeliskDataSource<UsageLimit>;

  private defaultFormValues = {
    name: '',
    description: '',
    maxHourlyComplexEventQueries: 0,
    maxHourlyComplexEventsStored: 0,
    maxHourlyComplexEventsStreamed: 0,
    maxHourlyComplexStatsQueries: 0,
    maxHourlyPrimitiveEventQueries: 0,
    maxHourlyPrimitiveEventsStored: 0,
    maxHourlyPrimitiveEventsStreamed: 0,
    maxHourlyPrimitiveStatsQueries: 0,
    maxDataExportRecords: 0,
    maxDataExports: 0,
    maxDataStreams: 0,
  }

  constructor(
    private route: ActivatedRoute,
    private confirm: ConfirmService,
    private toast: ToastService,
    private obelisk: ObeliskService,
    fb: FormBuilder
  ) {
    
    this.usageLimitForm = fb.group({
      name: ['', Validators.required],
      description: [''],
      maxHourlyComplexEventQueries: [null, Validators.required],
      maxHourlyComplexEventsStored: [null, Validators.required],
      maxHourlyComplexEventsStreamed: [null, Validators.required],
      maxHourlyComplexStatsQueries: [null, Validators.required],
      maxHourlyPrimitiveEventQueries: [null, Validators.required],
      maxHourlyPrimitiveEventsStored: [null, Validators.required],
      maxHourlyPrimitiveEventsStreamed: [null, Validators.required],
      maxHourlyPrimitiveStatsQueries: [null, Validators.required],
      maxDataExportRecords: [null, Validators.required],
      maxDataExports: [null, Validators.required],
      maxDataStreams: [null, Validators.required],
    });
    // set default:
    this.usageLimitForm.setValue(this.defaultFormValues);
    this.usageLimitsSource = new ObeliskDataSource(this.obelisk.listUsageLimits.bind(this.obelisk));
  }

  ngOnInit(): void {
  }

  ngOnDestroy() {
    this.usageLimitsSource.cleanUp();
  }

  ngAfterViewInit(): void {
    if ('create' === this.route.snapshot.fragment) {
      setTimeout(() => this.header.setCollapsed(false), 750);
    }
  }

  addUsageLimit(): void {
    const obj = {...this.usageLimitForm.value};
    const name = obj.name;
    const description = obj.description;
    delete obj.name;
    delete obj.description;
    const values = {...obj};
    const input = {name, description, values};
    this.obelisk.createUsageLimit(input).subscribe(resp => {
      if (resp.responseCode === 'SUCCESS') {
        this.usageLimitForm.reset(this.defaultFormValues);
        this.usageLimitsSource.invalidate();
        this.header.setCollapsed(true);
      }
    })
  }

  removeUsageLimit(event, usageLimitId) {
    event.preventDefault();
    event.stopPropagation();
    this.confirm.areYouSure('Are you sure you want to delete this Usage Limit?<br><i>It could be in use by other Usage Plans and/or user!<br><small>In that case they will revert to default values.</small></i>')
      .pipe(switchMap(ok => ok ? this.obelisk.removeUsageLimit(usageLimitId) : EMPTY))
      .subscribe(res => {
        if (res.responseCode === 'SUCCESS') {
          this.usageLimitsSource.invalidate();
          this.toast.success('Usage Limit removed');
        } else {
          this.toast.error('Error removing Usage Limit!');
        }
      });
  }

  markAsDefault($event: MouseEvent, id: string) {
    $event.preventDefault();
    $event.stopPropagation();
    this.obelisk.markUsageLimitAsDefault(id).subscribe(res => {
      if (res.responseCode === 'SUCCESS') {
        this.usageLimitsSource.invalidate();
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
    return this.usageLimitForm?.get('description')?.value?.length || 0;
  }
}
