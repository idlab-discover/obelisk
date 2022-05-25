import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ObeliskService } from '@core/services';
import { faSave } from '@fortawesome/free-solid-svg-icons';
import { NgSelectDataSource } from '@shared/datasources/ng-select-data-source';
import { Team, UsagePlan } from '@shared/model';
import { map, switchMap, tap, zip } from 'rxjs/operators';

@Component({
  selector: 'app-team',
  templateUrl: './team.component.html',
  styleUrls: ['./team.component.scss']
})
export class TeamComponent implements OnInit {
  team: Partial<Team>;
  editable: boolean = false;
  editForm: FormGroup;

  usagePlanSource: NgSelectDataSource<UsagePlan>;

  private defaultFormValues: any;

  constructor(
    private route: ActivatedRoute,
    private obelisk: ObeliskService,
    private router: Router,
    private fb: FormBuilder
  ) {
    this.editForm = fb.group({
      id: [null, Validators.required],
      name: [null, Validators.required],
      description: [],
      usagePlanId: [null]
    });
    this.usagePlanSource = new NgSelectDataSource(this.obelisk.listUsagePlans.bind(this.obelisk));
  }

  ngOnInit(): void {


    this.route.paramMap.pipe(
      map(p => p.get('teamId')),
      switchMap(id => this.obelisk.getTeam(id)),
    ).subscribe(team => {
      this.team = this.toFormModel(team);
      this.editForm.setValue(this.team);
    });
  }

  private toFormModel(team: Partial<Team>): any {
    return {
      id: team.id,
      name: team.name,
      description: team.description,
      usagePlanId: team.usagePlan,
    }
  }

  goToCreateUsagePlan() {
    this.router.navigate(['admin', 'usageplan'], {
      fragment: 'create'
    })
  }

  reset(): void {
    this.editForm.reset(this.team);
  }

  save(): void {

  }

}
