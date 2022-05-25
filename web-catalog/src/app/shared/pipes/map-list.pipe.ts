import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'mapList',
  pure: true
})
export class MapListPipe implements PipeTransform {

  transform(array: any[], path: string, delimiter: string = ', ', limit?: number | null, ellipsis: string = '...'): string {
    const mapper = (value: any) => {
      let p;
      if (path == null || path === '.' || path === '') {
        p = [];
      } else {
        p = path.split(".");
      }
      return p.length === 0 ? value : p.reduce((acc, cur) => acc[cur], value);
    };
    if (array == null) {
      return undefined;
    } else if (limit && array.length > limit) {
      return array.slice(0,limit).map(mapper).join(delimiter) + delimiter + ellipsis;
    } else {
      return array.map(mapper).join(delimiter);
    }
  }

}
