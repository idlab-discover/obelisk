import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ObeliskService, ResponseHandlerService, ToastService } from '@core/services';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CreateHeaderComponent } from '@shared/components';
import { ObeliskDataSource } from '@shared/datasources';
import { MdPreviewComponent } from '@shared/modals';
import { Announcement } from '@shared/model';
import { FilterBuilder } from '@shared/utils';

@Component({
  selector: 'app-news',
  templateUrl: './news.component.html',
  styleUrls: ['./news.component.scss']
})
export class NewsComponent implements OnInit, OnDestroy {
  newsForm: FormGroup;
  newsSource: ObeliskDataSource<Announcement>;

  constructor(
    private obelisk: ObeliskService,
    private toast: ToastService,
    private modals: NgbModal,
    private respHandler: ResponseHandlerService,
    fb: FormBuilder
  ) {
    this.newsForm = fb.group({
      title: ['', Validators.required],
      content: ['', Validators.required]
    });
  }

  ngOnInit(): void {
    const filter = term => {
      return FilterBuilder.or(
        FilterBuilder.regex_i('title', term.trim()),
        FilterBuilder.regex_i('content', term.trim()),
      );
    }
    this.newsSource = new ObeliskDataSource(this.obelisk.listAnnouncements.bind(this.obelisk), { filterFn: filter });
  }
  
  ngOnDestroy() {
    this.newsSource.cleanUp();
  }

  reset() {
    this.newsForm.reset();
  }

  preview() {
    const ref = this.modals.open(MdPreviewComponent, { size: 'lg' });
    ref.componentInstance.init(this.newsForm.get('content').value, this.newsForm.get('title').value);
  }


  add(comp: CreateHeaderComponent<Announcement>) {
    if (this.newsForm.valid) {
      this.obelisk.createAnnouncement(this.newsForm.value)
        .subscribe(res => this.respHandler.observe(res, {
          success: _ => {
            this.toast.show('News item created');
            this.newsSource.invalidate();
            comp.setCollapsed(true);
          }
        }));
    }
  }
}
