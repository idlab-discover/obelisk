import { APP_INITIALIZER, NgModule, SecurityContext } from '@angular/core';
import { CommonModule, registerLocaleData, TitleCasePipe } from '@angular/common';
import { HttpClientModule } from "@angular/common/http";
import localeBe from '@angular/common/locales/nl-BE';
import { ClipboardModule } from '@angular/cdk/clipboard';

// Third party
import { MarkdownModule, MarkedOptions, MarkedRenderer } from 'ngx-markdown';

// Proprietary
import { ConfigService } from './services/config.service';
import { httpInterceptorProviders } from './interceptors';

registerLocaleData(localeBe, 'nl-BE');

/**
 * In the Core Module we commonly place our singleton services and modules that will 
 * be used across the app but only need to be imported **once**. Examples are an Authentication 
 * Service or LocalStorage Service, but also modules like HttpClientModule , StoreModule.forRoot(…), 
 * TranslateModule.forRoot(…) . The CoreModule is then imported into the AppModule .
 */
@NgModule({
  declarations: [
  ],
  imports: [
    CommonModule,
    HttpClientModule,
    ClipboardModule,
    MarkdownModule.forRoot({
      sanitize: SecurityContext.NONE,
      markedOptions: {
        provide: MarkedOptions,
        useFactory: markedOptionsFactory
      }
    })
  ],
  providers: [
    {
      provide: APP_INITIALIZER,
      useFactory: (config: ConfigService) => () => config.loadConfig(),
      deps: [ConfigService],
      multi: true
    },
    httpInterceptorProviders,
    TitleCasePipe
  ]
})
export class CoreModule { }

// function that returns `MarkedOptions` with renderer override
export function markedOptionsFactory(): MarkedOptions {
  const renderer = new MarkedRenderer();

  renderer.blockquote = (text: string) => {
    return '<blockquote class="blockquote"><p class="mb-0">' + text + '</p></blockquote>';
  };

  return {
    renderer: renderer,
    gfm: true,
    breaks: true,
    pedantic: false,
    smartLists: true,
    smartypants: true,
  };
}
