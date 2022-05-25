import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Announcement } from '@shared/model';

@Component({
  selector: 'app-info-announcement',
  templateUrl: './info-announcement.component.html',
  styleUrls: ['./info-announcement.component.scss']
})
export class InfoAnnouncementComponent implements OnInit {
  announcement: Announcement;

  constructor(public activeModal: NgbActiveModal) { }

  ngOnInit(): void {

  }

  initFromAnnouncement(announcement: Announcement) {
    this.announcement = announcement;
  }

}
