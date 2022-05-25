import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { DataStream } from '@shared/model';

@Component({
  selector: 'app-stream-info',
  templateUrl: './stream-info.component.html',
  styleUrls: ['./stream-info.component.scss']
})
export class StreamInfoComponent implements OnInit {
  stream: DataStream;

  constructor(public activeModal: NgbActiveModal) { }

  ngOnInit(): void {
  }

  initFromStream(stream: DataStream) {
    this.stream = stream;
  }

}
