import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TabsModule } from 'primeng/tabs';

import { DatosEmpresa } from './datos-empresa';
import { Establecimientos } from './establecimientos';
import { LogoEmpresa } from './logo-empresa';
import { Series } from './series';

/** Empresa y series: los datos del emisor que van en cada comprobante, el logo, los locales y las series. */
@Component({
  selector: 'as-empresa-pagina',
  imports: [TabsModule, DatosEmpresa, LogoEmpresa, Establecimientos, Series],
  template: `
    <div class="as-pagina-titulo">
      <div>
        <h1>Empresa y series</h1>
        <div class="as-sub">Los datos que salen en tus boletas y facturas, tu logo, tus locales y la numeración.</div>
      </div>
    </div>

    <p-tabs [(value)]="pestana">
      <p-tablist>
        <p-tab value="datos"><i class="pi pi-building"></i> Datos</p-tab>
        <p-tab value="logo"><i class="pi pi-image"></i> Logo</p-tab>
        <p-tab value="locales"><i class="pi pi-map-marker"></i> Locales</p-tab>
        <p-tab value="series"><i class="pi pi-hashtag"></i> Series</p-tab>
      </p-tablist>
      <p-tabpanels>
        <p-tabpanel value="datos"><as-datos-empresa /></p-tabpanel>
        <p-tabpanel value="logo"><as-logo-empresa /></p-tabpanel>
        <p-tabpanel value="locales"><as-establecimientos /></p-tabpanel>
        <p-tabpanel value="series"><as-series /></p-tabpanel>
      </p-tabpanels>
    </p-tabs>
  `,
  styles: `
    p-tab i { margin-right: .4rem; }
    :host ::ng-deep .p-tabpanels { padding: 1.25rem 0 0; background: transparent; }
    :host ::ng-deep .p-tablist-tab-list { background: transparent; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmpresaPagina {
  protected readonly pestana = signal<string | number>('datos');
}
