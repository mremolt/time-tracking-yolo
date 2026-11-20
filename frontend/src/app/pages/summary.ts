import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Page } from '../core/page';
import { monthEnd, monthStart, today, parseIso, iso } from '../core/util';

interface Summary {
  targetMinutes: number; workMinutes: number; projectMinutes: number; deltaMinutes: number; balanceMinutes: number; openingBalanceMinutes: number;
  weeks: { week: string; targetMinutes: number; workMinutes: number; projectMinutes: number }[];
  byProject: { name: string; minutes: number }[];
}

@Component({
  selector: 'app-summary', imports: [FormsModule],
  template: `
  <div class="row between">
    <h1>{{ t('sum.title') }}</h1>
    <div class="row">
      <button class="btn secondary" type="button" (click)="shift(-1)" [attr.aria-label]="t('common.prev')">‹</button>
      <strong>{{ d(month(), {month:'long', year:'numeric'}) }}</strong>
      <button class="btn secondary" type="button" (click)="shift(1)" [attr.aria-label]="t('common.next')">›</button>
    </div>
  </div>
  @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
  @if (s(); as m) {
    <div class="kpis">
      <div class="kpi"><b>{{ hm(m.targetMinutes) }}</b><span>{{ t('day.target') }}</span></div>
      <div class="kpi"><b>{{ hm(m.workMinutes) }}</b><span>{{ t('day.work') }}</span></div>
      <div class="kpi"><b>{{ hm(m.projectMinutes) }}</b><span>{{ t('day.project') }}</span></div>
      <div class="kpi"><b [class.pos]="m.deltaMinutes > 0" [class.neg]="m.deltaMinutes < 0">{{ signed(m.deltaMinutes) }}</b><span>{{ t('sum.delta') }}</span></div>
      <div class="kpi"><b [class.pos]="m.balanceMinutes > 0" [class.neg]="m.balanceMinutes < 0">{{ signed(m.balanceMinutes) }}</b><span>{{ t('sum.balance') }}</span></div>
    </div>
    <p class="muted small">{{ t('sum.balanceHelp', { opening: hm(m.openingBalanceMinutes) }) }}</p>
    <div class="row" style="align-items:flex-start">
      <section class="card grow"><h2 style="margin-top:0">{{ t('sum.perWeek') }}</h2><div class="tablewrap"><table>
        <thead><tr><th>{{ t('group.WEEK') }}</th><th class="num">{{ t('day.target') }}</th><th class="num">{{ t('day.work') }}</th><th class="num">{{ t('day.project') }}</th></tr></thead>
        <tbody>@for (w of m.weeks; track w.week) { <tr><td>{{ w.week }}</td><td class="num">{{ hm(w.targetMinutes) }}</td><td class="num">{{ hm(w.workMinutes) }}</td><td class="num">{{ hm(w.projectMinutes) }}</td></tr> }</tbody>
      </table></div></section>
      <section class="card grow"><h2 style="margin-top:0">{{ t('sum.perProject') }}</h2><div class="tablewrap"><table>
        <thead><tr><th>{{ t('entry.project') }}</th><th class="num">{{ t('common.hours') }}</th></tr></thead>
        <tbody>@for (p of m.byProject; track p.name) { <tr><td>{{ p.name }}</td><td class="num">{{ hm(p.minutes) }}</td></tr> } @empty { <tr><td colspan="2" class="muted">{{ t('common.none') }}</td></tr> }</tbody>
      </table></div></section>
    </div>
  }
  <section class="card">
    <h2 style="margin-top:0">{{ t('own.title') }}</h2>
    <p class="muted">{{ t('own.help') }}</p>
    <div class="row">
      <label>{{ t('abs.from') }}<input type="date" name="f" [(ngModel)]="from"></label>
      <label>{{ t('abs.to') }}<input type="date" name="t" [(ngModel)]="to"></label>
      @for (f of formats; track f) { <a class="btn secondary" [href]="url(f)" download>{{ f.toUpperCase() }}</a> }
    </div>
    <h3>{{ t('own.record') }}</h3>
    <p class="muted small">{{ t('own.recordHelp') }}</p>
    <div class="row">
      <a class="btn secondary" [href]="recordUrl('pdf')" download>PDF</a>
      <a class="btn secondary" [href]="recordUrl('xlsx')" download>XLSX</a>
      <a class="btn secondary" [href]="recordUrl('csv')" download>CSV</a>
    </div>
  </section>`
})
export class SummaryPage extends Page {
  month = signal(monthStart(today()));
  s = signal<Summary | null>(null);
  formats = ['csv', 'xlsx', 'pdf'];
  from = monthStart(today()); to = today();
  constructor() { super(); void this.load(); }
  async load(): Promise<void> {
    const r = await this.run(() => this.api.get<Summary>('/api/summary', { from: this.month(), to: monthEnd(this.month()) }));
    if (r) this.s.set(r);
  }
  shift(n: number): void { const d = parseIso(this.month()); this.month.set(iso(new Date(d.getFullYear(), d.getMonth() + n, 1))); void this.load(); }
  url(format: string): string { return this.api.url('/api/me/export', { from: this.from, to: this.to, format }); }
  recordUrl(format: string): string { return this.api.url('/api/reports/user', { from: this.month(), to: monthEnd(this.month()), format }); }
}
