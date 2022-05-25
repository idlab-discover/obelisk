import { Component, ElementRef, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LicenseService, ToastService } from '@core/services';
import { ConfirmService } from '@core/services/confirm.service';
import { ObeliskService } from '@core/services/obelisk.service';
import { CodemirrorComponent } from '@ctrl/ngx-codemirror';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { UntilDestroy, untilDestroyed } from '@ngneat/until-destroy';
import { UploadModalComponent } from '@shared/modals/upload/upload-modal.component';
import { ResourceType } from '@shared/model';
import { Dataset } from '@shared/model/types';
import { ResourcePipe } from '@shared/pipes/resource.pipe';
import { EMPTY, of, throwError } from 'rxjs';
import { catchError, flatMap, map } from 'rxjs/operators';


enum ViewMode { EDIT, PREVIEW, BOTH }

@UntilDestroy()
@Component({
  selector: 'app-edit',
  templateUrl: './edit.component.html',
  styleUrls: ['./edit.component.scss'],
  providers: [ResourcePipe],
  encapsulation: ViewEncapsulation.None
})
export class EditComponent implements OnInit {
  dataset: Partial<Dataset>;
  generalForm: FormGroup;
  metadataForm: FormGroup;
  customizationForm: FormGroup;
  readmeForm: FormGroup;
  originsForm: FormGroup;

  @ViewChild('thumbnail') thumbnail: ElementRef<HTMLImageElement>;
  @ViewChild('cm') cm: CodemirrorComponent;
  viewMode: ViewMode = ViewMode.EDIT;
  mdSrc: string;

  collapse: Map<Section, boolean> = new Map([
    ['general', true],
    ['metadata', true],
    ['customization', true],
    ['readme', true],
    ['advanced', true]
  ]);

  thumbnailDsId: string;
  bannerDsId: string;

  options: CodeMirror.EditorConfiguration = {
    lineNumbers: false,
    lineWrapping: true,
    theme: 'eclipse',
    mode: { name: 'markdown' },
    smartIndent: true,
    autoRefresh: true
  }

  licenses: string[] = [];

  private defaultGeneralValues: any = null;

  constructor(
    private modal: NgbModal,
    private obelisk: ObeliskService,
    private route: ActivatedRoute,
    private toast: ToastService,
    private confirm: ConfirmService,
    private resourcePipe: ResourcePipe,
    private router: Router,
    private licenseService: LicenseService,
    fb: FormBuilder
  ) {
    this.generalForm = fb.group({
      name: ['', Validators.required],
      published: false,
      openData: false,
      description: ['', Validators.maxLength(255)]
    });
    this.metadataForm = fb.group({
      keywords: [],
      license: [],
      contactPoint: [],
      publisherName: [],
      publisherHomepage: []
    });
    this.customizationForm = fb.group({});
    this.readmeForm = fb.group({ mdSrc: [] });
    this.readmeForm.get('mdSrc').valueChanges.subscribe(val => this.mdSrc = val);
    this.licenses = licenseService.listNames();
  }

  ngOnInit(): void {
    this.route.data.pipe(
      untilDestroyed(this),
      map(data => data.dataset)
    )
      .subscribe(ds => {
        this.loadFromDataset(ds);
      });

  }

  editThumbnail() {
    const config = {
      title: 'Upload thumbnail image',
      datasetId: this.dataset.id,
      type: 'thumbnail' as ResourceType
    };
    const ref = this.modal.open(UploadModalComponent, { size: 'lg', backdrop: 'static' });
    ref.componentInstance.init(config);
    ref.result.then(_ => this.refreshThumbnail());
  }

  private refreshThumbnail() {
    this.thumbnailDsId = '__';
    setTimeout(() => this.thumbnailDsId = this.dataset.id, 0);
  }

  editBanner() {
    const config = {
      title: 'Upload banner image',
      datasetId: this.dataset.id,
      type: 'banner' as ResourceType
    };
    const ref = this.modal.open(UploadModalComponent, { size: 'lg', backdrop: 'static' });
    ref.componentInstance.init(config);
    ref.result.then(_ => this.refreshBanner());
  }

  private refreshBanner() {
    this.bannerDsId = '';
    setTimeout(() => this.bannerDsId = this.dataset.id, 0);
  }

  removeDataset() {
    this.confirm.areYouSureThen(
      "Do you really want to delete this dataset? <br><b>This is an irreversibly operation!</b>",
      this.obelisk.archiveDataset(this.dataset.id))
      .subscribe(res => {
        if (res.responseCode === 'SUCCESS') {
          this.refreshData();
          this.openOnly('general');
          this.toast.show("Dataset deleted");
          this.router.navigate(['my', 'datasets']);
        }
        else {
          // TODO: Error handling
          this.toast.error(`Error removing dataset!`);
        }
      }
      )
  }

  saveGeneral() {
    const input = this.generalForm.value;
    if (this.generalForm.valid) {
      this.obelisk.updateDataset(this.dataset.id, input).subscribe(
        res => {
          if (res?.responseCode === 'SUCCESS') {
            this.refreshData();
            this.openOnly('general');
            this.toast.success("Changes saved!");
          }
          else {
            // TODO: Error handling
            this.toast.error(`Error saving dataset!`);
          }
        })
    }
  }

  resetGeneral() {
    this.generalForm.reset(this.defaultGeneralValues);
  }

  saveMetadata() {
    const form = this.metadataForm;
    const name = form.get('publisherName').value ?? null;
    const homepage = form.get('publisherHomepage').value ?? null;
    const publisher = name || homepage ? { name, homepage } : null;
    const license = form.get('license').value ?? null;
    let keywords = form.get('keywords').value?.trim();
    const metadata = {
      keywords: keywords?.length > 0 ? keywords?.split(' ') : [],
      license: license ? this.licenseService.getUri(license) : null,
      contactPoint: form.get('contactPoint').value ?? null,
      publisher
    }

    this.obelisk.updateDataset(this.dataset.id, metadata).subscribe(res => {
      if (res.responseCode === 'SUCCESS') {
        this.refreshData();
        this.openOnly('metadata');
        this.toast.success("Changes saved!");
      }
      else {
        // TODO: Error handling
        this.toast.error(`Error saving metadata!`);
      }
    })
  }

  saveCustomization() {
    // this.obelisk.editDatasetDescription(this.dataset.id, this.customizationForm.get('description').value)
    //   .subscribe(res => {
    //     if (res.responseCode === 'SUCCESS') {
    //       this.refreshData();
    //       this.openOnly('customization');
    //       this.toast.success("Changes saved!");
    //     }
    //     else {
    //       // TODO: Error handling
    //       this.toast.error(`Error saving dataset!`);
    //     }
    //   })
  }

  saveReadme() {
    let blob = new Blob([this.readmeForm.get('mdSrc').value], { type: 'text/plain' });
    var ajaxData = new FormData();
    ajaxData.append('upload_file', blob, 'readme.md');
    this.obelisk.uploadResource(ajaxData, this.dataset.id, 'readme', true).subscribe(e => this.refreshData())
  }

  private refreshData() {
    this.obelisk.getDatasetSettings(this.dataset.id).subscribe(ds => this.loadFromDataset(ds));
  }

  private loadFromDataset(ds: Dataset) {
    this.dataset = ds;
    this.thumbnailDsId = ds.id;
    this.bannerDsId = ds.id;
    this.mdSrc = '';
    this.resourcePipe.transform(ds.id, 'readme').then(str => {
      this.readmeForm.get('mdSrc').setValue(str);
      // this.mdSrc = str;
      this.cm.codeMirror.getDoc().setValue(str);
    });
    setTimeout(() => {
      this.cm.codeMirror.refresh()
    }, 200);
    this.generalForm.setValue({
      name: ds.name,
      published: ds.published,
      openData: ds.openData,
      description: ds.description
    });
    this.defaultGeneralValues = { ...this.generalForm.value };

    this.metadataForm.reset({
      keywords: ds.keywords?.join(' '),
      license: this.licenseService.getName(ds.license),
      contactPoint: ds.contactPoint,
      publisherName: ds.publisher?.name,
      publisherHomepage: ds.publisher?.homepage
    });
  }

  resetReadme() {
    this.confirm.areYouSure('Are you sure you want to reset the readme content? It will immediatly be saved!')
      .pipe(
        flatMap(ok => !ok ? EMPTY : this.obelisk.deleteReadMe(this.dataset.id)),
        catchError(err => err.status === 404 ? of(true) : throwError(err)),
      )
      .subscribe(_ => {
        this.refreshData();
        this.toast.success("Readme reset to default!");
      });
  }

  toggleViewMode() {
    this.viewMode = (++this.viewMode % 3);
  }

  openOnly(section: Section) {
    this.collapse.forEach((_, key) => {
      this.collapse.set(key, key != section);
    });
  }

  toggle(event: MouseEvent, section: Section, accordion = false) {
    event.stopPropagation();
    const nowClosed = this.collapse.get(section);
    if (nowClosed && accordion) {
      this.openOnly(section);
    } else {
      this.collapse.set(section, !nowClosed);
    }
  }

  get hideEditor() {
    return this.viewMode === ViewMode.PREVIEW;
  }

  get hidePreview() {
    return this.viewMode === ViewMode.EDIT;
  }

  get viewClass() {
    return {
      view: true,
      single: this.viewMode !== ViewMode.BOTH,
      dual: this.viewMode === ViewMode.BOTH
    }
  }

  get collapseGeneral() {
    return this.collapse.get('general');
  }

  get collapseMetadata() {
    return this.collapse.get('metadata');
  }

  get collapseCustomization() {
    return this.collapse.get('customization');
  }

  get collapseReadme() {
    return this.collapse.get('readme');
  }

  get collapseAdvanced() {
    return this.collapse.get('advanced');
  }

  get desc() {
    return this.generalForm.get('description')
  }
}

type Section = 'general' | 'metadata' | 'customization' | 'readme' | 'advanced';
