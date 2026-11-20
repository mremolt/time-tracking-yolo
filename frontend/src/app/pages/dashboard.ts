import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Page } from '../core/page';
import { monthEnd, monthStart, today } from '../core/util';

interface Named { name: string; minutes: number; }
interface Dash {
  projectMinutes: number; workMinutes: number; byProject: Named[]; byTeam: Named[];
  weekly: { week: string; workMinutes: number; projectMinutes: number }[];
  overtimeTotal: number; overtimeByTeam: Named[]; missingDays: number; missingEmployees: number; nearBudget: { project: string; percent: number }[];
}

/** Overview for backoffice and management. Aggregates only, no per-person data (F-01, F-14). */
@Component({
  selector: 'app-dashboard', imports: [FormsModule, RouterLink],
  template: `
  <div class="row between">
    <h1>{{ auth.isAdmin() ? t('dash.title') : t('dash.titleMgmt') }}</h1>
    <form (ngSubmit)="load()" class="row">
      <label>{{ t('abs.from') }}<input type="date" name="f" [(ngModel)]="from"></label>
      <label>{{ t('abs.to') }}<input type="date" name="t" [(ngModel)]="to"></label>
      <button class="btn" type="submit">{{ t('rep.show') }}</button>
    </form>
  </div>
  @if (!auth.isAdmin()) { <div class="alert info">{{ t('dash.mgmtNote') }}</div> }
  @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
  @if (dash(); as x) {
    <div class="kpis">
      <div class="kpi"><b>{{ hm(x.workMinutes) }}</b><span>{{ t('dash.work') }}</span></div>
      <div class="kpi"><b>{{ hm(x.projectMinutes) }}</b><span>{{ t('dash.project') }}</span></div>
      <div class="kpi"><b [class.pos]="x.overtimeTotal > 0" [class.neg]="x.overtimeTotal < 0">{{ signed(x.overtimeTotal) }}</b><span>{{ t('dash.overtime') }}</span></div>
      <div class="kpi"><b>{{ x.missingDays }}</b><span>{{ t('dash.missing', { n: x.missingEmployees }) }}</span></div>
    </div>
    <div class="row" style="align-items:flex-start">
      <section class="card grow"><h2 style="margin-top:0">{{ t('dash.perProject') }}</h2>
        @for (p of x.byProject; track p.name) { <div class="hbar"><span>{{ p.name }}</span><div class="bar"><i [style.width.%]="pct(p.minutes, x.byProject)"></i></div><span class="right">{{ hm(p.minutes) }}</span></div> } @empty { <p class="muted">{{ t('common.none') }}</p> }</section>
      <section class="card grow"><h2 style="margin-top:0">{{ t('dash.perTeam') }}</h2>
        @for (p of x.byTeam; track p.name) { <div class="hbar"><span>{{ p.name }}</span><div class="bar"><i [style.width.%]="pct(p.minutes, x.byTeam)"></i></div><span class="right">{{ hm(p.minutes) }}</span></div> } @empty { <p class="muted">{{ t('common.none') }}</p> }</section>
    </div>
    <div class="row" style="align-items:flex-start">
      <section class="card grow"><h2 style="margin-top:0">{{ t('dash.trend') }}</h2>
        <div class="tablewrap"><table><thead><tr><th>{{ t('group.WEEK') }}</th><th class="num">{{ t('dash.work') }}</th><th class="num">{{ t('dash.project') }}</th><th>{{ t('dash.trend') }}</th></tr></thead><tbody>
          @for (w of x.weekly; track w.week) { <tr><td>{{ w.week }}</td><td class="num">{{ hm(w.workMinutes) }}</td><td class="num">{{ hm(w.projectMinutes) }}</td><td style="width:35%"><div class="bar"><i [style.width.%]="trend(w.workMinutes, x)"></i></div></td></tr> }
        </tbody></table></div></section>
      <section class="card grow"><h2 style="margin-top:0">{{ t('dash.overtimeTeam') }}</h2>
        <table><tbody>@for (p of x.overtimeByTeam; track p.name) { <tr><td>{{ p.name }}</td><td class="num" [class.pos]="p.minutes > 0" [class.neg]="p.minutes < 0">{{ signed(p.minutes) }}</td></tr> }</tbody></table>
        <h2>{{ t('dash.nearBudget') }}</h2>
        @for (n of x.nearBudget; track n.project) { <div class="hbar"><span>{{ n.project }}</span><div class="bar" [class.warn]="n.percent >= 80 && n.percent < 100" [class.err]="n.percent >= 100"><i [style.width.%]="min100(n.percent)"></i></div><span class="right">{{ n.percent }} %</span></div> }
        @empty { <p class="muted">{{ t('dash.noBudgetIssues') }}</p> }
        <p><a routerLink="/reports">{{ t('dash.toReports') }}</a></p></section>
    </div>
  }`
})
export class DashboardPage extends Page {
  from = monthStart(today()); to = monthEnd(today());
  dash = signal<Dash | null>(null);
  constructor() { super(); void this.load(); }
  async load(): Promise<void> { const r = await this.run(() => this.api.get<Dash>('/api/dashboard', { from: this.from, to: this.to })); if (r) this.dash.set(r); }
  pct(v: number, list: Named[]): number { const max = Math.max(1, ...list.map(x => x.minutes)); return (v / max) * 100; }
  trend(v: number, x: Dash): number { return (v / Math.max(1, ...x.weekly.map(w => w.workMinutes))) * 100; }
  min100(v: number): number { return Math.min(100, v); }
}
