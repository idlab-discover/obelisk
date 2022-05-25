import { AfterViewInit, Component, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ConfigService } from '@core/services';
import { CodemirrorComponent } from '@ctrl/ngx-codemirror';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import Ajv from "ajv";
import CodeMirror, { Hint, Hints } from 'codemirror';
import * as jp from 'jsonpath';
import { of } from 'rxjs';
import { debounceTime, switchMap } from 'rxjs/operators';

const ajv = new Ajv({ allowMatchingProperties: true });

@UntilDestroy()
@Component({
  selector: 'app-filter-editor',
  templateUrl: './filter-editor.component.html',
  styleUrls: ['./filter-editor.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class FilterEditorComponent implements OnInit, AfterViewInit {
  @ViewChild('cm') cm: CodemirrorComponent;
  hideDocs: boolean = false;
  schema: any;

  /** Confirmed or not when closed */
  confirmed = false;

  myForm: FormGroup;

  editor = `{}`;
  options: CodeMirror.EditorConfiguration = {
    lineNumbers: true,
    theme: 'eclipse',
    mode: { name: 'javascript', json: true },
    smartIndent: true,
    matchBrackets: true,
    extraKeys: {
      "Ctrl-Space": "autocomplete",
      "Shift-Alt-F": "prettify"
    },
    autoCloseBrackets: true,
    showHint: true,
    hintOptions: {
      hint: this.hintFn.bind(this, () => this.keys),
      completeSingle: false,
      alignWithWord: true,
    },
  };

  isValidJson: boolean = true;
  isValidFilter: boolean = true;

  iFrameSrc: string = '';

  private validate;
  private keys = [];
  private host: string;

  private hintFn(getKeysFn: () => string[], cm: CodeMirror.Editor): Hints {
    const keys = getKeysFn();
    const cur = cm.getCursor(), token = cm.getTokenAt(cur, true)
    const type = cm.getTokenTypeAt(cur);
    const isString = type?.startsWith('string');
    let term, from = CodeMirror.Pos(cur.line, token.start), to = cur
    if (token.start < cur.ch && /\w/.test(token.string.charAt(cur.ch - token.start - 1))) {
      term = token.string.substr(0, cur.ch - token.start)
    } else {
      term = ""
      from = cur
    }
    var found = [];
    for (var i = 0; i < keys.length; i++) {
      var word = keys[i];
      if (!isString && word.slice(0, term.length) == term)
        found.push({
          text: `"${word}"`,
          displayText: word
        } as Hint);
      if (isString && word.slice(0, term.length - 1) == term.slice(1)) {
        found.push({
          text: word,
          from: { ch: from.ch + 1, line: from.line }
        } as Hint);
      }
      if (isString && word.slice(0, term.length) == term) {
        found.push(word);
      }
    }

    if (found.length) return { list: found, from: from, to: to };

  }

  constructor(public activeModal: NgbActiveModal, private config: ConfigService, fb: FormBuilder) {
    this.myForm = fb.group({
      editor: []
    });

    this.myForm.get('editor').valueChanges.pipe(
      untilDestroyed(this),
      debounceTime(200),
      switchMap(val => {
        try {
          const json = JSON.parse(val);
          return of(json);
        } catch (err) {
          return of(undefined)
        }
      }),
    )
      .subscribe({
        next: json => {
          this.isValidJson = (json != null);
          this.isValidFilter = this.validate(json);
        },
        error: console.error
      });
  }

  ngOnInit(): void {
    // If running local, use live docs as iframe
    const host = this.config.getCfg().clientHost;
    const isRunningLocal = host.startsWith('http://localhost') || host.startsWith('http://127.0.0.1');
    this.iFrameSrc = (isRunningLocal ? 'https://rc.obelisk.ilabt.imec.be' : host) + '/docs/tech_reference/filters.html#operator-fields';
  }

  ngAfterViewInit(): void {
    setTimeout(() => {
      const cm = this.cm.codeMirror;
      const that = this;
      this.cm.codeMirrorGlobal.commands['prettify'] = function (cm) {
        that.prettify(cm);
      }
      cm.focus();
      this.cm.codeMirror.execCommand('prettify');
      cm.setCursor({ ch: 1, line: 0 });
    }, 200);
  }

  init(filter: any, schema: any) {
    if (filter != null) {
      this.myForm.get('editor').setValue(JSON.stringify(filter));

    } else {
      this.myForm.get('editor').setValue('{}');
    }
    this.schema = schema;
    this.validate = ajv.compile(schema);
    const res = jp.query(schema, '$..properties');
    this.keys = res.reduce((arr: string[], curr: any) => [...arr, ...Object.keys(curr).filter(k => k.startsWith('_'))], []);
  }

  prettify(cm?: CodeMirror.Editor) {
    cm = cm ?? this.cm.codeMirror;
    const isSelection = cm.getSelection().length > 0;
    if (!isSelection) {
      // If valid json: prettify
      let val = cm.getValue();
      try {
        val = JSON.parse(val);
        val = JSON.stringify(val, null, 2);
        cm.setValue(val);
      } catch {
        // NO valid json: smartindent
        const lastLine = cm.lineCount() - 1;
        const lastLineChar = cm.getLine(lastLine).length - 1;
        const start = { line: 0, ch: 0 };
        const end = { line: lastLine, ch: lastLineChar };
        cm.setSelection(start, end);
      }
    } else {
      // If valid json: prettify
      let val = cm.getSelection()
      try {
        val = JSON.parse(val);
        val = JSON.stringify(val, null, 2);
        cm.setValue(val);
      } catch {
        // NO valid json: smartindent
        cm.indentSelection('smart');
      }
    }
  }

  confirm() {
    if (this.isValidJson) {
      this.cm.codeMirror.execCommand('prettify');
      this.activeModal.close(JSON.parse(this.myForm.get('editor').value));
    }
  }

  decline() {
    this.confirmed = false;
    this.activeModal.dismiss();
  }


}
