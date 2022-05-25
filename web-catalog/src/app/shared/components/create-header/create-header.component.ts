import { Component, Input, OnChanges, OnInit, Output, SimpleChanges, TemplateRef, ViewEncapsulation } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { ObeliskDataSource } from '@shared/datasources/obelisk-data-source';
import { EventEmitter } from 'events';

type DisplayType = 'header' | 'desc';

@UntilDestroy()
@Component({
  selector: 'app-create-header',
  templateUrl: './create-header.component.html',
  styleUrls: ['./create-header.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class CreateHeaderComponent<T> implements OnInit, OnChanges {
  @Input() collapsed: boolean = true;
  @Input('header') title: string = null;
  @Input() createTitle: string;
  @Input() filterSource: ObeliskDataSource<T> = null;
  @Input() filterProperties: string[];
  @Input() help: string;
  @Input() template: TemplateRef<any>;
  @Input() open: boolean = false;
  @Input() label: string = "New";
  @Input() icon: string = "plus";
  @Input() btnStyle: string = "success";
  @Input() filterMode: 'local'|'remote' = 'remote';

  @Output() search: EventEmitter = new EventEmitter();


  searchForm: FormGroup;

  constructor(fb: FormBuilder) {
    this.searchForm = fb.group({
      search: []
    });
  }

  ngOnInit(): void {
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.filterSource?.currentValue != null) {
      this.searchForm.get('search').valueChanges
      .pipe(untilDestroyed(this))
      .subscribe(term => this.filterMode === 'remote' ? this.filterSource.queryRemote$.next(term) : this.filterSource.queryLocal$.next(term));
    }
  }

  setCollapsed(collapse: boolean) {
    this.collapsed = collapse;
  }

  resetFilter() {
    this.searchForm.get('search').reset();
  }

  get mainBtnStyle() {
    return {
      [`btn-${this.btnStyle}`]: this.collapsed,
      [`btn-outline-${this.btnStyle}`]: !this.collapsed
    };
  }

}
