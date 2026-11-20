import { ChangeDetectorRef, inject, signal } from '@angular/core';
import { Api } from './api';
import { Auth } from './auth';
import { I18n } from './i18n';
import { hm, signedHm } from './util';

/** Shared helpers for page components: translation, error/success banner, busy flag. */
export abstract class Page {
  protected api = inject(Api);
  protected i18n = inject(I18n);
  protected auth = inject(Auth);
  protected cdr = inject(ChangeDetectorRef);
  error = signal('');
  ok = signal('');
  busy = signal(false);
  t = (key: string, params?: Record<string, unknown>) => this.i18n.t(key, params);
  msg = (m: { key: string; params?: Record<string, unknown> }) => this.i18n.msg(m);
  hm = hm;
  signed = signedHm;
  d = (iso: string, o?: Intl.DateTimeFormatOptions) => this.i18n.date(iso, o);

  /** Runs an action, shows translated errors, returns undefined on failure. */
  async run<T>(fn: () => Promise<T>, okKey?: string): Promise<T | undefined> {
    this.error.set(''); this.busy.set(true);
    if (okKey) this.ok.set('');
    try {
      const r = await fn();
      if (okKey) { this.ok.set(this.t(okKey)); setTimeout(() => { this.ok.set(''); this.cdr.markForCheck(); }, 6000); }
      return r;
    } catch (e) {
      this.error.set(this.api.message(e));
      return undefined;
    } finally {
      this.busy.set(false);
      this.cdr.markForCheck();
    }
  }
}
