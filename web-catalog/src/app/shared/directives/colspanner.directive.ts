import { Directive, ElementRef } from '@angular/core';

@Directive({
  selector: '[colspanner]'
})
export class ColspannerDirective {

  constructor(ref: ElementRef<HTMLTableDataCellElement>) {
    const td = ref.nativeElement;
    const tr = td.parentNode;
    if (tr == null) { throw new Error('td[colspanner] needs to be within a tr element!') }
    const tbody = tr.parentNode;
    if (tbody == null) { throw new Error('td[colspanner] needs to be within a tbody>tr element!') }
    const table = tbody.parentNode;
    if (table == null) { throw new Error('td[colspanner] needs to be within a table>tbody>tr element!') }
    const thead_tr = table.querySelector('thead tr');
    if (thead_tr == null) { throw new Error('td[colspanner] needs to be within a table with a thead>tr section!') }
    const cols = thead_tr.querySelectorAll('th').length;
    if (cols == null) { throw new Error('td[colspanner] needs to be within a table with thead>tr>th elements') }
    td.colSpan = cols;
  }

}
