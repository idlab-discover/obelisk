import { Injectable } from '@angular/core';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class HistoryService {
  private ns: string;
  private bufferSize: number = 15;

  private lastId = 0;

  constructor(private auth: AuthService) {
    this.ns = auth.getNamespace();
  }

  private key() {
    return this.ns + ':history';
  }

  private getHistory(): Item[] {
    try {
      return JSON.parse(localStorage.getItem(this.key()) || '[]')
    } catch (error) {
      return [];
    }
  }

  private save(history: Item[]) {
    localStorage.setItem(this.key(), JSON.stringify(history))
  }


  /**
   * Add new item
   * @param item 
   * @return id 
   */
  pushItem(item: Item): void {
    let history = this.getHistory();
    if (JSON.stringify(history[0]) != JSON.stringify(item)) {
      history.unshift(item);
      if (history.length > this.bufferSize) {
        history = history.slice(0, this.bufferSize);
      }
      this.save(history);
    }
  }

  removeItem(idx: number) {
    const history = this.getHistory();
    history.splice(idx, 1);
    this.save(history);
  }

  listItems(): Item[] {
    return this.getHistory();
  }

}

export interface Item {
  path: string;
  method: string;
  body?: any;
}

