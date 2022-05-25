import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { Dataset } from '@shared/model';

@UntilDestroy()
@Component({
  selector: 'app-readme',
  templateUrl: './readme.component.html',
  styleUrls: ['./readme.component.scss']
})
export class ReadmeComponent implements OnInit {
  dataset: Partial<Dataset>;

  constructor(private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.route.data
      .pipe(untilDestroyed(this))
      .subscribe(data => this.dataset = data.dataset);
  }

}
