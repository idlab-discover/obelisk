import { Injectable, TemplateRef } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class ToastService {
  toasts: ToastOrTemplate[] = [];

  private readonly SUCCESS_STYLE = 'bg-success text-light';
  private readonly INFO_STYLE = 'bg-info text-light';
  private readonly WARNING_STYLE = 'bg-warning text-dark';
  private readonly DANGER_STYLE = 'bg-danger text-light';

  constructor() { }

  show(textOrTplOrToast: string | TemplateRef<any> | Toast, options: ToastOptions = {}) {
    let toastOrTpl;
    if (typeof textOrTplOrToast === 'string') {
      toastOrTpl = { body: textOrTplOrToast }
    } else {
      toastOrTpl = textOrTplOrToast as (Toast | TemplateRef<any>);
    }
    this.toasts.push({ toastOrTpl, ...options });
  }

  success(textOrTplOrToast: string | TemplateRef<any> | Toast) {
    this.show(textOrTplOrToast, { classname: this.SUCCESS_STYLE });
  }

  info(textOrTplOrToast: string | TemplateRef<any> | Toast) {
    this.show(textOrTplOrToast, { classname: this.INFO_STYLE });
  }

  warn(textOrTplOrToast: string | TemplateRef<any> | Toast) {
    this.show(textOrTplOrToast, { classname: this.WARNING_STYLE });
  }

  error(textOrTplOrToast: string | TemplateRef<any> | Toast) {
    this.show(textOrTplOrToast, { classname: this.DANGER_STYLE });
  }

  remove(toast) {
    this.toasts = this.toasts.filter(t => t !== toast);
  }

}

export interface ToastOrTemplate extends ToastOptions {
  toastOrTpl: Toast | TemplateRef<any>;
}

export interface Toast {
  body: string;
  header?: string;
}

export interface ToastOptions {
  classname?: string;
  delay?: number;

}