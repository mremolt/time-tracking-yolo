import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Page } from '../core/page';
import { Simple } from '../core/models';

interface Lock { id: number; year: number; month: number; userId?: number; userName?: string; active: boolean; lockedAt: string; unlockedAt?: string; }

@Component({
  selector: 'app-admin-periods', imports: [FormsModule],
  template: `
  <h1>{{ t('lock.title') }}</h1>
  <p class="muted">{{ t('lock.help') }}</p>
  @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
  @if (ok()) { <div class="alert ok" role="status">{{ ok() }}</div> }
  <section class="card">
    <form (ngSubmit)="lock()" class="row">
      <label>{{ t('rep.month') }}<input type="month" name="m" [(ngModel)]="month" required></label>
      <label>{{ t('lock.scope') }}<select name="u" [(ngModel)]="userId"><option [ngValue]="null">{{ t('lock.all') }}</option>@for (u of users(); track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }</select></label>
      <button class="btn" type="submit" [disabled]="busy()">🔒 {{ t('lock.lock') }}</button>
    </form>
  </section>
  <div class="card tablewrap"><table>
    <thead><tr><th>{{ t('rep.month') }}</th><th>{{ t('lock.scope') }}</th><th>{{ t('abs.status') }}</th><th>{{ t('audit.when') }}</th><th></th></tr></thead>
    <tbody>@for (l of locks(); track l.id) {
      <tr [class.inactive]="!l.active"><td>{{ l.year }}-{{ pad(l.month) }}</td><td>{{ l.userName || t('lock.all') }}</td>
        <td>@if (l.active) { <span class="chip warn">🔒 {{ t('lock.locked') }}</span> } @else { <span class="chip">{{ t('lock.unlocked') }}</span> }</td>
        <td>{{ i18n.dateTime(l.lockedAt) }}</td>
        <td>@if (l.active) { <button class="btn small secondary" type="button" (click)="unlock(l)">{{ t('lock.unlock') }}</button> }</td></tr>
    } @empty { <tr><td colspan="5" class="muted">{{ t('common.none') }}</td></tr> }</tbody>
  </table></div>`
})
export class AdminPeriodsPage extends Page {
  locks = signal<Lock[]>([]); users = signal<Simple[]>([]);
  userId: number | null = null;
  month = (() => { const d = new Date(); d.setMonth(d.getMonth() - 1); return d.toISOString().substring(0, 7); })();
  constructor() { super(); void this.init(); }
  pad(n: number): string { return String(n).padStart(2, '0'); }
  async init(): Promise<void> { this.users.set(await this.api.get<Simple[]>('/api/lookups/users')); await this.load(); }
  async load(): Promise<void> { const r = await this.run(() => this.api.get<Lock[]>('/api/admin/locks')); if (r) this.locks.set(r); }
  async lock(): Promise<void> {
    const [y, m] = this.month.split('-').map(Number);
    if (!confirm(this.t('lock.confirm', { month: this.month }))) return;
    if (await this.run(() => this.api.post('/api/admin/locks', { year: y, month: m, userId: this.userId }).then(() => true), 'lock.done')) await this.load();
  }
  async unlock(l: Lock): Promise<void> {
    if (!confirm(this.t('lock.confirmUnlock', { month: `${l.year}-${this.pad(l.month)}` }))) return;
    if (await this.run(() => this.api.post('/api/admin/locks/' + l.id + '/unlock').then(() => true), 'lock.unlockDone')) await this.load();
  }
}
