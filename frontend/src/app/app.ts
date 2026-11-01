import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ToastModule } from 'primeng/toast';

/** Raíz: el router, y los avisos y confirmaciones compartidos por toda la aplicación. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastModule, ConfirmDialogModule],
  template: `
    <router-outlet />
    <p-toast position="top-right" />
    <p-confirmdialog />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {}
