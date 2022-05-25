import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ObeliskService, ToastService } from '@core/services';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { NgSelectDataSource } from '@shared/datasources';
import { InfoUsageLimitsModalComponent } from '@shared/modals';
import { UsageLimit, User } from '@shared/model';
import { FilterBuilder } from '@shared/utils';
import { asapScheduler, Observable } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';

@Component({
  selector: 'app-user',
  templateUrl: './user.component.html',
  styleUrls: ['./user.component.scss']
})
export class UserComponent implements OnInit {
  user: Partial<User>;

  usageLimitSource: NgSelectDataSource<UsageLimit>;
  userForm: FormGroup;

  private originalUsageLimitId: string;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private obelisk: ObeliskService,
    private toast: ToastService,
    private modal: NgbModal,
    fb: FormBuilder
  ) {
    this.userForm = fb.group({
      usageLimitId: []
    });
    this.route.paramMap.pipe(
      map(p => p.get('userId')),
      switchMap(id => this.obelisk.getUserAsAdmin(id))
    ).subscribe(user => this.loadData(user));
  }

  ngOnInit(): void {
  }

  goToUsageLimits() {
    this.router.navigate(['admin', 'usagelimit']);
  }

  save() {
    if (this.userForm.valid) {
      this.obelisk.setUserUsageLimit(this.user.id, this.userForm.get('usageLimitId').value?.id || null).pipe(
        switchMap(_ => this.obelisk.getUserAsAdmin(this.user.id)),
      ).subscribe({
        next: user => {
          this.toast.show('Usage limit saved');
          this.loadData(user);
        },
        error: _ => this.toast.error('Saving usage limit failed!')
      });
    }
  }

  isSaveDisabled() {
    return this.userForm.get('usageLimitId').value?.id == this.originalUsageLimitId;
  }

  viewSelectedUsageLimit() {
    const ulId = this.userForm.get('usageLimitId').value;
    let obs: Observable<UsageLimit>;
    if (ulId != null) {
      obs = this.obelisk.getUsageLimit(ulId.id);
    }
    else {
      obs = this.obelisk.listUsageLimits({ filter: FilterBuilder.eq('defaultLimit', true) }).pipe(
        map(page => page.items[0]),
        switchMap(item => this.obelisk.getUsageLimit(item.id))
      );
    }
    obs.subscribe(info => {
      const ref = this.modal.open(InfoUsageLimitsModalComponent);
      ref.componentInstance.info = info;
    });
  }

  private loadData(user: User) {
    this.user = user;
    this.originalUsageLimitId = user.usageLimitAssigned ? user.usageLimit?.id : null;
    this.usageLimitSource = new NgSelectDataSource(this.obelisk.listUsageLimits.bind(this.obelisk));
    asapScheduler.schedule(() => {
      this.userForm.setValue({
        usageLimitId: user?.usageLimitAssigned ? { id: user.usageLimit?.id } : null
      });
    }, 200);
  }
}
