import { Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Page } from '../core/page';
import { BookableProject, Day, Simple } from '../core/models';
import { addDays, hm, mondayOf, parseDuration, today } from '../core/util';

interface Row { projectId: number; activityId: number; }

@Component({
  selector: 'app-week', imports: [FormsModule, RouterLink],
  template: `
  <div class="row between">
    <h1>{{ t('week.title') }}</h1>
    <div class="row">
      <button class="btn secondary" type="button" (click)="go(-7)" [attr.aria-label]="t('common.prev')">‹</button>
      <strong>{{ t('week.kw', { w: weekNo(), from: d(monday(), {day:'2-digit',month:'2-digit'}), to: d(sunday(), {day:'2-digit',month:'2-digit',year:'numeric'}) }) }}</strong>
      <button class="btn secondary" type="button" (click)="go(7)" [attr.aria-label]="t('common.next')">›</button>
      <button class="btn secondary" type="button" (click)="monday.set(mon(todayIso)); load()">{{ t('week.thisWeek') }}</button>
    </div>
  </div>
  @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
  @if (ok()) { <div class="alert ok" role="status">{{ ok() }}</div> }
  <div class="card">
    <div class="tablewrap">
    <table class="weekgrid">
      <caption class="sr-only-label">{{ t('week.title') }}</caption>
      <thead><tr>
        <th scope="col">{{ t('entry.project') }} / {{ t('entry.activity') }}</th>
        @for (dy of days(); track dy.date) {
          <th scope="col" class="num" [class.today]="dy.date === todayIso">
            <a [routerLink]="['/day']" [queryParams]="{date: dy.date}">{{ d(dy.date, {weekday:'short', day:'2-digit', month:'2-digit'}) }}</a>
            @if (dy.warnings.length) { <span title="{{ warnTitle(dy) }}" aria-label="warning">⚠</span> }
            @if (dy.hints.length) { <span title="{{ hintTitle(dy) }}" aria-label="hint">💡</span> }
          </th>
        }
        <th scope="col" class="num">{{ t('common.total') }}</th>
      </tr></thead>
      <tbody>
        @for (r of rows(); track key(r)) {
          <tr>
            <th scope="row" style="background:#fff">
              <button type="button" class="icon-btn" style="color:#c98a00" (click)="toggleFav(r.projectId)" [attr.aria-pressed]="isFav(r.projectId)" [attr.aria-label]="t('week.favorite')">{{ isFav(r.projectId) ? '★' : '☆' }}</button>
              {{ projectLabel(r.projectId) }}<br><span class="muted small">{{ activityLabel(r.activityId) }}</span>
            </th>
            @for (dy of days(); track dy.date) {
              <td class="num" [class.today]="dy.date === todayIso">
                @if (cellCount(dy, r) > 1) { <span class="muted" [title]="t('week.multi')">{{ hm(cellMinutes(dy, r)) }} *</span> }
                @else {
                  <input class="cell" [attr.aria-label]="projectLabel(r.projectId) + ' ' + dy.date" [value]="cellText(dy, r)" placeholder="–"
                         (change)="setCell(dy, r, $any($event.target).value, $event)" (keydown.enter)="$any($event.target).blur()">
                }
              </td>
            }
            <td class="num"><strong>{{ hm(rowTotal(r)) }}</strong></td>
          </tr>
        } @empty { <tr><td [attr.colspan]="days().length + 2" class="muted">{{ t('week.empty') }}</td></tr> }
        <tr class="total"><td>{{ t('week.projectTotal') }}</td>@for (dy of days(); track dy.date) { <td class="num">{{ hm(dy.projectMinutes) }}</td> }<td class="num">{{ hm(sum('projectMinutes')) }}</td></tr>
        <tr><td>{{ t('week.workTime') }}</td>@for (dy of days(); track dy.date) { <td class="num">{{ hm(dy.workMinutes) }}</td> }<td class="num">{{ hm(sum('workMinutes')) }}</td></tr>
        <tr><td>{{ t('day.target') }}</td>@for (dy of days(); track dy.date) { <td class="num">{{ hm(dy.targetMinutes) }}<div class="small muted">{{ dayNote(dy) }}</div></td> }<td class="num">{{ hm(sum('targetMinutes')) }}</td></tr>
      </tbody>
    </table></div>
  </div>

  <div class="card">
    <h2 style="margin-top:0">{{ t('week.addRow') }}</h2>
    <div class="row">
      <label class="grow">{{ t('entry.project') }}<select [(ngModel)]="newProject" name="np">@for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.favorite ? '★ ' : (p.recent ? '⟲ ' : '') }}{{ p.code }} {{ p.name }}</option> }</select></label>
      <label class="grow">{{ t('entry.activity') }}<select [(ngModel)]="newActivity" name="na">@for (a of activities(); track a.id) { <option [ngValue]="a.id">{{ a.name }}</option> }</select></label>
      <button class="btn" type="button" (click)="addRow()">+ {{ t('common.add') }}</button>
    </div>
    <h3>{{ t('week.copy') }}</h3>
    <div class="row">
      <button class="btn secondary" type="button" (click)="copyWeek()">{{ t('week.copyWeek') }}</button>
      <label>{{ t('week.copyDayTo') }}<select [(ngModel)]="copyTarget" name="ct">@for (dy of days(); track dy.date) { <option [ngValue]="dy.date">{{ d(dy.date, {weekday:'short', day:'2-digit', month:'2-digit'}) }}</option> }</select></label>
      <button class="btn secondary" type="button" (click)="copyDay()">{{ t('week.copyDay') }}</button>
    </div>
    <p class="muted small">{{ t('week.help') }}</p>
  </div>`
})
export class WeekPage extends Page {
  todayIso = today();
  monday = signal(mondayOf(today()));
  sunday = computed(() => addDays(this.monday(), 6));
  days = signal<Day[]>([]);
  extra = signal<Row[]>([]);
  projects = signal<BookableProject[]>([]);
  activities = signal<Simple[]>([]);
  favs = signal<number[]>([]);
  newProject: number | null = null;
  newActivity: number | null = null;
  copyTarget = today();
  mon = mondayOf;

  weekNo = computed(() => {
    const [y, m, dd] = this.monday().split('-').map(Number);
    const d = new Date(Date.UTC(y, m - 1, dd + 3));
    const first = new Date(Date.UTC(d.getUTCFullYear(), 0, 4));
    return 1 + Math.round(((d.getTime() - first.getTime()) / 86400000 - 3 + ((first.getUTCDay() + 6) % 7)) / 7);
  });

  rows = computed<Row[]>(() => {
    const seen = new Map<string, Row>();
    for (const dy of this.days()) for (const e of dy.entries) { const r = { projectId: e.projectId, activityId: e.activityId }; seen.set(this.key(r), r); }
    for (const r of this.extra()) seen.set(this.key(r), r);
    const fav = new Set(this.favs());
    return [...seen.values()].sort((a, b) => Number(fav.has(b.projectId)) - Number(fav.has(a.projectId))
      || this.projectLabel(a.projectId).localeCompare(this.projectLabel(b.projectId)) || a.activityId - b.activityId);
  });

  constructor() { super(); void this.init(); }

  async init(): Promise<void> {
    const r = await this.run(async () => ({
      acts: await this.api.get<Simple[]>('/api/lookups/activities'),
      favs: await this.api.get<number[]>('/api/me/favorites')
    }));
    if (!r) return;
    this.activities.set(r.acts); this.favs.set([...r.favs]);
    this.newActivity = r.acts[0]?.id ?? null;
    await this.load();
  }

  async load(): Promise<void> {
    const r = await this.run(async () => ({
      days: await this.api.get<Day[]>('/api/days', { from: this.monday(), to: this.sunday() }),
      projects: await this.api.get<BookableProject[]>('/api/lookups/projects')
    }));
    if (!r) return;
    this.days.set(r.days); this.projects.set(r.projects);
    const def = this.auth.user()?.defaultProjectId;
    if (!this.newProject || !r.projects.some(p => p.id === this.newProject)) this.newProject = (r.projects.find(p => p.id === def) ?? r.projects[0])?.id ?? null;
  }

  go(n: number): void { this.monday.set(addDays(this.monday(), n)); this.extra.set([]); void this.load(); }
  key(r: Row): string { return r.projectId + ':' + r.activityId; }
  projectLabel(id: number): string { const p = this.projects().find(x => x.id === id); return p ? `${p.code} ${p.name}` : '#' + id; }
  activityLabel(id: number): string { return this.activities().find(x => x.id === id)?.name ?? '#' + id; }
  isFav(id: number): boolean { return this.favs().includes(id); }
  cellEntries(dy: Day, r: Row) { return dy.entries.filter(e => e.projectId === r.projectId && e.activityId === r.activityId); }
  cellCount(dy: Day, r: Row): number { return this.cellEntries(dy, r).length; }
  cellMinutes(dy: Day, r: Row): number { return this.cellEntries(dy, r).reduce((s, e) => s + e.minutes, 0); }
  cellText(dy: Day, r: Row): string { const m = this.cellMinutes(dy, r); return m ? hm(m) : ''; }
  rowTotal(r: Row): number { return this.days().reduce((s, dy) => s + this.cellMinutes(dy, r), 0); }
  sum(k: 'projectMinutes' | 'workMinutes' | 'targetMinutes'): number { return this.days().reduce((s, dy) => s + dy[k], 0); }
  warnTitle(dy: Day): string { return dy.warnings.map(w => this.msg(w)).join('\n'); }
  hintTitle(dy: Day): string { return dy.hints.map(w => this.msg(w)).join('\n'); }
  dayNote(dy: Day): string {
    if (dy.absence) return this.t('absence.' + dy.absence);
    return (this.i18n.lang() === 'de' ? dy.holidayDe : dy.holidayEn) ?? '';
  }

  async setCell(dy: Day, r: Row, value: string, ev: Event): Promise<void> {
    const v = value.trim();
    const minutes = v === '' ? 0 : parseDuration(v);
    if (minutes === null) { this.error.set(this.t('err.DURATION_REQUIRED')); (ev.target as HTMLInputElement).value = this.cellText(dy, r); return; }
    const ok = await this.run(() => this.api.put('/api/entries/cell', { date: dy.date, projectId: r.projectId, activityId: r.activityId, minutes }).then(() => true));
    await this.load();
    if (!ok) (ev.target as HTMLInputElement).value = this.cellText(dy, r);
  }
  async toggleFav(id: number): Promise<void> {
    const next = this.isFav(id) ? this.favs().filter(x => x !== id) : [...this.favs(), id];
    const r = await this.run(() => this.api.put<number[]>('/api/me/favorites', { projectIds: next }));
    if (r) { this.favs.set([...r]); await this.load(); }
  }
  addRow(): void {
    if (this.newProject && this.newActivity) this.extra.update(x => [...x, { projectId: this.newProject!, activityId: this.newActivity! }]);
  }
  async copyWeek(): Promise<void> {
    const r = await this.run(() => this.api.post<{ copied: number }>('/api/entries/copy', { from: addDays(this.monday(), -7), to: this.monday(), week: true }));
    if (r) { this.ok.set(this.t('week.copied', { n: r.copied })); await this.load(); }
  }
  async copyDay(): Promise<void> {
    const r = await this.run(() => this.api.post<{ copied: number }>('/api/entries/copy', { from: addDays(this.copyTarget, -1), to: this.copyTarget, week: false }));
    if (r) { this.ok.set(this.t('week.copied', { n: r.copied })); await this.load(); }
  }
}
