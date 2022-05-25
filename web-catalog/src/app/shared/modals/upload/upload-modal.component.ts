import { AfterViewInit, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { ObeliskService } from '@core/services/obelisk.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ResourceType } from '@shared/model/types';

@Component({
  selector: 'app-upload-modal',
  templateUrl: './upload-modal.component.html',
  styleUrls: ['./upload-modal.component.scss']
})
export class UploadModalComponent implements OnInit, AfterViewInit {
  @ViewChild('dropspace') dropspace: ElementRef<HTMLDivElement>
  @ViewChild('myCanvas') canvas: ElementRef<HTMLCanvasElement>

  title: string;
  datasetId: string;
  type: ResourceType;
  state: State = 'SELECT';

  private cropCanvas: HTMLCanvasElement;
  private cropCtx: CanvasRenderingContext2D;
  private vals: Partial<CalcVals> = {};
  private movingImage: boolean = false;
  private file: File;

  private readonly fileUploadValue: string = '<fa-icon icon="upload" class="mr-2"></fa-icon> <em>Choose a file</em> or <em>drag one here</em>...';
  constructor(public activeModal: NgbActiveModal, private obelisk: ObeliskService) { }

  ngOnInit() {
    switch (this.type) {
      case 'banner':
        this.vals.rectWidth = 1920;
        this.vals.rectHeight = 144;
        break;
      case 'thumbnail': {
        this.vals.rectWidth = 265;
        this.vals.rectHeight = 108;
        break;
      }
    }
  }

  ngAfterViewInit(): void {
    this.dropspace.nativeElement.addEventListener("dragenter", this.dragenter, false);
    this.dropspace.nativeElement.addEventListener("dragover", this.dragenter, false);
    this.dropspace.nativeElement.addEventListener("dragleave", this.dragleave, false);
    this.dropspace.nativeElement.addEventListener("dragend", this.dragleave, false);
    this.dropspace.nativeElement.addEventListener("drop", this.drop, false);
    const input = document.getElementById('file');
    input.addEventListener('change', (e: Event) => {
      const input = document.getElementById('file') as HTMLInputElement;
      this.file = input.files[0];
      this.showFile((e.target as HTMLInputElement).value.split('\\').pop());
    });
  }

  init(config: any) {
    this.title = config.title;
    this.datasetId = config.datasetId;
    this.type = config.type;
  }

  loadCropping() {
    this.state = 'CROP';
    this.loadPreview(this.file);
  }

  save() {
    const canvas = document.createElement('canvas');
    canvas.width = this.vals.rectWidth;
    canvas.height = this.vals.rectHeight;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(this.vals.loadedImg, this.vals.rectX / this.vals.ratio, this.vals.rectY / this.vals.ratio, this.vals.rectWidth*this.vals.scale, this.vals.rectHeight*this.vals.scale, 0, 0, canvas.width, canvas.height);
    canvas.toBlob(blob => {
      var ajaxData = new FormData();
      ajaxData.append('upload_file', blob, this.file.name.substring(0, this.file.name.lastIndexOf('.')) + '.png');
      this.obelisk.uploadResource(ajaxData, this.datasetId, this.type, true).subscribe(ok => {
        this.activeModal.close();
      });
    }, 'image/png')
  }

  changeScale(e) {
    let newVal = parseFloat(e.target.value);
    if (newVal < this.vals.scaleMin) {
      newVal = this.vals.scaleMin;
    }
    if (newVal > this.vals.scaleMax) {
      newVal = this.vals.scaleMax;
    }
    this.vals.scale = newVal;
    redrawSelectionRectangle(this.cropCtx, this.vals, 0, 0);
  }

  get scale() {
    return this.vals.scale;
  }

  get min() {
    return this.vals.scaleMin;
  }

  get max() {
    return Math.ceil(this.vals.scaleMax);
  }

  get step() {
    return this.vals.scaleStep;
  }

  private dragenter = (e: DragEvent) => {
    e.stopPropagation();
    e.preventDefault();
    this.dropspace.nativeElement.classList.add('is-dragover');
  }
  private dragleave = (e: DragEvent) => {
    e.stopPropagation();
    e.preventDefault();
    this.dropspace.nativeElement.classList.remove('is-dragover');
  }

  private drop = (e: DragEvent) => {
    e.stopPropagation();
    e.preventDefault();
    this.dropspace.nativeElement.classList.remove('is-dragover');

    let files = [];
    if (e.dataTransfer.items) {
      // Use DataTransferItemList interface to access the file(s)
      for (var i = 0; i < e.dataTransfer.items.length; i++) {
        // If dropped items aren't files, reject them
        if (e.dataTransfer.items[i].kind === 'file') {
          files.push(e.dataTransfer.items[i].getAsFile());
        }
      }
    } else {
      // Use DataTransfer interface to access the file(s)
      for (var i = 0; i < e.dataTransfer.files.length; i++) {
        files.push(e.dataTransfer.files.item(i));
      }
    }

    this.handleFiles(files);
  }

  private showFile = (fileName: string | null) => {
    this.state = (fileName == null) ? 'SELECT' : 'SELECTED';
    const input = document.getElementById('file');
    if (fileName) {
      input.nextElementSibling.innerHTML = fileName;
    } else {
      input.nextElementSibling.innerHTML = this.fileUploadValue;
    }
  }

  private handleFiles = (files: File[]) => {
    if (files.length > 1) {
      console.error('more than 1 file');
      this.showFile(null);
      return;
    }

    const file = files[0];

    if (!file.type.startsWith('image/')) {
      console.error('no image file');
      this.showFile(null);
      return;
    }
    this.file = file;
    this.showFile(file.name);
  }

  private loadCanvas() {
    this.cropCanvas = document.getElementById('myCanvas') as HTMLCanvasElement;
    this.cropCtx = this.cropCanvas.getContext("2d");
    this.vals.frameWidth = this.cropCanvas.width;
    this.vals.frameHeight = this.cropCanvas.height;
    this.vals.drawWidth = this.vals.loadedImg.naturalWidth;
    this.vals.drawHeight = this.vals.loadedImg.naturalHeight;
    if (this.vals.drawWidth > this.vals.drawHeight) { // landscape
      this.vals.ratio = this.vals.frameWidth / this.vals.loadedImg.naturalWidth;
      this.vals.drawHeight *= this.vals.ratio;
      this.vals.drawWidth = this.vals.frameWidth;
      this.cropCanvas.height = this.vals.drawHeight;
    } else { // portrait
      this.vals.ratio = this.vals.frameHeight / this.vals.loadedImg.naturalHeight;
      this.vals.drawWidth *= this.vals.ratio;
      this.vals.drawHeight = this.vals.frameHeight;
      this.cropCanvas.width = this.vals.drawWidth;
    }
    this.vals.rectWidthScaled = Math.ceil(this.vals.rectWidth * this.vals.ratio);
    this.vals.rectHeightScaled = Math.ceil(this.vals.rectHeight * this.vals.ratio);
    this.vals.scaleMax = Math.min(this.vals.frameWidth / this.vals.rectWidthScaled, this.vals.frameHeight / this.vals.rectHeightScaled);
    this.vals.rectX = 0;
    this.vals.rectY = 0;
    this.vals.scale = 1.0;
    this.vals.scaleMin = 0.1;
    this.vals.scaleStep = 0.1;
  }

  private loadPreview(file: Blob) {
    const that = this;
    let reader = new FileReader();
    reader.onloadend = () => {
      that.vals.loadedImg = new Image();
      that.vals.loadedImg.src = reader.result as string;
      that.vals.loadedImg.onload = function () {
        that.loadCanvas();
        const can = that.cropCanvas;
        can.onmousedown = (e: MouseEvent) => {
          if (isInBounds(e.offsetX, e.offsetY, that.vals)) {
            that.movingImage = true;

            if (window.onmouseup == null || window.onmouseup.length === 0) {
              window.onmouseup = (e) => {
                that.movingImage = false;
                window.onmouseup = null;
                can.style.cursor = 'default';
              }
            }
          }
        };
        can.onmouseup = (e) => {
          that.movingImage = false;
          can.style.cursor = 'default';
        };
        can.onmousemove = (e: MouseEvent) => {
          if (that.movingImage) {
            can.style.cursor = 'move';
            redrawSelectionRectangle(that.cropCtx, that.vals, e.movementX, e.movementY);
          } else if (isInBounds(e.offsetX, e.offsetY, that.vals)) {
            can.style.cursor = 'move';
          } else {
            can.style.cursor = 'default';
          }
        };

        can.onwheel = (e: WheelEvent) => {
          const delta = (e.deltaY * 0.001);
          const newVal = that.vals.scale + delta;
          that.changeScale({ target: { value: newVal } });
        };
        that.cropCtx.drawImage(that.vals.loadedImg, 0, 0, that.vals.drawWidth, that.vals.drawHeight);
        drawShadedStroke(that.cropCtx, that.vals);

      }
    }
    reader.readAsDataURL(file);
  }

}

function redrawSelectionRectangle(ctx: CanvasRenderingContext2D, vals: any, dx: number, dy: number) {
  incrementBounded(vals, dx, dy);
  ctx.drawImage(vals.loadedImg, 0, 0, vals.drawWidth, vals.drawHeight);
  drawShadedStroke(ctx, vals);
}


function drawShadedStroke(ctx: CanvasRenderingContext2D, vals: any) {
  const drawWidth = vals.drawWidth;
  const drawHeight = vals.drawHeight;
  const rectX = vals.rectX;
  const rectY = vals.rectY;
  const rectWidthScaled = Math.ceil(vals.rectWidthScaled * vals.scale);
  const rectHeightScaled = Math.ceil(vals.rectHeightScaled * vals.scale);
  ctx.fillStyle = "rgba(0,0,0,0.4)";
  ctx.fillRect(0, 0, drawWidth, rectY);
  ctx.fillRect(0, rectY + rectHeightScaled, drawWidth, drawHeight - (rectY + rectHeightScaled));
  ctx.fillRect(0, rectY, rectX, rectHeightScaled);
  ctx.fillRect(rectX + rectWidthScaled, rectY, drawWidth - (rectX + rectWidthScaled), rectHeightScaled);
  ctx.strokeStyle = "#ffcc00";
  ctx.strokeRect(rectX, rectY, rectWidthScaled, rectHeightScaled);

}

function isInBounds(x: number, y: number, vals: any) {
  const originX = vals.rectX;
  const originY = vals.rectY;
  const width = vals.rectWidthScaled * vals.scale;
  const height = vals.rectHeightScaled * vals.scale;
  return (originX <= x && x <= originX + width)
    && (originY <= y && y <= originY + height);
}

function incrementBounded(vals: any, dx: number, dy: number) {
  let x = vals.rectX;
  let y = vals.rectY;
  const maxX = vals.drawWidth - (vals.rectWidthScaled * vals.scale);
  const maxY = vals.drawHeight - ((vals.rectHeightScaled * vals.scale) + 1);
  x += dx;
  y += dy;
  x = Math.max(0, x);
  x = Math.min(maxX, x);
  y = Math.max(0, y);
  y = Math.min(maxY, y);
  vals.rectX = x;
  vals.rectY = y;
}

type State = 'SELECT' | 'SELECTED' | 'CROP';

interface CalcVals {
  frameWidth: number;
  frameHeight: number;
  drawWidth: number;
  drawHeight: number;
  ratio: number;
  rectX: number;
  rectY: number;
  rectWidth: number;
  rectHeight: number;
  rectWidthScaled: number;
  rectHeightScaled: number;
  scale: number;
  scaleMax: number;
  scaleMin: number;
  scaleStep: number;
  loadedImg: HTMLImageElement;
}