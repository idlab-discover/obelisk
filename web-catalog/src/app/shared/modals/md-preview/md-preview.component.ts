import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-md-preview',
  templateUrl: './md-preview.component.html',
  styleUrls: ['./md-preview.component.scss']
})
export class MdPreviewComponent implements OnInit {
  md: string;
  title: string;

  constructor(public activeModal: NgbActiveModal) { }

  ngOnInit(): void {
  }

  init(md: string, title?: string | null) {
    this.md = md;
    this.title = title;
  }

}
