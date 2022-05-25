import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ObeliskService } from '@core/services';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { ObeliskDataSource } from '@shared/datasources';
import { Dataset, Team, TeamTiles } from '@shared/model';
import { map, switchMap } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {
  team: Team;
  tiles: TeamTiles;
  datasetSource: ObeliskDataSource<Dataset>;

  constructor(
    private route: ActivatedRoute,
    private obelisk: ObeliskService
  ) { }

  ngOnInit(): void {
    this.route.data.pipe(
      untilDestroyed(this),
      map(data => data.team),
      switchMap(team => this.obelisk.getTeamTiles(team.id).pipe(map(tiles => [team, tiles])))
      )
      .subscribe(([team, tiles]) => {
        this.team = team;
        this.tiles = tiles;
        this.datasetSource = new ObeliskDataSource(this.obelisk.listTeamDatasets.bind(this.obelisk, team.id));
      });
  }

}
