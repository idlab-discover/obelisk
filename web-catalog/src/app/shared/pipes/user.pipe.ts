import { Pipe, PipeTransform } from '@angular/core';
import { User } from '@shared/model';

@Pipe({
  name: 'user'
})
export class UserPipe implements PipeTransform {

  transform(user: Partial<User>, showIncompleteName: boolean = false): string {
    if (user == null) {
      return '-';
    }
    if (user.firstName != null && user.lastName != null) {
      return `${user.firstName} ${user.lastName}`;
    } else if (user.firstName != null) {
      return showIncompleteName ? `${user.firstName} (${user.email})` : user.email;
    } else if (user.lastName != null) {
      return showIncompleteName ? `${user.lastName} (${user.email})` : user.email;
    }
  }
}
