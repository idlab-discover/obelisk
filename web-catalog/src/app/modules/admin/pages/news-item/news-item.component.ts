import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmService, ObeliskService, ToastService } from '@core/services';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { MdPreviewComponent } from '@shared/modals';
import { Announcement } from '@shared/model';
import { map, pluck, switchMap } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-news-item',
  templateUrl: './news-item.component.html',
  styleUrls: ['./news-item.component.scss']
})
export class NewsItemComponent implements OnInit {
  news: Announcement;
  newsForm: FormGroup;

  private defaultValues: any;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private obelisk: ObeliskService,
    private confirm: ConfirmService,
    private toast: ToastService,
    private modals: NgbModal,
    fb: FormBuilder
  ) {
    this.newsForm = fb.group({
      title: ['', Validators.required],
      content: ['', Validators.required],
    });
  }

  ngOnInit(): void {
    this.route.paramMap.pipe(
      untilDestroyed(this),
      map(params => params.get('newsId'))
    ).subscribe(id => this.loadData(id))
  }

  private loadData(id: string) {
    this.obelisk.getAnnouncement(id)
      .subscribe(news => {
        this.news = news;
        this.newsForm.reset(news);
        this.defaultValues = { ... this.newsForm.value };
      });
  }

  preview() {
    const ref = this.modals.open(MdPreviewComponent, { size: 'lg' });
    ref.componentInstance.init(this.newsForm.get('content').value, this.newsForm.get('title').value);
  }

  save() {
    if (this.newsForm.valid) {
      this.obelisk.updateAnnouncement(this.news?.id, this.newsForm.value).subscribe(res => {
        if (res.responseCode == 'SUCCESS') {
          this.toast.show('News item saved');
          this.loadData(this.news?.id);
        } else {
          this.toast.error('Error while saving news item!');
        }
      })
    }

  }

  remove() {
    return this.confirm.areYouSureThen('Do you really want to remove this news item?', this.obelisk.removeAnnouncement(this.news?.id))
      .subscribe(res => {
        if (res.responseCode == 'SUCCESS') {
          this.toast.show('News item removed');
          this.router.navigate(['..'], { relativeTo: this.route });
        } else {
          this.toast.error('Error while removing news item!');
        }
      })
  }

  reset() {
    this.newsForm.reset(this.defaultValues);
  }

}
