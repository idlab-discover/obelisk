import { Component, OnInit } from '@angular/core';
import { TicketService } from '../../ticket.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {

  constructor(private ticket: TicketService) { }

  ngOnInit(): void {
    Promise.resolve().then(_ => this.ticket.openTicketFrame());
  }

}
