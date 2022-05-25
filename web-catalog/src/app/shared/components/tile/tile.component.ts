import { AfterViewInit, Component, ElementRef, Input, OnInit, ViewChild } from '@angular/core';

@Component({
  selector: 'app-tile, tile',
  templateUrl: './tile.component.html',
  styleUrls: ['./tile.component.scss']
})
export class TileComponent implements OnInit, AfterViewInit {
  @Input() nr: string|number;
  @Input() caption: string;
  @Input() color: string = "#085f88";

  @ViewChild("tile") tile: ElementRef<HTMLDivElement>;

  constructor() { }

  ngOnInit(): void {
  }

  ngAfterViewInit() {
    this.tile.nativeElement.style.backgroundColor = this.color;
  }

}
