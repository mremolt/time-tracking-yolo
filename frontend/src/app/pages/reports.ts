import { Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Page } from '../core/page';
import { Simple, Table } from '../core/models';
import { monthEnd, monthStart, today } from '../core/util';

type Tab = 'user' | 'project' | 'budget' | 'completeness' | 'overtime' | 'payroll';
const GROUPS = ['PROJECT', 'USER', 'TEAM', 'ACTIVITY', 'WEEK', 'MONTH', 'DAY'];

@Component({
  selector: 'app-reports', imports: [FormsModule],
  template: `
  <h1>{{ t('rep.title') }}</h1>
  <div class="tabs" role="tablist" [attr.aria-label]="t('rep.title')">
    @for (x of tabs(); track x) { <button type="button" role="tab" [attr.aria-selected]="tab() === x" (click)="select(x)">{{ t('rep.tab.' + x) }}</button> }
  </div>
  @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }

  <section class="card">
    <form (ngSubmit)="load()" class="row">
      @if (tab() !== 'budget' && tab() !== 'overtime' && tab() !== 'payroll') {
        <label>{{ t('abs.from') }}<input type="date" name="f" [(ngModel)]="from" required></label>
        <label>{{ t('abs.to') }}<input type="date" name="t" [(ngModel)]="to" required></label>
      }
      @if (tab() === 'payroll') { <label>{{ t('rep.month') }}<input type="month" name="m" [(ngModel)]="month" required></label> }
      @if (tab() === 'overtime') { <label>{{ t('col.asOf') }}<input type="date" name="a" [(ngModel)]="asOf"></label> }
      @if (tab() === 'user' && auth.isAdmin()) {
        <label>{{ t('col.user') }}<select name="u" [(ngModel)]="userId"><option [ngValue]="null">{{ t('day.myself') }}</option>@for (u of users(); track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }</select></label>
      }
      @if (tab() === 'project') {
        <label>{{ t('col.team') }}<select name="tm" [(ngModel)]="teamId"><option [ngValue]="null">{{ t('common.all') }}</option>@for (x of teams(); track x.id) { <option [ngValue]="x.id">{{ x.name }}</option> }</select></label>
        <label>{{ t('entry.project') }}<select name="p" [(ngModel)]="projectId"><option [ngValue]="null">{{ t('common.all') }}</option>@for (x of projects(); track x.id) { <option [ngValue]="x.id">{{ x.name }}</option> }</select></label>
        @if (auth.isAdmin()) { <label>{{ t('col.user') }}<select name="us" [(ngModel)]="userId"><option [ngValue]="null">{{ t('common.all') }}</option>@for (x of users(); track x.id) { <option [ngValue]="x.id">{{ x.name }}</option> }</select></label> }
        <label>{{ t('entry.activity') }}<select name="a" [(ngModel)]="activityId"><option [ngValue]="null">{{ t('common.all') }}</option>@for (x of activities(); track x.id) { <option [ngValue]="x.id">{{ x.name }}</option> }</select></label>
      }
      @if (tab() === 'completeness') {
        <label>{{ t('col.team') }}<select name="tm2" [(ngModel)]="teamId"><option [ngValue]="null">{{ t('common.all') }}</option>@for (x of teams(); track x.id) { <option [ngValue]="x.id">{{ x.name }}</option> }</select></label>
      }
      @if (tab() === 'overtime') {
        <label>{{ t('col.team') }}<select name="tm3" [(ngModel)]="teamId"><option [ngValue]="null">{{ t('common.all') }}</option>@for (x of teams(); track x.id) { <option [ngValue]="x.id">{{ x.name }}</option> }</select></label>
        @if (auth.isAdmin()) { <label class="inline"><input type="checkbox" name="bt" [(ngModel)]="byTeam"> {{ t('rep.byTeam') }}</label> }
      }
      <button class="btn" type="submit" [disabled]="busy()">{{ t('rep.show') }}</button>
    </form>
    @if (tab() === 'project') {
      <fieldset style="border:0;padding:0;margin:.8rem 0 0"><legend class="small muted">{{ t('rep.groupBy') }}</legend>
        <div class="row">@for (g of groups; track g) { <label class="inline"><input type="checkbox" [name]="'g'+g" [checked]="groupBy().includes(g)" (change)="toggleGroup(g)"> {{ t('group.' + g) }}</label> }
        <label class="inline"><input type="checkbox" name="det" [(ngModel)]="detail"> {{ t('rep.detail') }}</label></div></fieldset>
      <p class="muted small">{{ t('rep.visibility') }}</p>
    }
    @if (tab() === 'payroll') { <p class="muted small">{{ t('rep.payrollHelp') }}</p> }
  </section>

  @if (table(); as tb) {
    <section class="card">
      <div class="row between">
        <h2 style="margin:0">{{ tb.title }}</h2>
        <div class="row">
          @for (f of formats(); track f) { <a class="btn secondary" [href]="exportUrl(f)" download>⬇ {{ f.toUpperCase() }}</a> }
        </div>
      </div>
      @for (n of tb.notes; track n) { <p class="muted small" style="margin:.3rem 0">{{ n }}</p> }
      <div class="tablewrap"><table>
        <thead><tr>@for (h of tb.headers; track $index) { <th scope="col">{{ h }}</th> }</tr></thead>
        <tbody>@for (r of tb.rows; track $index) { <tr>@for (c of r; track $index) { <td [class.num]="isNum(c)">{{ c }}</td> }</tr> }
        @empty { <tr><td [attr.colspan]="tb.headers.length" class="muted">{{ t('common.none') }}</td></tr> }</tbody>
        @if (tb.totals.length) { <tfoot><tr class="total">@for (c of tb.totals; track $index) { <td [class.num]="isNum(c)">{{ c }}</td> }</tr></tfoot> }
      </table></div>
    </section>
  }`
})
export class ReportsPage extends Page {
  tab = signal<Tab>('user');
  table = signal<Table | null>(null);
  teams = signal<Simple[]>([]); projects = signal<Simple[]>([]); users = signal<Simple[]>([]); activities = signal<Simple[]>([]);
  groups = GROUPS;
  groupBy = signal<string[]>(['PROJECT']);
  from = monthStart(today()); to = monthEnd(today()); month = today().substring(0, 7); asOf = today();
  teamId: number | null = null; projectId: number | null = null; userId: number | null = null; activityId: number | null = null;
  detail = false; byTeam = false;

  tabs = computed<Tab[]>(() => {
    const r: Tab[] = ['user', 'project'];
    if (this.auth.isAdmin() || this.auth.isLead() || this.auth.isManagement()) r.push('budget');
    if (this.auth.isAdmin()) r.push('completeness');
    if (this.auth.isAdmin() || this.auth.isManagement()) r.push('overtime');
    if (this.auth.isAdmin()) r.push('payroll');
    return r;
  });
  formats = computed(() => this.tab() === 'payroll' ? ['csv', 'xlsx'] : (this.tab() === 'user' || this.tab() === 'project') ? ['csv', 'xlsx', 'pdf'] : ['csv', 'xlsx']);

  constructor() {
    super();
    void (async () => {
      this.teams.set(await this.api.get<Simple[]>('/api/lookups/teams'));
      this.projects.set(await this.api.get<Simple[]>('/api/lookups/report-projects'));
      this.activities.set(await this.api.get<Simple[]>('/api/lookups/activities'));
      if (this.auth.isAdmin()) this.users.set(await this.api.get<Simple[]>('/api/lookups/users'));
      if (this.auth.isManagement() && !this.auth.isAdmin()) { this.tab.set('project'); }
      await this.load();
    })();
  }

  select(t: Tab): void { this.tab.set(t); this.table.set(null); this.error.set(''); void this.load(); }
  toggleGroup(g: string): void { this.groupBy.update(x => x.includes(g) ? x.filter(y => y !== g) : [...x, g]); }
  isNum(c: string): boolean { return /^-?[0-9]+([.,][0-9]+)?( %)?$/.test(c ?? ''); }

  private path(): string {
    switch (this.tab()) {
      case 'user': return '/api/reports/user';
      case 'project': return '/api/reports/project';
      case 'budget': return '/api/reports/budget';
      case 'completeness': return '/api/reports/completeness';
      case 'overtime': return '/api/reports/overtime';
      default: return '/api/exports/payroll';
    }
  }
  private params(): Record<string, unknown> {
    switch (this.tab()) {
      case 'user': return { from: this.from, to: this.to, userId: this.userId };
      case 'project': return { from: this.from, to: this.to, teamId: this.teamId, projectId: this.projectId, userId: this.userId, activityId: this.activityId, groupBy: this.groupBy(), detail: this.detail };
      case 'completeness': return { from: this.from, to: this.to, teamId: this.teamId };
      case 'overtime': return { asOf: this.asOf, byTeam: this.byTeam || !this.auth.isAdmin(), teamId: this.teamId };
      case 'payroll': return { month: this.month };
      default: return {};
    }
  }
  async load(): Promise<void> {
    const r = await this.run(() => this.api.get<Table>(this.path(), { ...this.params(), format: 'json', lang: this.i18n.lang() }));
    if (r) this.table.set(r);
  }
  exportUrl(format: string): string { return this.api.url(this.path(), { ...this.params(), format, lang: this.i18n.lang() }); }
}
