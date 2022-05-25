import { Directive, ElementRef, HostListener } from '@angular/core';

@Directive({
  selector: '[allowTab]'
})
export class AllowTabDirective {

  constructor(private el: ElementRef<HTMLTextAreaElement>) { }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(e: KeyboardEvent) {
    var after, before, end, lastNewLine, changeLength, re, replace, selection, start, val;
    if ((e.charCode === 9 || e.keyCode === 9) && !e.altKey && !e.ctrlKey && !e.metaKey) {
      e.preventDefault();
      start = this.el.nativeElement.selectionStart;
      end = this.el.nativeElement.selectionEnd;
      val = this.el.nativeElement.value;
      before = val.substring(0, start);
      after = val.substring(end);
      replace = true;
      if (start !== end) {
        selection = val.substring(start, end);
        if (~selection.indexOf('\n')) {
          replace = false;
          changeLength = 0;
          lastNewLine = before.lastIndexOf('\n');
          if (!~lastNewLine) {
            selection = before + selection;
            changeLength = before.length;
            before = '';
          } else {
            selection = before.substring(lastNewLine) + selection;
            changeLength = before.length - lastNewLine;
            before = before.substring(0, lastNewLine);
          }
          if (e.shiftKey) {
            re = /(\n|^)(\t|[ ]{1,8})/g;
            if (selection.match(re)) {
              start--;
              changeLength--;
            }
            selection = selection.replace(re, '$1');
          } else {
            selection = selection.replace(/(\n|^)/g, '$1\t');
            start++;
            changeLength++;
          }
          this.el.nativeElement.value = before + selection + after;
          this.el.nativeElement.selectionStart = start;
          this.el.nativeElement.selectionEnd = start + selection.length - changeLength;
        }
      }
      if (replace && !e.shiftKey) {
        this.el.nativeElement.value = before + '\t' + after;
        this.el.nativeElement.selectionStart = this.el.nativeElement.selectionEnd = start + 1;
      }
    }
  }



}
