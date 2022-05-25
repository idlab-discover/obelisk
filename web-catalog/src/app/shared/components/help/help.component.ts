import { Component, Input, OnInit, ViewEncapsulation } from '@angular/core';

@Component({
  selector: 'app-help, help',
  templateUrl: './help.component.html',
  styleUrls: ['./help.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class HelpComponent implements OnInit {
  @Input('text') text: string;
  @Input('icon') icon: string = 'question-circle';
  @Input('placement') placement: 'top' | 'right' | 'auto' | 'bottom' = 'auto';
  @Input('cursor') cursor: 'pointer' | 'help' | 'default' = 'help';

  constructor() { }

  ngOnInit(): void { 
    
  }

}
