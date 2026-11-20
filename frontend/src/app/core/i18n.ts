import { Injectable, signal } from '@angular/core';
import { DE } from './i18n-de';
import { EN } from './i18n-en';

export type Lang = 'de' | 'en';

/** Minimal translation service: German and English (D-12, I-02). Reading the language signal makes templates reactive. */
@Injectable({ providedIn: 'root' })
export class I18n {
  readonly lang = signal<Lang>(this.initial());
  private dict: Record<Lang, Record<string, string>> = { de: DE, en: EN };

  private initial(): Lang {
    try { const s = localStorage.getItem('lang'); if (s === 'de' || s === 'en') return s; } catch { /* ignore */ }
    return navigator.language?.startsWith('en') ? 'en' : 'de';
  }

  set(l: Lang): void {
    this.lang.set(l);
    try { localStorage.setItem('lang', l); } catch { /* ignore */ }
    document.documentElement.lang = l;
  }

  t(key: string, params?: Record<string, unknown>): string {
    const l = this.lang();
    let s = this.dict[l][key] ?? this.dict.de[key] ?? key;
    if (params) for (const [k, v] of Object.entries(params)) s = s.split('{' + k + '}').join(String(v));
    return s;
  }

  /** Server-side message {key, params}; duration params (minutes) are formatted as h:mm. */
  msg(m: { key: string; params?: Record<string, unknown> }): string {
    const p: Record<string, unknown> = { ...(m.params ?? {}) };
    for (const k of ['missing', 'work', 'project', 'break']) if (typeof p[k] === 'number') {
      const v = p[k] as number;
      p[k] = `${Math.floor(v / 60)}:${String(v % 60).padStart(2, '0')}`;
    }
    return this.t('msg.' + m.key, p);
  }

  locale(): string { return this.lang() === 'de' ? 'de-DE' : 'en-GB'; }

  date(iso: string, opts: Intl.DateTimeFormatOptions = { weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric' }): string {
    const [y, m, d] = iso.substring(0, 10).split('-').map(Number);
    return new Intl.DateTimeFormat(this.locale(), opts).format(new Date(y, m - 1, d));
  }
  dateTime(iso: string): string {
    return new Intl.DateTimeFormat(this.locale(), { dateStyle: 'short', timeStyle: 'short' }).format(new Date(iso));
  }
}
