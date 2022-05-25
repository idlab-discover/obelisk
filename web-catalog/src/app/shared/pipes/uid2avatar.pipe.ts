import { AsyncPipe } from '@angular/common';
import { ThrowStmt } from '@angular/compiler';
import { ChangeDetectorRef, Pipe, PipeTransform } from '@angular/core';
import { ObeliskService } from '@core/services';
import { Utils } from '@shared/utils';
import { Observable } from 'rxjs';
import { map, switchMap, tap } from 'rxjs/operators';

@Pipe({
  name: 'uid2avatar',
})
export class Uid2avatarPipe implements PipeTransform {

  constructor(private obelisk: ObeliskService) {

  }

  transform(uid: string): Observable<string> {
    return this.obelisk.getUserAsAdmin(uid).pipe(
      map(user => Utils.generateAvatarImgUrl(user.firstName, user.lastName, 20))
    );
  }

}
