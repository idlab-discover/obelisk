import { ArrayDataSource } from '@angular/cdk/collections';
import { AfterViewInit, Component, ElementRef, Input, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { fadeSlideIn } from "@core/animations";
import { CarouselDataSource } from '@shared/datasources/carousel-data-source';
import { ID } from '@shared/model';
import { tap } from 'rxjs/operators';

@Component({
  selector: 'app-carousel',
  templateUrl: './carousel.component.html',
  styleUrls: ['./carousel.component.scss'],
  encapsulation: ViewEncapsulation.None,
  animations: [
    fadeSlideIn
  ]
})
export class CarouselComponent<T extends ID> implements OnInit, AfterViewInit {
  @Input('carouselHeight') carouselHeight: number;
  @Input('datasource') datasource: CarouselDataSource<T>;
  @Input('rows') rows: number = 1;
  @Input('cols') cols: number = 4;
  @ViewChild('window') window: ElementRef<HTMLDivElement>;
  private _isFetching = false;

  get isFetching(): boolean {
    return this._isFetching;
  }
  
  constructor() { }

  ngOnInit(): void {
    this.datasource.fetching$.subscribe(isFetching => this._isFetching = isFetching);
  }

  ngAfterViewInit(): void {
    const s = this.window.nativeElement.style;
    s.gridTemplateColumns = `repeat(${this.cols}, minmax(200px, 300px)`;
    s.gridTemplateRows = `repeat(${this.rows}, auto)`;
  }

  next() {
    if (this.datasource.hasNextPage()) {
      this.datasource.getNextPage();
    }
  }

  previous() {
    if (this.datasource.hasPreviousPage()) {
      this.datasource.getPreviousPage();
    }
  }

}
