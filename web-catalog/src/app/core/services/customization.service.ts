import { Injectable } from '@angular/core';
import custom from '../../../assets/customization/customize.json';

@Injectable({
  providedIn: 'root'
})
export class CustomizationService {

  constructor() { }

  load(): Customization {
    return custom as Customization;
  }


}

export interface Customization {
  brandName: string;
}
