import { HttpClient } from '@angular/common/http';
import { Component, OnInit, isDevMode } from '@angular/core';
import { defer, onErrorResumeNext, of, config } from 'rxjs';
import { AuthService } from '../auth.service';
import { Editor } from './editor';
import { tap, catchError, map } from 'rxjs/operators';

@Component({
  selector: 'app-graphi-ql',
  templateUrl: './graphi-ql.component.html',
  styleUrls: ['./graphi-ql.component.scss']
})
export class GraphiQLComponent implements OnInit {

  private uri: string;

  constructor(private http: HttpClient, private auth: AuthService) {
    const cfg = this.auth.getClient().getConfig();
    const catalogBasePath = cfg.oblxApiPrefix+'/catalog/graphql';
    this.uri = cfg.oblxHost+catalogBasePath;
  }

  ngOnInit() {
    Editor.initialize(this.obsFetcher);
  }

  private obsFetcher = (graphQLParams: any) => {
    return defer(() => this.http.post(this.uri, JSON.stringify(graphQLParams), {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + this.auth.getTokens().accessToken,
      }
    })
      // .pipe(
      //   catchError(err => of(err)),
      //   tap(console.log))
      );
  }
}
