import { Clipboard } from '@angular/cdk/clipboard';
import { Component, HostListener, Input, OnInit } from '@angular/core';
import { ToastService } from '@core/services';


@Component({
  selector: 'app-id',
  templateUrl: './id.component.html',
  styleUrls: ['./id.component.scss']
})
export class IdComponent implements OnInit {

  @Input() id: string;

  constructor(
    private clipboard: Clipboard,
    private toast: ToastService
    ) { }

  ngOnInit(): void {
  }

  @HostListener('click')
  copyId() {
    this.clipboard.copy(this.id);
    this.toast.show("ID copied to clipboard")
  }

}
