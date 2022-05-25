import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'checkmark'
})
export class CheckmarkPipe implements PipeTransform {

  transform(input: boolean, htmlColored: boolean = false) {
    if (htmlColored) {
      return input ? '<font color="green">\u2713</font>' : '<font color="red">\u2718</font>';
    } else {
      return input ? '\u2713' : '\u2718';
    }
  }

}
