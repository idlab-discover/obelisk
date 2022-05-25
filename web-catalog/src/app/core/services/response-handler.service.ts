import { TitleCasePipe } from '@angular/common';
import { Injectable } from '@angular/core';
import { Response, ResponseHandler } from '@shared/model';
import { ToastService } from './toast.service';

@Injectable({
  providedIn: 'root'
})
export class ResponseHandlerService {
  private defaultErrorHandler = (response) => {
    const header = this.ucFirst.transform(response.responseCode).replace('_', ' ');
    this.toast.error({ header: header, body: response.message });
  }
  private defaultBadReqeustHandler = (response) => {
    const header = this.ucFirst.transform(response.responseCode).replace('_', ' ');
    this.toast.warn(response?.message || 'Bad request');
  }

  constructor(
    private toast: ToastService,
    private ucFirst: TitleCasePipe
  ) {

  }

  observe<T>(response: Response<T>, handler: ResponseHandler<T>) {
    const observer: ResponseHandler<T> = {
      success: handler.success,
      badRequest: handler.badRequest ?? this.defaultBadReqeustHandler,
      error: handler.error ?? this.defaultErrorHandler,
    }

    switch (response.responseCode) {
      case 'SUCCESS':
        observer.success(response?.item);
        break;
      case 'BAD_REQUEST':
        observer.badRequest(response);
        break;
      default:
        observer.error(response);
    }
  }

}
