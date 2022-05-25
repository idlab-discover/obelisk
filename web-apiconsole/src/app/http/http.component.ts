import { Clipboard } from '@angular/cdk/clipboard';
import { HttpClient } from '@angular/common/http';
import { Component, isDevMode, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HistoryService, Item } from 'app/history.service';
import { Observable } from 'rxjs';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-http',
  templateUrl: './http.component.html',
  styleUrls: ['./http.component.scss']
})
export class HttpComponent implements OnInit {

  private token: string;
  private host: string;
  httpForm: FormGroup;
  output: any;
  items: Item[];

  constructor(
    private http: HttpClient,
    private clipboard: Clipboard,
    private history: HistoryService,
    auth: AuthService,
    fb: FormBuilder) {
    this.token = auth.getClient().getTokens().accessToken;
    this.host = isDevMode() ? 'http://localhost:8080' : auth.getClient().getHostUrl();
    this.httpForm = fb.group({
      method: ['GET', Validators.required],
      path: ['', Validators.required],
      body: []
    });
  }

  bodyDisabled() {
    return this.httpForm.get('method').value === 'GET'
      || this.httpForm.get('method').value === 'DELETE';
  }

  private full(path: string) {
    return path.startsWith('/') ? this.host + path : this.host + '/' + path;
  }

  ngOnInit(): void {
    this.reloadHistory();
    this.restore(this.items[0]);
  }

  private reloadHistory() {
    this.items = this.history.listItems();
  }

  private tokenHeader() {
    return {
      Authorization: `Bearer ${this.token}`
    };
  }

  private doGet(path: string): Observable<Object> {
    return this.http.get(this.full(path), {
      headers: this.tokenHeader()
    });
  }

  private doPost(path: string, body: any): Observable<Object> {
    return this.http.post(this.full(path), body, {
      headers: this.tokenHeader()
    });
  }

  private doPut(path: string, body: any): Observable<Object> {
    return this.http.put(this.full(path), body, {
      headers: this.tokenHeader()
    });
  }

  private doDelete(path: string): Observable<Object> {
    return this.http.delete(this.full(path), {
      headers: this.tokenHeader()
    });
  }

  doRequest() {
    if (this.httpForm.valid) {
      const path: string = this.httpForm.get('path').value;
      const body: any = this.httpForm.get('body').value;
      const method: string = this.httpForm.get('method').value;
      let obs: Observable<any>;
      switch (method) {
        case 'GET':
          obs = this.doGet(path);
          break;
        case 'POST':
          obs = this.doPost(path, body);
          break;
        case 'PUT':
          obs = this.doPut(path, body);
          break;
        case 'DELETE':
          obs = this.doDelete(path);
          break;
      }
      obs.subscribe(
        json => {
          this.history.pushItem({method, path, body});
          this.reloadHistory();
          this.setOutput(json, true);
        },
        err => this.setOutput(err, false)
      );
    }
  }

  restore(item:Item) {
    this.httpForm.reset({
      method: item?.method,
      path: item?.path,
      body: item?.body
    });
    this.output = undefined;
    const div = document.getElementById('my-output') as HTMLDivElement;
    div.classList.remove('success');
    div.classList.remove('failed');
  }

  private setOutput(output: any, isSuccess: boolean) {
    const div = document.getElementById('my-output') as HTMLDivElement;
    div.classList.add(isSuccess ? 'success' : 'failed');
    div.classList.remove(isSuccess ? 'failed' : 'success');
    this.output = output;
  }

  clear() {
    this.httpForm.reset();
    this.httpForm.get('method').setValue('GET');
    this.output = undefined;
    const div = document.getElementById('my-output') as HTMLDivElement;
    div.classList.remove('success');
    div.classList.remove('failed');
  }

  copy() {
    this.clipboard.copy(JSON.stringify(this.output, null, 2));
    const el = document.getElementById('body-copy-token-icon');
    const txt = el.textContent;
    el.textContent = 'check_circle_outline';
    setTimeout(() => {
      el.textContent = txt;
    }, 1500);
  }

}
