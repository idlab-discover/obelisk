import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { CoreModule } from "@core/core.module";
import { FaIconLibrary, FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faAngleDoubleLeft, faAngleDoubleRight, faArchive, faBan, faBook, faBoxOpen, faBullseye, faChartLine, faCheckCircle, faChevronCircleLeft, faChevronCircleRight, faCircle, faClipboardList, faCopy, faDatabase, faDesktop, faDownload, faEdit, faExclamationCircle, faExclamationTriangle, faExpandAlt, faFileExport, faFileUpload, faHandshake, faHandsHelping, faHeadset, faHistory, faHome, faInfoCircle, faKey, faLock, faMagic, faMapMarkedAlt, faMapMarkerAlt, faNewspaper, faPen, faQuestionCircle, fas, faShieldAlt, faSignOutAlt, faSitemap, faStream, faSyncAlt, faTable, faTabletAlt, faTh, faToggleOff, faToggleOn, faTools, faTrash, faUnlock, faUpload, faUser, faUserCheck, faUserCircle, faUserCog, faUserFriends, faUserPlus, faUsers, faUserSlash, faUsersSlash, faWaveSquare } from '@fortawesome/free-solid-svg-icons';
import { SharedModule } from '@shared/shared.module';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { HomeModule } from './modules/home/home.module';





@NgModule({
  declarations: [
    AppComponent
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,
    AppRoutingModule,
    CoreModule,
    HomeModule,
    SharedModule,
    FontAwesomeModule    
  ],
  bootstrap: [AppComponent]
})
export class AppModule {
  constructor(library: FaIconLibrary) {
    library.addIconPacks(fas);
    library.addIcons(
      faAngleDoubleLeft,
      faAngleDoubleRight,
      faArchive,
      faBan,
      faBook,
      faBoxOpen,
      faBullseye,
      faChartLine,
      faCheckCircle,
      faCircle,
      faChevronCircleLeft,
      faChevronCircleRight,
      faClipboardList,
      faCopy,
      faDatabase,
      faDesktop,
      faDownload,
      faEdit,
      faExclamationCircle,
      faExclamationTriangle,
      faExpandAlt,
      faFileExport,
      faFileUpload,
      faHandshake,
      faHandsHelping,
      faHeadset,
      faHistory,
      faHome,
      faInfoCircle,
      faKey,
      faLock,
      faMagic,
      faMapMarkedAlt,
      faMapMarkerAlt,
      faNewspaper,
      faPen,
      faQuestionCircle,
      faShieldAlt,
      faSignOutAlt,
      faSitemap,
      faStream,
      faSyncAlt,
      faTabletAlt,
      faTable,
      faTh,
      faToggleOff,
      faToggleOn,
      faTools,
      faTrash,
      faUnlock,
      faUpload,
      faUser,
      faUserCheck,
      faUserCircle,
      faUserCog,
      faUserFriends,
      faUserPlus,
      faUserSlash,
      faUsers,
      faUsersSlash,
      faWaveSquare
    );
  }

}


