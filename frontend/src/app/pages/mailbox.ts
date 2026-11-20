import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Page } from '../core/page';

interface Mail { id: number; sentAt: string; recipient: string; subject: string; body: string; lang: string; }

/** Local installation: emails are captured here instead of being delivered. */
@Component({
  selector: 'app-mailbox', imports: [RouterLink],
  template: `
  <div>
    <div class="row between"><h1>✉ {{ t('mailbox.title') }}</h1>
      <span class="row"><button class="btn secondary" type="button" (click)="load()">{{ t('common.refresh') }}</button>
      <a class="btn secondary" routerLink="/">{{ t('mailbox.back') }}</a></span></div>
    <p class="muted">{{ t('mailbox.help') }}</p>
    @if (error()) { <div class="alert error">{{ error() }}</div> }
    @for (m of mails(); track m.id) {
      <article class="card">
        <div class="row between"><strong>{{ m.subject }}</strong><span class="muted small">{{ i18n.dateTime(m.sentAt) }} · {{ m.lang.toUpperCase() }}</span></div>
        <div class="muted small">{{ t('mailbox.to') }}: {{ m.recipient }}</div>
        <pre style="white-space:pre-wrap;font:inherit;margin:.6rem 0 0" [innerHTML]="linkify(m.body)"></pre>
      </article>
    } @empty { <p class="muted">{{ t('mailbox.empty') }}</p> }
  </div>`
})
export class MailboxPage extends Page {
  mails = signal<Mail[]>([]);
  constructor() { super(); void this.load(); }
  async load(): Promise<void> { const r = await this.run(() => this.api.get<Mail[]>('/api/dev/mails')); if (r) this.mails.set(r); }
  linkify(s: string): string {
    const esc = s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    return esc.replace(/(https?:\/\/[^\s]+)/g, '<a href="$1">$1</a>');
  }
}
