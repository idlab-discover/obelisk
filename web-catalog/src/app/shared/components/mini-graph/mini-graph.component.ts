import { AfterViewInit, Component, DoCheck, ElementRef, Host, HostListener, Input, IterableChanges, IterableDiffer, IterableDiffers, OnInit, ViewChild } from '@angular/core';
import { TinyColor } from '@ctrl/tinycolor';
import { NrPipe } from '@shared/pipes';
import { Tx } from '@shared/utils';
import moment from 'moment';


@Component({
  selector: 'app-mini-graph, mini-graph',
  templateUrl: './mini-graph.component.html',
  styleUrls: ['./mini-graph.component.scss']
})
export class MiniGraphComponent implements OnInit, DoCheck, AfterViewInit {
  @Input() values: number[];
  @Input() ts: number;
  @Input() max: number;
  @Input() min: number;
  @Input() caption: string;
  @Input() unit: string;
  @Input() width: number;
  @Input() height: number;
  @Input() color: string = "#5d6985";
  @Input() line: 'straight' | 'quadratic' | 'rounded' = 'rounded';
  @Input() fuzzy: boolean = true;

  @ViewChild('graph') graph: ElementRef<HTMLDivElement>;
  @ViewChild('canvas') canvas: ElementRef<HTMLCanvasElement>;

  private w: number;
  private h: number;
  private ctx: CanvasRenderingContext2D;
  private maxWasNotSet = false;
  private minWasNotSet = false;

  private options = {
    marginTopPercent: 0.5,
    txtOffsetLeft: 6,
    fontSizeCaption: 13,
    fontSizeValue: 25,
    fontSizeUnit: 12,
  }

  private baseColor: string;
  private lightColor: string;
  private textColor: string;
  private borderColor: string;

  private calcCololors() {
    const color = new TinyColor(this.color);
    this.baseColor = color.toHexString();
    this.lightColor = color.brighten(20).toHexString();
    this.textColor = '#ffffff';
    this.borderColor = color.darken(10).toHexString();
  }

  private _diff: IterableDiffer<number>;

  constructor(private _iterableDiffers: IterableDiffers, private nrPipe: NrPipe) { }

  ngOnInit(): void {
    this._diff = this._iterableDiffers.find(this.values).create();
  }

  ngDoCheck() {
    const changes: IterableChanges<number> = this._diff.diff(this.values);
    if (changes) {
      if (this.max == null || this.maxWasNotSet) {
        this.maxWasNotSet = true;
        this.max = Math.max(...this.values);
      }
      if (this.min == null || this.minWasNotSet) {
        this.minWasNotSet = true;
        this.min = Math.min(...this.values);
      }
      this.redraw(this.ctx, this.options);
    }

  }

  ngAfterViewInit() {
    this.getImage();
  }

  @HostListener('mouseover', ['event'])
  onMouseOver(event: MouseEvent) {
    this.redrawDetailed(this.ctx, this.options);
  }

  @HostListener('mouseout', ['event'])
  onMouseOut(event: MouseEvent) {
    this.redraw(this.ctx, this.options);
  }

  private redraw(ctx: CanvasRenderingContext2D, opt: any) {
    if (ctx) {
      ctx.lineWidth = 0;
      ctx.clearRect(0, 0, this.w, this.h);
      // Render graph background
      ctx.fillStyle = this.baseColor;
      ctx.fillRect(0, 0, this.w, this.h);

      if (this.values.length > 2) {
        const offsetY = opt.marginTopPercent;
        const baseline = this.h;
        const segmentWidth = this.w / (this.values.length - 1);
        const segmentHeight = (value: number) => {
          const val = ((value - this.min) / (this.max - this.min)) * (baseline * (1 - offsetY));
          return baseline - val;
        }

        const calcPoint = (distance: number, fromValue: number, toValue: number, delta: number) => {
          const m = -(fromValue - toValue) / distance;
          const b = fromValue;
          return (m * delta) + b;
        }

        switch (this.line) {
          case 'straight':
            // Straight
            ctx.moveTo(0, baseline);
            ctx.beginPath();
            for (let i = 0; i < this.values.length; i++) {
              ctx.lineTo(segmentWidth * (i), segmentHeight(this.values[i]));
            }
            ctx.lineTo(this.w, baseline);
            ctx.lineTo(0, baseline);
            ctx.closePath();
            break;
          case 'rounded':
          case 'quadratic':
            const offset = 'quadratic' == this.line ? segmentWidth / 2 : segmentWidth / 8;
            let lastPoint = baseline;
            ctx.moveTo(0, baseline);
            ctx.beginPath();

            for (let i = 0; i < this.values.length; i++) {
              if (i == 0) {
                ctx.lineTo(0, segmentHeight(this.values[i]));
              } else if (i == this.values.length - 1) {
                ctx.lineTo((segmentWidth * i), segmentHeight(this.values[i]))
              } else {
                const a = calcPoint(segmentWidth, lastPoint, this.values[i], segmentWidth - offset);
                const b = calcPoint(segmentWidth, this.values[i], this.values[i + 1], offset);
                ctx.lineTo((segmentWidth * i) - offset, segmentHeight(a));
                ctx.quadraticCurveTo(segmentWidth * i, segmentHeight(this.values[i]), (segmentWidth * i) + offset, segmentHeight(b));
              }
              lastPoint = this.values[i];
            }
            ctx.lineTo(this.w, baseline);
            ctx.lineTo(0, baseline);
            ctx.closePath();
            break;
        }

        ctx.lineWidth = 0;
        ctx.strokeStyle = this.lightColor;
        ctx.fillStyle = this.lightColor;
        ctx.fill();
        ctx.stroke();
      }

      // Render text above
      ctx.fillStyle = this.textColor;
      // Caption
      ctx.font = `${opt.fontSizeCaption}px Arial`;
      ctx.textAlign = 'left';
      const msr = ctx.measureText(this.caption);
      ctx.fillText(this.caption, opt.txtOffsetLeft, opt.fontSizeCaption + 2);
      // Mean value
      ctx.font = `${opt.fontSizeValue}px Arial`;
      let value = '';
      if (this.values?.length > 1) {
        let mean = this.values.reduce((acc, cur) => acc + (isNaN(cur) ? 0 : cur)) / this.values.length;
        mean = mean < 1 ? Tx.round(mean, 3) : Math.round(mean);
        value = this.fuzzy ? this.nrPipe.transform(mean) : mean + '';
      }
      // const value = this.values?.length > 1 ? this.nrPipe.transform(this.values[this.values.length - 1]) : '';
      const msr2 = ctx.measureText(value);
      ctx.fillText(value, opt.txtOffsetLeft + msr.width + 10, this.h - (2 * (this.h - opt.fontSizeValue) / 3));
      // Unit
      ctx.font = `${opt.fontSizeUnit}px Arial`;
      const unit = this.values?.length > 1 ? this.unit : '';
      ctx.fillText(unit, opt.txtOffsetLeft + msr.width + 10 + msr2.width + 5, opt.fontSizeValue)

      // Outside border
      ctx.strokeStyle = this.borderColor;
      ctx.lineWidth = 2;
      ctx.strokeRect(0, 0, this.w, this.h);
      ctx.lineWidth = 0;
    }
  }

  private redrawDetailed(ctx: CanvasRenderingContext2D, opt: any) {
    if (ctx) {

      ctx.clearRect(0, 0, this.w, this.h);
      // Render graph background
      ctx.fillStyle = this.baseColor;
      ctx.fillRect(0, 0, this.w, this.h);
      ctx.lineWidth = 0;

      if (this.values.length > 2) {
        const offsetY = opt.marginTopPercent;
        const baseline = this.h;
        const segmentWidth = this.w / (this.values.length - 1);
        const segmentHeight = (value: number) => {
          const val = ((value - this.min) / (this.max - this.min)) * (baseline * (1 - offsetY));
          return baseline - val;
        }

        const calcPoint = (distance: number, fromValue: number, toValue: number, delta: number) => {
          const m = -(fromValue - toValue) / distance;
          const b = fromValue;
          return (m * delta) + b;
        }

        switch (this.line) {
          case 'straight':
            // Straight
            ctx.moveTo(0, baseline);
            ctx.beginPath();
            for (let i = 0; i < this.values.length; i++) {
              ctx.lineTo(segmentWidth * (i), segmentHeight(this.values[i]));
            }
            ctx.lineTo(this.w, baseline);
            ctx.lineTo(0, baseline);
            ctx.closePath();
            break;
          case 'rounded':
          case 'quadratic':
            const offset = 'quadratic' == this.line ? segmentWidth / 2 : segmentWidth / 8;
            let lastPoint = baseline;
            ctx.moveTo(0, baseline);
            ctx.beginPath();

            for (let i = 0; i < this.values.length; i++) {
              if (i == 0) {
                ctx.lineTo(0, segmentHeight(this.values[i]));
              } else if (i == this.values.length - 1) {
                ctx.lineTo((segmentWidth * i), segmentHeight(this.values[i]))
              } else {
                const a = calcPoint(segmentWidth, lastPoint, this.values[i], segmentWidth - offset);
                const b = calcPoint(segmentWidth, this.values[i], this.values[i + 1], offset);
                ctx.lineTo((segmentWidth * i) - offset, segmentHeight(a));
                ctx.quadraticCurveTo(segmentWidth * i, segmentHeight(this.values[i]), (segmentWidth * i) + offset, segmentHeight(b));
              }
              lastPoint = this.values[i];
            }
            ctx.lineTo(this.w, baseline);
            ctx.lineTo(0, baseline);
            ctx.closePath();
            break;
        }
        ctx.strokeStyle = this.lightColor;
        ctx.fillStyle = this.lightColor;
        ctx.fill();
        ctx.stroke();
      }

      // Render text above
      ctx.fillStyle = this.textColor;
      // Caption
      ctx.font = `${opt.fontSizeCaption}px Arial`;
      ctx.textAlign = 'left';
      const msr = ctx.measureText(this.caption);
      ctx.fillText(this.caption, opt.txtOffsetLeft, opt.fontSizeCaption + 2);
      // Calculcate first : mean and max
      let mean = 0;
      let idx = 0;
      let max = 0;
      let meanTxt;
      let maxTxt;
      if (this.values?.length > 1) {
        max = this.values.reduce((acc, cur, i) => {
        if (cur >= acc) {
          idx = i;
          return cur;
        } else {
          return acc;
          }
        }
        , 0);
        mean = this.values.reduce((acc, cur) => acc + (isNaN(cur) ? 0 : cur)) / this.values.length;
        max = max < 1 ? Tx.round(max, 3) : Math.round(max);
        mean = mean < 1 ? Tx.round(mean, 3) : Math.round(mean);
        const padLength = Math.max((max+'').length, (mean+'').length);
        meanTxt = `mean: ${(mean+'').padStart(padLength, ' ')} ${this.unit}`
        maxTxt = `max: ${(max+'').padStart(padLength, ' ')} ${this.unit}`
      }

      const margin = 4;
      
      // Mean
      ctx.font = `${opt.fontSizeUnit-1}px monospace`;
      const msr2 = ctx.measureText(meanTxt);
      ctx.textAlign = 'right'
      ctx.fillText(meanTxt, this.w - margin, this.options.fontSizeUnit);

      // Max line
      const y = this.h * this.options.marginTopPercent;
      // const x = Math.round(this.w * ((idx + 1) / this.values.length));
      // ctx.lineWidth = 1;
      // ctx.strokeStyle = this.lightColor;
      // ctx.setLineDash([2,2])
      // ctx.beginPath();
      // ctx.moveTo(x+.5, y-.5);
      // ctx.lineTo(this.w+.5, y-.5);
      // ctx.closePath();
      // ctx.stroke();
      // ctx.lineWidth = 0;
      // ctx.setLineDash([0,0]);
      

      // Max txt
      ctx.textAlign = 'right';
      const ms = ctx.measureText(maxTxt);
      ctx.fillText(maxTxt, this.w - margin, y + Math.floor(this.options.fontSizeUnit / 2) - 3);

      // Time
      const to = moment(this.ts);
      const from = to.clone().subtract(1, 'hour');
      ctx.font = `${this.options.fontSizeUnit - 1}px Arial`;
      ctx.textAlign = 'left';
      ctx.fillText(from.format('HH:mm'), 0 + margin, this.h - margin);
      ctx.textAlign = 'right';
      ctx.fillText(to.format('HH:mm'), this.w - margin, this.h - margin);

      // Outside border
      ctx.strokeStyle = this.borderColor;
      ctx.lineWidth = 2;
      ctx.strokeRect(0, 0, this.w, this.h);
      ctx.lineWidth = 0;
    }
  }

  private getImage() {
    if (this.canvas) {
      const graph = this.graph.nativeElement;
      const canvas = this.canvas.nativeElement;
      this.calcCololors();
      this.ctx = canvas.getContext("2d");
      this.w = this.width ?? graph.clientWidth;
      this.h = this.height ?? graph.clientHeight;
      canvas.width = this.w;
      canvas.height = this.h;

      this.redraw(this.ctx, this.options);
    }
  }

}


