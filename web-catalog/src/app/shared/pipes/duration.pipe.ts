import { Pipe, PipeTransform } from '@angular/core';
import moment from 'moment';
import { AgoPipe } from './ago.pipe';

@Pipe({
  name: 'duration',
})
export class DurationPipe implements PipeTransform {
  constructor(private ago: AgoPipe) {

  }

  transform(ms: number, exact: boolean = false, relativeToNow: boolean = false): string {
    if (relativeToNow) {
      const time = moment.now()+ms;
      return this.ago.transform(time, exact);
    } else {
      const duration = moment.duration(ms, 'milliseconds');
      let txt = '';
      if (!exact) {
        return duration.humanize();
      } else {
        const y = duration.years();
        const M = duration.months();
        const d = duration.days();
        const h = duration.hours();
        const m = duration.minutes();
        const s = duration.seconds();

        if (y > 0) {
          txt += y;
          txt += y === 1 ? 'year ' : ' years ';
        }
        if (M > 0) {
          txt += M;
          txt += M === 1 ? 'month ' : ' months ';
        }
        if (d > 0) {
          txt += d;
          txt += d === 1 ? 'day ' : ' days ';
        }
        if (h > 0) {
          txt += h;
          txt += h === 1 ? 'hour ' : ' hours ';
        }
        if (m > 0) {
          txt += m;
          txt += m === 1 ? 'minute ' : ' minutes ';
        }
        if (s > 0) {
          txt += s;
          txt += s === 1 ? 'second ' : ' seconds ';
        }
        if (txt.length > 0) {
          txt = txt.substr(0, txt.length - 1);
        }
        return txt;
      }
    }
  }
}
