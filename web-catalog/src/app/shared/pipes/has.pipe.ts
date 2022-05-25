import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'has'
})
export class HasPipe implements PipeTransform {

  transform<T>(array: Array<T>, needle: T): boolean {
    return array.includes(needle);
  }

}
