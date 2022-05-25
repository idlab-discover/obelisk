import { AfterViewInit, Component, ElementRef, HostListener, Input, OnChanges, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { Status } from '@shared/model';
import { animationFrameScheduler, interval, Subscription } from 'rxjs';
import { map, throttleTime } from 'rxjs/operators';

@UntilDestroy()
@Component({
  selector: 'app-status, oblx-status',
  templateUrl: './status.component.html',
  styleUrls: ['./status.component.scss']
})
export class StatusComponent implements OnInit, AfterViewInit, OnChanges {
  @Input() values: Status[] = [];
  @Input() width: number;
  @Input() height: number;
  @Input() autoscale: boolean = false;

  @ViewChild('graph') graph: ElementRef<HTMLDivElement>;
  @ViewChild('canvas') canvas: ElementRef<HTMLCanvasElement>;
  private w: number;
  private h: number;
  private ww: number;
  private hh: number;
  private ctx: CanvasRenderingContext2D;

  @HostListener('window:resize')
  private onResize() {
    if (this.autoscale) {
      animationFrameScheduler.schedule(
        () => this.redraw(this.ctx, this.options),
        0);
    }
  }

  @HostListener('mouseover')
  private onMouseOver() {
    this.options.borderColor = '#085f88';
    this.options.textColor = '#085f88';
    animationFrameScheduler.schedule(
      () => this.redraw(this.ctx, this.options),
      0);
  }

  @HostListener('mouseout')
  private onMouseOut() {
    this.options.borderColor = '#4385a5';
    this.options.textColor = '#4385a5';
    animationFrameScheduler.schedule(
      () => this.redraw(this.ctx, this.options),
      0);
  }

  private options = {
    blockAmount: 15,
    blockMargin: 3,
    blockHeight: 14,
    blockWidth: 8,
    outerMargin: 10,
    fontSizeLabel: 12,
    fontSizeState: 12,
    borderWidth: 1,
    borderColor: '#4385a5',
    bgColor: 'white',
    blinkColor: 'white',
    greenColor: '#3CD671',
    redColor: '#DD484A',
    orangeColor: '#EB9413',
    lightColor: '#697891',
    textColor: '#4385a5',
  }

  private sub: Subscription;

  constructor() { }

  ngOnInit(): void {
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.values) {
      if (!this.sub || this.sub.closed) {
        this.sub = interval(0, animationFrameScheduler).pipe(
          untilDestroyed(this),
          throttleTime(100),
          map((_, idx) => idx % 8),
          map(i => i < 1),
        )
          .subscribe(on => this.blink(on));
      }
      this.redraw(this.ctx, this.options);
    }
  }

  ngAfterViewInit() {
    this.getImage();
  }

  private blink(blink: boolean) {
    if (this.values?.length > 0) {
      const ctx = this.ctx;
      const opt = this.options
      const x = opt.outerMargin + ((opt.blockAmount - 1) * (this.ww + opt.blockMargin));
      const y = this.h - this.hh - opt.outerMargin;
      const lastValue = this.values[opt.blockAmount - 1];
      let missing = false;

      ctx.lineWidth = 1;

      switch (lastValue) {
        case 'HEALTHY':
          ctx.fillStyle = opt.greenColor;
          missing = false;
          break;
        case 'FAILED':
          ctx.fillStyle = opt.redColor;
          missing = false;
          break;
        case 'DEGRADED':
          ctx.fillStyle = opt.orangeColor;
          missing = false;
          break;
        case 'UNKNOWN':
          ctx.lineWidth = 1;
          ctx.strokeStyle = opt.lightColor;
          missing = true;
          break;
      }

      if (blink) {
        ctx.lineWidth = 0;
        ctx.fillStyle = opt.blinkColor;
        ctx.fillRect(x - 1.5, y - 1.5, this.ww + 1, this.hh + 1);
      } else {
        if (missing) {
          ctx.lineWidth = 1;
          ctx.fillStyle = opt.bgColor;
          ctx.fillRect(x, y, this.ww - 1, this.hh - 1);
          ctx.strokeRect(x + .5, y + .5, this.ww - 2, this.hh - 2);
        } else {
          ctx.fillRect(x, y, this.ww - 1, this.hh - 1);
        }
      }
    }
  }

  private redraw(ctx: CanvasRenderingContext2D, opt: any) {
    if (ctx) {
      const opt = this.options;


      ctx.lineWidth = 1;

      // Determine width
      if (this.autoscale) {
        this.w = this.graph.nativeElement.clientWidth;
      } else {
        this.w = this.width ?? (opt.outerMargin * 2) + ((opt.blockWidth + opt.blockMargin) * this.values.length) - opt.blockMargin;
      }
      this.h = this.height ?? (opt.outerMargin * 2) + opt.blockHeight + Math.max(opt.fontSizeLabel + 4, opt.fontSizeState + 4);
      // this.h = this.height ?? graph.clientHeight;
      this.graph.nativeElement.style.height = this.h + 'px';
      this.canvas.nativeElement.width = this.w;
      this.canvas.nativeElement.height = this.h;

      ctx.clearRect(0, 0, this.w, this.h);
      // Render graph background
      ctx.fillStyle = opt.bgColor;
      ctx.fillRect(0, 0, this.w, this.h);


      const dividableWidth = this.w - (2 * opt.outerMargin) + opt.blockMargin;
      if (this.autoscale || this.width) {
        this.ww = (dividableWidth / opt.blockAmount) - opt.blockMargin;
      } else {
        this.ww = opt.blockWidth;
      }
      // const ww = this.width ? (dividableWidth / opt.blockAmount) - opt.blockMargin : opt.blockWidth;
      this.hh = opt.blockHeight;
      let missing = false;

      if (this.values?.length > 0) {
        // Blocks
        for (let i = 0; i < opt.blockAmount; i++) {
          switch (this.values[i]) {
            case 'HEALTHY':
              ctx.lineWidth = 0;
              ctx.fillStyle = opt.greenColor;
              missing = false;
              break;
            case 'FAILED':
              ctx.lineWidth = 0;
              ctx.fillStyle = opt.redColor;
              missing = false;
              break;
            case 'DEGRADED':
              ctx.lineWidth = 0;
              ctx.fillStyle = opt.orangeColor;
              missing = false;
              break;
            case 'UNKNOWN':
              ctx.lineWidth = 1;
              ctx.strokeStyle = opt.lightColor;
              ctx.fillStyle = opt.bgColor;
              missing = true;
              break;
          }

          const x = opt.outerMargin + (i * (this.ww + opt.blockMargin));
          const y = this.h - this.hh - opt.outerMargin;

          ctx.fillRect(x, y, this.ww - 1, this.hh - 1);
          if (missing) {
            ctx.strokeRect(x + .5, y + .5, this.ww - 2, this.hh - 2);
          }
        }
      }

      // Text: label
      ctx.font = `${opt.fontSizeLabel}px Arial`;
      ctx.fillStyle = opt.textColor;
      ctx.textAlign = 'left';
      const txtY = this.h - this.hh - (opt.outerMargin) - (2 * opt.blockMargin);
      ctx.fillText('status', opt.outerMargin, txtY);
      // Text: state
      ctx.font = `${opt.fontSizeState}px Arial`;
      ctx.textAlign = 'right';
      let state;

      if (this.values?.length > 0) {
        switch (this.values[this.values.length - 1]) {
          case 'HEALTHY':
            state = 'operational'
            break
          case 'FAILED':
            state = 'down';
            break;
          case 'DEGRADED':
            state = 'degraded';
            break;
          case 'UNKNOWN':
            state = '-';
            break;
        }
      }

      ctx.fillText(state, this.w - opt.outerMargin, txtY);

      // border
      ctx.strokeStyle = opt.borderColor;
      ctx.lineWidth = opt.borderWidth;
      ctx.strokeRect(.5, .5, this.w - 1, this.h - 1);
    }
  }

  private getImage() {
    if (this.canvas) {
      const canvas = this.canvas.nativeElement;
      this.ctx = canvas.getContext("2d");
      this.redraw(this.ctx, this.options);
    }
  }
}
