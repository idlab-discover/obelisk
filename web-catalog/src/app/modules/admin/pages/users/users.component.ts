import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ConfirmService, ObeliskService, ResponseHandlerService, ToastService } from '@core/services';
import { ObeliskDataSource } from '@shared/datasources/obelisk-data-source';
import { User } from '@shared/model/types';
import { FilterBuilder } from '@shared/utils';
import { EMPTY } from 'rxjs';
import { debounceTime, switchMap } from 'rxjs/operators';

@Component({
  selector: 'app-users',
  templateUrl: './users.component.html',
  styleUrls: ['./users.component.scss']
})
export class UsersComponent implements OnInit, OnDestroy {
  searchForm: FormGroup;
  userSource: ObeliskDataSource<Partial<User>>;
  me: User;

  constructor(
    private obelisk: ObeliskService,
    private confirm: ConfirmService,
    private respHandler: ResponseHandlerService,
    private toast: ToastService,
    fb: FormBuilder
  ) {
    const filter = (term) => {
      const fb = FilterBuilder
      return fb.or(
        fb.regex_i('firstName', term.trim()),
        fb.regex_i('lastName', term.trim()),
        fb.regex_i('email', term.trim()),
      )
    };

    this.searchForm = fb.group({
      search: []
    });
    this.userSource = new ObeliskDataSource(this.obelisk.listAllUsers.bind(this.obelisk), { filterFn: filter });
    this.searchForm.get('search').valueChanges.pipe(debounceTime(200)).subscribe(term => this.userSource.queryRemote$.next(term));

  }

  ngOnInit(): void {
    this.obelisk.getProfile().subscribe(me => this.me = me);
  }

  ngOnDestroy() {
    this.userSource.cleanUp();
  }

  stopPropagation(event: MouseEvent) {
    event.stopPropagation();
  }

  toggleAdmin(user: User) {
    const newState: boolean = user.platformManager;
    const obs = newState ? this.obelisk.setUserAsPlatformManger(user.id, true) : this.confirm.areYouSure("Demote this admin to a normal user?", { yesLabel: 'Ok' })
      .pipe(switchMap(ok => {
        if (ok) {
          return this.obelisk.setUserAsPlatformManger(user.id, false);
        } else {
          user.platformManager = true;
          return EMPTY;
        }
      }));
    obs.subscribe(res => this.respHandler.observe(res, {
      success: _ => {
        this.toast.show('Saved')
        this.userSource.invalidate();
      },
      badRequest: _ => {
        this.toast.error('Error toggling saving admin status');
        this.userSource.invalidate();
      },
      error: _ => {
        this.toast.error('Error toggling saving admin status');
        this.userSource.invalidate();
      }
    }
    ));

  }

  isMeOrAdminAccount(user: User) {
    return (user.id == this.me?.id) || user.id == '0';
  }

}
