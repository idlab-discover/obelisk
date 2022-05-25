import { Pipe, PipeTransform } from '@angular/core';
import moment from 'moment';

@Pipe({
  name: 'ago',
  pure: false
})
export class AgoPipe implements PipeTransform {

  transform(millis: number, short:boolean = false): any {
    if (!millis) {
      return millis;
    }
    if (typeof(millis) == 'string') {
      millis = parseInt(millis);
    }
    return moment(millis).fromNow(short);
  }

}