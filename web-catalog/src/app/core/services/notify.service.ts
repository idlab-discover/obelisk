import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class NotifyService {

  constructor() { }

  error(message: string) {
    alert(message);
  }
}
