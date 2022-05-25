import { Component, OnInit, Input, ElementRef } from '@angular/core';

@Component({
  selector: 'app-spinner',
  templateUrl: './spinner.component.html',
  styleUrls: ['./spinner.component.scss']
})
export class SpinnerComponent implements OnInit {
  @Input('height') height: number = 64;
  public top: string;

  constructor(private hostEl: ElementRef<HTMLElement>) { }

  ngOnInit() {
    this.top = ((this.height-11) / 2)+'px';
    this.hostEl.nativeElement.style.height = this.height+'px';
  }

}
