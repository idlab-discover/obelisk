import { Pipe, PipeTransform } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { ObeliskService } from '@core/services/obelisk.service';
import { ResourceType } from '@shared/model';
import { ConfigService } from '@core/services/config.service';

@Pipe({
  name: 'resource'
})
export class ResourcePipe implements PipeTransform {

  constructor(
    private http: HttpClient,
    private obelisk: ObeliskService,
    private config: ConfigService
  ) { }

  async transform(datasetId: string, resourceType: ResourceType, isCss: boolean = false): Promise<string> {
    if (!datasetId) {
      const asset = (resourceType === 'readme') ? '' : './assets/img/project/_generic.jpg';
      return isCss ? `url(${asset})` : asset;
    }
    
    const src = this.obelisk.getResourceUrl(datasetId, resourceType);
    try {
      const blob = await this.http.get(src, { responseType: 'blob' }).toPromise();
      const reader = new FileReader();
      return new Promise((resolve, reject) => {
        reader.onloadend = () => {
          const res = reader.result as string;
          if (res)
            resolve(isCss ? `url(${res})` : res);
        };
        switch (resourceType) {
          case 'banner':
          case 'thumbnail':
            reader.readAsDataURL(blob);
            break;
          default:
          case 'readme':
            reader.readAsText(blob);
            break;
        }
      });
    } catch (err) {
      if (err.status === 401) {
        const asset = './assets/img/project/_unauthorized.jpg';
        return isCss ? `url(${asset})` : asset;
      } else if (err.status === 404) {
        let asset;
        switch (resourceType) {
          default:
          case 'banner':
            asset = `./assets/img/project/${this.hash(datasetId)}.png`;
            break;
          case 'thumbnail':

            asset = `./assets/img/project/_thumb${this.hash(datasetId)}.png`;
            break;
          case 'readme':
            asset = this.config.getReadmeTemplate();
            break;
        }
        return isCss ? `url(${asset})` : asset;
      } else {
        const asset = './assets/img/project/_unauthorized.jpg';
        return isCss ? `url(${asset})` : asset;
      }
    }
  }

  private hash(datasetId: string) {
    let total = 0;
    for (let i = 0; i < datasetId.length; i++) {
      total += datasetId.codePointAt(i);
    }
    const nr = total % 10;
    return '_' + nr;
  }

}