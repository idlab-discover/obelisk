import { Component, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ConfirmService, ResponseHandlerService, ToastService } from '@core/services';
import { ObeliskService } from '@core/services/obelisk.service';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { FullSource, FullSourceOptions, NgSelectDataSource, ObeliskDataSource } from '@shared/datasources';
import { Dataset, PermissionTuple, Permission_ALL, Response, Role, Team, User } from '@shared/model/types';
import { FilterBuilder } from '@shared/utils';
import { asyncScheduler, Observable } from 'rxjs';
import { debounceTime, map } from 'rxjs/operators';


@UntilDestroy()
@Component({
  selector: 'app-access',
  templateUrl: './access.component.html',
  styleUrls: ['./access.component.scss'],
})
export class AccessComponent implements OnInit, OnDestroy {
  dataset: Partial<Dataset>;
  allRoles: FullSource<Role>;
  userSource: ObeliskDataSource<Partial<User>>;
  teamSource: ObeliskDataSource<Partial<Team>>;
  roleSource: NgSelectDataSource<Role>;
  radioForm: FormGroup;
  userForm: FormGroup;
  userFilterForm: FormGroup;
  teamFilterForm: FormGroup;

  allPermissions: PermissionTuple = Permission_ALL;
  mode: 'users' | 'teams' = 'users';
  forms: { [key: string]: FormGroup } = {};

  user: User;
  team: Team;

  constructor(
    private route: ActivatedRoute,
    private obelisk: ObeliskService,
    private confirm: ConfirmService,
    private toast: ToastService,
    private respHandler: ResponseHandlerService,
    fb: FormBuilder
  ) {
    this.radioForm = fb.group({
      view: 'users'
    });
    this.userForm = fb.group({
      roles: [[]]
    });
    this.userFilterForm = fb.group({
      filter: []
    });
    this.teamFilterForm = fb.group({
      filter: []
    });
  }

  ngOnInit() {
    const userFilter = (term) => {
      const fb = FilterBuilder;
      return fb.or(
        fb.regex_i('firstName', term?.trim()),
        fb.regex_i('lastName', term?.trim()),
        fb.regex_i('email', term?.trim()),
        fb.eq('id', term?.trim())
      );
    };
    const teamFilter = (term) => {
      const fb = FilterBuilder;
      return fb.or(
        fb.regex_i('name', term?.trim()),
        fb.eq('id', term?.trim())
      );
    };
    this.userFilterForm.get('filter').valueChanges.pipe(debounceTime(200)).subscribe(f => this.userSource.queryRemote$.next(f));
    this.teamFilterForm.get('filter').valueChanges.pipe(debounceTime(200)).subscribe(f => this.teamSource.queryRemote$.next(f));
    this.radioForm.get('view').valueChanges.subscribe(val => {
      this.mode = val;
      this.deselect();
      this.userFilterForm.get('filter').reset();
      this.teamFilterForm.get('filter').reset();
      this.userSource.resetImmediate();
      this.teamSource.resetImmediate();
    });
    this.route.data.pipe(
      untilDestroyed(this),
      map(data => data.dataset),
    ).subscribe(ds => {
      this.dataset = ds;
      const opt: FullSourceOptions = { sortField: 'name' };
      this.allRoles = new FullSource<Role>(this.obelisk.listDatasetRoles.bind(this.obelisk, ds.id), opt);
      this.roleSource = new NgSelectDataSource(this.obelisk.listDatasetRoles.bind(this.obelisk, ds.id));
      this.userSource = new ObeliskDataSource(this.obelisk.getDatasetMemberRoles.bind(this.obelisk, ds.id), { filterFn: userFilter });
      this.teamSource = new ObeliskDataSource(this.obelisk.getDatasetTeams.bind(this.obelisk, ds.id), { filterFn: teamFilter });

      this.checkFragment();
    });
  }

  ngOnDestroy() {
    this.allRoles.cleanUp();
    this.roleSource.cleanUp();
    this.userSource.cleanUp();
    this.teamSource.cleanUp();
  }

  save() {
    const user = this.user;
    const team = this.team;
    const roleIds = this.userForm.get('roles').value.map(r => r.id);
    let obs: Observable<Response<Dataset>>;
    switch (this.mode) {
      case 'users':
        obs = this.obelisk.setDatasetMemberRoles(this.dataset.id, user.id, roleIds);
        break;
      case 'teams':
        obs = this.obelisk.setDatasetTeamRoles(this.dataset.id, team.id, roleIds);
        break;
    }
    // const user = this.user;
    // const roleIds = this.userForm.get('roles').value.map(r => r.id);
    obs.subscribe(res => this.respHandler.observe(res, {
      success: _ => {
        this.toast.success("Roles saved");
        switch (this.mode) {
          case 'users':
            this.loadUser(user);
            break;
          case 'teams':
            this.loadTeam(team);
            break;
        }
      }
    }));
  }

  remove() {
    let obs: Observable<Response<Dataset>>;
    let item;
    switch (this.mode) {
      case 'users':
        obs = this.obelisk.removeDatasetMember(this.dataset.id, this.user.id);
        item = 'user';
        break;
      case 'teams':
        obs = this.obelisk.removeDatasetTeam(this.dataset.id, this.team.id);
        item = 'team';
        break;
    }
    this.confirm.areYouSureThen(`Do you really want to remove this ${item} from the dataset?`, obs)
      .subscribe(res => this.respHandler.observe(res, {
        success: _ => {
          this.toast.show(`Removed ${item} from dataset`)
          this.deselect();
          this.userSource.invalidate();
          this.teamSource.invalidate();
        }
      }));
  }

  reset() {
    switch (this.mode) {
      case 'users':
        this.loadUser(this.user);
        break;
      case 'teams':
        this.loadTeam(this.team);
        break;
    }
  }

  loadUser(user: User) {
    this.obelisk.getDatasetMember(this.dataset.id, user.id).subscribe(u => {
      this.user = u as User;
      this.userForm.reset({ roles: u?.membership.roles });
    })
  }

  loadTeam(team: Team) {
    this.obelisk.getDatasetTeam(this.dataset.id, team.id).subscribe(t => {
      this.team = t as Team;
      this.userForm.reset({ roles: t?.membership.roles });
    })
  }

  isAssigned(roleId: string) {
    switch (this.mode) {
      case 'users':
        return this.user?.membership?.roles.some(r => r.id == roleId) || false;
      case 'teams':
        return this.team?.membership?.roles.some(r => r.id == roleId) || false;
    }
  }

  deselect() {
    this.team = null;
    this.user = null;
  }

  private checkFragment() {
    const route = this.route;
    route.fragment.subscribe(fragment => {
      if (fragment) {
        this.deselect();
        const id = fragment.slice(1);
        switch (fragment.charAt(0)) {
          case 'u':
            this.radioForm.get('view').setValue('users');
            if (id) {
              this.userFilterForm.get('filter').setValue(id);
              asyncScheduler.schedule(() => {
                this.loadUser(this.userSource.getCurrentItems().find(u => u.id === id) as User);
              }, 300);
            }
            break;
          case 't':
            this.radioForm.get('view').setValue('teams');
            if (id) {
              this.teamFilterForm.get('filter').setValue(id);
              asyncScheduler.schedule(() => {
                this.loadTeam(this.teamSource.getCurrentItems().find(t => t.id === id) as Team);
              }, 300);
            }
            break;
        }
      }
    })
  }
}