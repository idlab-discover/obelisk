import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HeaderService, ObeliskService, ToastService } from '@core/services';
import { UsageLimit } from '@shared/model';
import { map, switchMap } from 'rxjs/operators';


@Component({
  selector: 'app-usage-limit',
  templateUrl: './usage-limit.component.html',
  styleUrls: ['./usage-limit.component.scss']
})
export class UsageLimitComponent implements OnInit {
  private usageLimit: any;
  ulForm: FormGroup;

  constructor(
    private header: HeaderService,
    private route: ActivatedRoute,
    private obelisk: ObeliskService,
    private toast: ToastService,
    fb: FormBuilder
  ) {
    this.ulForm = fb.group({
      id: [null, Validators.required],
      name: [null, Validators.required],
      description: [],
      defaultLimit: [],
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
    })
  }

  ngOnInit(): void {
    // this.header.setTitle('Usage Limit details')
    this.route.paramMap.pipe(
      map(params => params.get('usageLimitId')),
      switchMap(id => this.obelisk.getUsageLimit(id))
    ).subscribe(ul => {
      this.usageLimit = this.toFormModel(ul)
      this.ulForm.setValue(this.usageLimit);
    });
  }

  reset() {
    this.ulForm.setValue(this.usageLimit);
  }

  save() {
    const obj = {...this.ulForm.value};
    const id = obj.id;
    const name = obj.name;
    const description = obj.description;
    delete obj.id;
    delete obj.name;
    delete obj.description;
    delete obj.defaultLimit;
    const values = {...obj};
    const input = {name, description, values};
    this.obelisk.editUsageLimit(id, input).subscribe(resp => {
      if (resp.responseCode === 'SUCCESS') {
        this.toast.success('Usage Limit changes saved');
        this.usageLimit = this.ulForm.value;
        this.reset();
      } else {
        this.toast.error('Something went wrong saving Usage Limit changes!');
      }
    });
  }

  get dLength(): number {
    return this.ulForm?.get('description')?.value?.length || 0;
  }

  private toFormModel(ul: UsageLimit) {
    return {
      id: ul.id,
      name: ul.name,
      description: ul.description,
      defaultLimit: ul.defaultLimit,
      ...ul.values
    }
  }

}
