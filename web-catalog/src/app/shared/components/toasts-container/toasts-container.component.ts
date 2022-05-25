import { Component, TemplateRef } from '@angular/core';
import { ToastService } from '@core/services';
import { NgbToast } from '@ng-bootstrap/ng-bootstrap';


@Component({
  selector: 'app-toasts',
  templateUrl: './toasts-container.component.html',
  styleUrls: ['./toasts-container.component.scss']
})
export class ToastsContainerComponent {

  constructor(public toastService: ToastService) { }

  isTemplate(toast) {
    return toast.textOrTpl instanceof TemplateRef;
  }

  close(t: NgbToast) {
    t.hide().subscribe();
  }

}
