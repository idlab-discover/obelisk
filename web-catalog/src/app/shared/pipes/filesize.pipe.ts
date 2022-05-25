import { Pipe, PipeTransform } from '@angular/core';
import { Tx } from "../utils";

@Pipe({
  name: 'filesize'
})
export class FilesizePipe implements PipeTransform {

  transform(value: number, decimals: number = 2, inputUnit: 'B' | 'KB' | 'MB' | 'GB' = 'B'): any {
    return Tx.fileSize(value, decimals, inputUnit);
  }

}
