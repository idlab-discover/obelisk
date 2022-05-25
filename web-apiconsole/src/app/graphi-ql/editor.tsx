import GraphiQL from 'graphiql';
import * as React from 'react';
import * as ReactDOM from 'react-dom';
import { Observable } from 'rxjs';

export class Editor extends React.Component {
    static initialize(fetcher: (graphQLParams: any) => Promise<any>|Observable<any>) {
        const node =  document.getElementById('react-graphiql-view');
        ReactDOM.render(React.createElement(GraphiQL, {fetcher: fetcher}), node);
    }
}