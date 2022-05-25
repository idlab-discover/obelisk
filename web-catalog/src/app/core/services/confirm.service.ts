import { Injectable } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AreYouSureComponent, AreYouSureOptions } from '@shared/modals/are-you-sure/are-you-sure.component';
import { PromptComponent, PromptOptions } from '@shared/modals/prompt/prompt.component';
import { EMPTY, from, Observable } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class ConfirmService {

  constructor(private modal: NgbModal) { }

  areYouSure(prompt?: string, options?: AreYouSureOptions): Observable<boolean> {
    const ref = this.modal.open(AreYouSureComponent);
    const comp = (ref.componentInstance as AreYouSureComponent);
    comp.init(prompt, options);

    return from(ref.result).pipe(catchError(err => EMPTY));
  }

  areYouSureThen<T>(prompt: string, obs: Observable<T>, options?: AreYouSureOptions): Observable<T> {
    return this.areYouSure(prompt, options).pipe(
      switchMap(ok => ok ? obs : EMPTY)
    );
  }

  prompt(prompt?: string, options?: PromptOptions): Observable<boolean> {
    const ref = this.modal.open(PromptComponent);
    const comp = (ref.componentInstance as PromptComponent);
    comp.init(prompt, options);

    return from(ref.result).pipe(catchError(err => EMPTY));
  }

}
