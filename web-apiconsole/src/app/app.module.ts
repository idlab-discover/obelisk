import { NgModule, APP_INITIALIZER } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { GraphiQLComponent } from './graphi-ql/graphi-ql.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MaterialModule } from './material/material.module';
import { HomeComponent } from './home/home.component';
import { HttpClientModule } from '@angular/common/http';
import { LoginComponent } from './login/login.component';
import { ConfigService } from './config.service';
import { HttpComponent } from './http/http.component';
import { ReactiveFormsModule } from '@angular/forms';
import { ClipboardModule } from '@angular/cdk/clipboard';

export function loadConfig(config: ConfigService) {
  return () => config.loadConfig();
}

@NgModule({
  declarations: [
    AppComponent,
    GraphiQLComponent,
    HomeComponent,
    LoginComponent,
    HttpComponent,
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    BrowserAnimationsModule,
    ReactiveFormsModule,
    MaterialModule,
    HttpClientModule, 
    ClipboardModule
  ],
  providers: [
    ConfigService,
    {
      provide: APP_INITIALIZER,
      useFactory: loadConfig,
      deps: [ConfigService],
      multi: true
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
