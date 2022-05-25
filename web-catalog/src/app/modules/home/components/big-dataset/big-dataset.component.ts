import { Component, OnInit, Input } from '@angular/core';
import { Dataset } from '@shared/model/types';

@Component({
  selector: 'app-big-dataset',
  templateUrl: './big-dataset.component.html',
  styleUrls: ['./big-dataset.component.scss']
})
export class BigDatasetComponent implements OnInit {
  @Input() dataset: Dataset;

  constructor() { }

  ngOnInit(): void {
  }

}
