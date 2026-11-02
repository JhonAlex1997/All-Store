import { HttpClient, HttpContext } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';

import { SIN_AVISO } from '../http/errores.interceptor';

interface Marca {
  nombre: string;
  configurada: boolean;
  /** null si la tienda no tiene logo. */
  logoVersion?: number | null;
}

const CLAVE = 'allstore.marca';
const SIN_CONFIGURAR = 'Mi tienda';

/**
 * Nombre de la tienda (nombre comercial de la empresa, o su razón social). Se muestra en el login,
 * el menú y el título de la pestaña; se cambia desde los datos de la empresa, sin tocar código.
 */
@Injectable({ providedIn: 'root' })
export class MarcaService {
  private readonly http = inject(HttpClient);

  /** Arranca con el último nombre conocido para que el login no parpadee mientras responde la API. */
  private readonly marca = signal<Marca>(leerGuardada() ?? { nombre: SIN_CONFIGURAR, configurada: true });

  readonly nombre = computed(() => this.marca().nombre);
  readonly configurada = computed(() => this.marca().configurada);

  /** URL del logo con su versión (cambia al subir otro, así no se muestra el anterior), o null. */
  readonly logoUrl = computed(() => {
    const v = this.marca().logoVersion;
    return v ? `/api/publico/marca/logo?v=${v}` : null;
  });

  /** Palabras iniciales y última, para resaltar la última: "KISSME" + "JEANS" (cuando no hay logo). */
  readonly partes = computed(() => {
    const palabras = this.nombre().trim().split(/\s+/);
    return palabras.length > 1
      ? { inicio: palabras.slice(0, -1).join(' '), fin: palabras[palabras.length - 1] }
      : { inicio: palabras[0] ?? '', fin: '' };
  });

  readonly inicial = computed(() => (this.nombre().trim()[0] ?? '?').toUpperCase());

  /** La llama el arranque de la aplicación. Si la API no responde, se queda con el último nombre conocido. */
  async cargar(): Promise<void> {
    try {
      // Ruta pública (sin sesión). Cuando se regenere el cliente (npm run api) existirá como MarcaControllerService.
      // Máximo 3 s y sin aviso de error: el arranque no debe quedarse esperando ni mostrar fallas por esto.
      const context = new HttpContext().set(SIN_AVISO, true);
      const m = await firstValueFrom(this.http.get<Marca>('/api/publico/marca', { context }).pipe(timeout(3000)));
      if (m?.nombre) {
        this.marca.set(m);
        guardar(m);
      }
    } catch {
      // sin conexión: se mantiene el nombre guardado
    }
  }
}

function leerGuardada(): Marca | null {
  try {
    const texto = localStorage.getItem(CLAVE);
    if (!texto) {
      return null;
    }
    // Versiones anteriores guardaban solo el nombre como texto.
    return texto.startsWith('{') ? (JSON.parse(texto) as Marca) : { nombre: texto, configurada: true };
  } catch {
    return null;
  }
}

function guardar(marca: Marca): void {
  try {
    localStorage.setItem(CLAVE, JSON.stringify(marca));
  } catch {
    // sin almacenamiento: se vuelve a pedir en cada carga
  }
}
