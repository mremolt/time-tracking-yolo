import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { I18n } from './i18n';

/** Thin HTTP wrapper: promise based, translated error messages. */
@Injectable({ providedIn: 'root' })
export class Api {
  private http = inject(HttpClient);
  private i18n = inject(I18n);

  private params(p?: Record<string, unknown>): HttpParams {
    let hp = new HttpParams();
    for (const [k, v] of Object.entries(p ?? {})) {
      if (v === undefined || v === null || v === '') continue;
      if (Array.isArray(v)) v.forEach(x => (hp = hp.append(k, String(x))));
      else hp = hp.set(k, String(v));
    }
    return hp;
  }
  get<T>(url: string, p?: Record<string, unknown>): Promise<T> { return firstValueFrom(this.http.get<T>(url, { params: this.params(p) })); }
  post<T = void>(url: string, body?: unknown): Promise<T> { return firstValueFrom(this.http.post<T>(url, body ?? {})); }
  put<T = void>(url: string, body?: unknown): Promise<T> { return firstValueFrom(this.http.put<T>(url, body ?? {})); }
  delete<T = void>(url: string, p?: Record<string, unknown>): Promise<T> { return firstValueFrom(this.http.delete<T>(url, { params: this.params(p) })); }
  upload<T>(url: string, file: File): Promise<T> {
    const f = new FormData(); f.append('file', file);
    return firstValueFrom(this.http.post<T>(url, f));
  }
  /** URL for a browser download (same origin, session cookie). */
  url(path: string, p?: Record<string, unknown>): string {
    const q = this.params(p).toString();
    return q ? `${path}?${q}` : path;
  }
  /** Translated message for an error returned by the server. */
  message(e: unknown): string {
    if (e instanceof HttpErrorResponse) {
      const code = e.error?.code as string | undefined;
      if (code) {
        const key = 'err.' + code;
        const txt = this.i18n.t(key, e.error?.params ?? {});
        return txt === key ? this.i18n.t('err.UNKNOWN') + ' (' + code + ')' : txt;
      }
      if (e.status === 0) return this.i18n.t('err.NETWORK');
      if (e.status === 403) return this.i18n.t('err.FORBIDDEN');
    }
    return this.i18n.t('err.UNKNOWN');
  }
}
