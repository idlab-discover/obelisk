import { Pipe, PipeTransform } from '@angular/core';
import { Tx } from '../utils';

@Pipe({
  name: 'nr'
})
export class NrPipe implements PipeTransform {

  transform(value: number, precision: number = 0, prefix: boolean = true, niceFormatting: boolean = true): string {
    return Tx.number(value, precision, prefix, niceFormatting);
  }

}
