import { Component, OnDestroy, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { inject } from '@angular/core';
import { Page } from '../core/page';
import { BookableProject, Day, Entry, Simple, Status } from '../core/models';
import { addDays, parseDuration, timeOf, today } from '../core/util';
import { AuditDialog } from './audit-dialog';

@Component({
  selector: 'app-day', imports: [FormsModule, AuditDialog],
  template: `
  <div class="row between">
    <h1>{{ t('day.title') }}</h1>
    <div class="row">
      @if (auth.isAdmin()) {
        <label>{{ t('day.forUser') }}
          <select [ngModel]="userId()" (ngModelChange)="userId.set($event); load()" name="u">
            <option [ngValue]="null">{{ t('day.myself') }}</option>
            @for (u of users(); track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }
          </select></label>
      }
      <div class="row" role="group" [attr.aria-label]="t('day.navigate')">
        <button class="btn secondary" type="button" (click)="go(-1)" [attr.aria-label]="t('common.prev')">‹</button>
        <label class="sr-only-label">{{ t('common.date') }}<input type="date" [ngModel]="date()" (ngModelChange)="setDate($event)" name="date"></label>
        <button class="btn secondary" type="button" (click)="go(1)" [attr.aria-label]="t('common.next')">›</button>
        <button class="btn secondary" type="button" (click)="setDate(todayIso)">{{ t('common.today') }}</button>
      </div>
    </div>
  </div>
  <h2 style="margin-top:0">{{ d(date(), {weekday:'long', day:'numeric', month:'long', year:'numeric'}) }}</h2>
  @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
  @if (ok()) { <div class="alert ok" role="status">{{ ok() }}</div> }
  @if (locked()) { <div class="alert warn" role="status">🔒 {{ auth.isAdmin() ? t('day.lockedAdmin') : t('day.locked') }}</div> }

  @if (day(); as dy) {
    @if (isToday() && !userId()) {
      <section class="card" [attr.aria-label]="t('timer.title')">
        <div class="timer">
          <div>
            <div class="muted small">{{ t('timer.work') }}</div>
            <div class="clock" aria-live="off">{{ workClock() }}</div>
            <div class="muted small">{{ workState() }}</div>
          </div>
          <div class="row">
            @if (!status()?.workDay) { <button class="btn big" type="button" (click)="act('start')">▶ {{ t('timer.start') }}</button> }
            @else if (status()?.workDay?.running) {
              @if (status()?.workDay?.paused) { <button class="btn big" type="button" (click)="act('resume')">▶ {{ t('timer.resume') }}</button> }
              @else { <button class="btn big secondary" type="button" (click)="act('pause')">⏸ {{ t('timer.pause') }}</button> }
              <button class="btn big danger" type="button" (click)="act('stop')">■ {{ t('timer.stop') }}</button>
            }
          </div>
          <div style="flex:1 1 20rem">
            <div class="muted small">{{ t('timer.project') }}</div>
            @if (status()?.projectTimer; as pt) {
              <div class="row between"><strong>{{ projectLabel(pt.projectId) }} · {{ activityLabel(pt.activityId) }}</strong><span class="clock" style="font-size:1.4rem">{{ projectClock() }}</span>
                <button class="btn danger" type="button" (click)="stopProjectTimer()">■ {{ t('timer.stop') }}</button></div>
            } @else {
              <div class="row">
                <label class="grow">{{ t('entry.project') }}<select [(ngModel)]="timerProject" name="tp">@for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.favorite ? '★ ' : '' }}{{ p.code }} {{ p.name }}</option> }</select></label>
                <label class="grow">{{ t('entry.activity') }}<select [(ngModel)]="timerActivity" name="ta">@for (a of activities(); track a.id) { <option [ngValue]="a.id">{{ a.name }}</option> }</select></label>
                <button class="btn" type="button" [disabled]="!timerProject || !timerActivity" (click)="startProjectTimer()">▶ {{ t('timer.start') }}</button>
              </div>
            }
          </div>
        </div>
      </section>
    }

    <div class="kpis">
      <div class="kpi"><b>{{ hm(dy.targetMinutes) }}</b><span>{{ t('day.target') }}</span></div>
      <div class="kpi"><b>{{ hm(dy.workMinutes) }}</b><span>{{ t('day.work') }} ({{ t('day.breaks') }} {{ hm(dy.breakMinutes) }})</span></div>
      <div class="kpi"><b>{{ hm(dy.projectMinutes) }}</b><span>{{ t('day.project') }}</span></div>
      <div class="kpi"><b [class.pos]="dy.workMinutes - dy.targetMinutes > 0" [class.neg]="dy.workMinutes - dy.targetMinutes < 0">{{ signed(dy.workMinutes - dy.targetMinutes) }}</b><span>{{ t('day.delta') }}</span></div>
      <div class="kpi"><b [class.pos]="(status()?.balanceMinutes ?? 0) > 0" [class.neg]="(status()?.balanceMinutes ?? 0) < 0">{{ signed(status()?.balanceMinutes ?? 0) }}</b><span>{{ t('day.balance') }}</span></div>
    </div>
    <div class="chips" style="margin-bottom:.6rem">
      @if (holiday(dy)) { <span class="chip ok">🎉 {{ holiday(dy) }}</span> }
      @if (dy.absence) { <span class="chip">{{ t('absence.' + dy.absence) }}{{ dy.absenceHalf ? ' ½' : '' }}</span> }
    </div>
    @for (w of dy.warnings; track w.key) { <div class="alert warn" role="status">⚠ {{ msg(w) }}</div> }
    @for (h of dy.hints; track h.key) { <div class="alert info" role="status">💡 {{ msg(h) }}</div> }

    <section class="card">
      <h2 style="margin-top:0">{{ t('wt.title') }}</h2>
      <form (ngSubmit)="saveWorkDay()" class="row">
        <label>{{ t('wt.start') }}<input type="time" name="ws" [(ngModel)]="wd.start" required></label>
        <label>{{ t('wt.end') }}<input type="time" name="we" [(ngModel)]="wd.end"></label>
        <label class="inline"><input type="checkbox" name="wn" [(ngModel)]="wd.endNext"> {{ t('wt.nextDay') }}</label>
        <button class="btn" type="submit" [disabled]="busy()">{{ t('common.save') }}</button>
        @if (dy.workDay) { <button class="btn secondary danger-text" type="button" (click)="deleteWorkDay()">{{ t('common.delete') }}</button> }
      </form>
      <h3>{{ t('wt.breaks') }}</h3>
      @for (b of wd.breaks; track $index) {
        <div class="row" style="margin-bottom:.3rem">
          <label>{{ t('wt.breakStart') }}<input type="time" [name]="'bs'+$index" [(ngModel)]="b.start"></label>
          <label>{{ t('wt.breakEnd') }}<input type="time" [name]="'be'+$index" [(ngModel)]="b.end"></label>
          <button class="btn small secondary" type="button" (click)="wd.breaks.splice($index, 1)" [attr.aria-label]="t('common.remove')">✕</button>
        </div>
      }
      <button class="btn small secondary" type="button" (click)="wd.breaks.push({start:'12:00', end:'12:30'})">+ {{ t('wt.addBreak') }}</button>
      <span class="muted small"> {{ t('wt.breakHelp') }}</span>
    </section>

    <section class="card">
      <h2 style="margin-top:0">{{ t('entry.title') }}</h2>
      <div class="tablewrap">
      <table>
        <thead><tr><th>{{ t('entry.project') }}</th><th>{{ t('entry.activity') }}</th><th>{{ t('entry.time') }}</th><th class="num">{{ t('common.hours') }}</th><th>{{ t('entry.description') }}</th><th></th></tr></thead>
        <tbody>
          @for (e of dy.entries; track e.id) {
            <tr>
              <td>{{ projectLabel(e.projectId) }}</td><td>{{ activityLabel(e.activityId) }}</td>
              <td class="nowrap">{{ e.start ? timeOf(e.start) + '–' + (e.end ? timeOf(e.end) : '…') : '' }}{{ e.running ? ' ⏱' : '' }}</td>
              <td class="num">{{ hm(e.minutes) }}</td><td>{{ e.description }}</td>
              <td class="nowrap">
                <button class="btn small secondary" type="button" (click)="edit(e)">{{ t('common.edit') }}</button>
                <button class="btn small secondary" type="button" (click)="remove(e)">{{ t('common.delete') }}</button>
                <button class="btn small secondary" type="button" (click)="history = e.id" [attr.aria-label]="t('audit.history')">🕘</button>
              </td>
            </tr>
          } @empty { <tr><td colspan="6" class="muted">{{ t('entry.none') }}</td></tr> }
        </tbody>
        <tfoot><tr class="total"><td colspan="3">{{ t('common.total') }}</td><td class="num">{{ hm(dy.projectMinutes) }}</td><td colspan="2"></td></tr></tfoot>
      </table></div>

      <h3>{{ editId ? t('entry.edit') : t('entry.add') }}</h3>
      <form (ngSubmit)="saveEntry()" class="row">
        <label class="grow">{{ t('entry.project') }}<select name="ep" [(ngModel)]="form.projectId" required>
          @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.favorite ? '★ ' : (p.recent ? '⟲ ' : '') }}{{ p.code }} {{ p.name }}</option> }</select></label>
        <label class="grow">{{ t('entry.activity') }}<select name="ea" [(ngModel)]="form.activityId" required>
          @for (a of activities(); track a.id) { <option [ngValue]="a.id">{{ a.name }}</option> }</select></label>
        <label>{{ t('entry.duration') }}<input name="ed" [(ngModel)]="form.duration" placeholder="1:30" [attr.aria-describedby]="'durhelp'"></label>
        <label>{{ t('entry.from') }}<input type="time" name="ef" [(ngModel)]="form.start"></label>
        <label>{{ t('entry.to') }}<input type="time" name="et" [(ngModel)]="form.end"></label>
        <label class="grow">{{ t('entry.description') }}<input name="edesc" [(ngModel)]="form.description" maxlength="1000"></label>
        <button class="btn" type="submit" [disabled]="busy()">{{ editId ? t('common.save') : t('common.add') }}</button>
        @if (editId) { <button class="btn secondary" type="button" (click)="resetForm()">{{ t('common.cancel') }}</button> }
      </form>
      <p id="durhelp" class="muted small">{{ t('entry.durationHelp') }}</p>
    </section>
  }
  @if (history) { <app-audit-dialog type="ProjectEntry" [id]="history" (closed)="history = 0" /> }`
})
export class DayPage extends Page implements OnDestroy {
  todayIso = today();
  private route = inject(ActivatedRoute);
  date = signal(this.route.snapshot.queryParamMap.get('date') || today());
  userId = signal<number | null>(null);
  users = signal<Simple[]>([]);
  day = signal<Day | null>(null);
  status = signal<Status | null>(null);
  projects = signal<BookableProject[]>([]);
  activities = signal<Simple[]>([]);
  locked = signal(false);
  now = signal(Date.now());
  timerProject: number | null = null;
  timerActivity: number | null = null;
  editId = 0;
  history = 0;
  wd: { start: string; end: string; endNext: boolean; breaks: { start: string; end: string }[] } = { start: '', end: '', endNext: false, breaks: [] };
  form: { projectId: number | null; activityId: number | null; duration: string; start: string; end: string; description: string } =
    { projectId: null, activityId: null, duration: '', start: '', end: '', description: '' };
  timeOf = timeOf;
  private tick = setInterval(() => this.now.set(Date.now()), 15000);

  isToday = computed(() => this.date() === this.todayIso);
  workClock = computed(() => {
    const w = this.status()?.workDay; this.now();
    if (!w) return '0:00';
    const end = w.end ? new Date(w.end).getTime() : Date.now();
    let ms = end - new Date(w.start).getTime();
    for (const b of w.breaks) ms -= (b.end ? new Date(b.end).getTime() : end) - new Date(b.start).getTime();
    return this.hm(Math.max(0, Math.floor(ms / 60000)));
  });
  workState = computed(() => {
    const w = this.status()?.workDay;
    if (!w) return this.t('timer.notStarted');
    if (w.running) return w.paused ? this.t('timer.paused') : this.t('timer.running', { since: timeOf(w.start) });
    return this.t('timer.finished');
  });
  projectClock = computed(() => {
    const p = this.status()?.projectTimer; this.now();
    if (!p?.start) return '0:00';
    return this.hm(Math.max(0, Math.floor((Date.now() - new Date(p.start).getTime()) / 60000)));
  });

  constructor() {
    super();
    void this.init();
  }
  ngOnDestroy(): void { clearInterval(this.tick); }

  async init(): Promise<void> {
    const [acts] = await Promise.all([this.api.get<Simple[]>('/api/lookups/activities')]);
    this.activities.set(acts);
    if (this.auth.isAdmin()) this.users.set(await this.api.get<Simple[]>('/api/lookups/users'));
    await this.load();
  }

  async load(): Promise<void> {
    const uid = this.userId() ?? undefined;
    const r = await this.run(async () => {
      const [days, status, projects, locked] = await Promise.all([
        this.api.get<Day[]>('/api/days', { from: this.date(), to: this.date(), userId: uid }),
        this.api.get<Status>('/api/status', { userId: uid }),
        this.api.get<BookableProject[]>('/api/lookups/projects', { userId: uid }),
        this.api.get<boolean>('/api/locked', { date: this.date(), userId: uid })
      ]);
      return { days, status, projects, locked };
    });
    if (!r) return;
    this.day.set(r.days[0]); this.status.set(r.status); this.projects.set(r.projects); this.locked.set(r.locked);
    const def = this.auth.user()?.defaultProjectId;
    const first = r.projects.find(p => p.id === def) ?? r.projects[0];
    if (!this.timerProject || !r.projects.some(p => p.id === this.timerProject)) this.timerProject = first?.id ?? null;
    if (!this.timerActivity) this.timerActivity = this.activities()[0]?.id ?? null;
    if (!this.editId && (!this.form.projectId || !r.projects.some(p => p.id === this.form.projectId))) this.form.projectId = first?.id ?? null;
    if (!this.form.activityId) this.form.activityId = this.activities()[0]?.id ?? null;
    this.fillWorkDay(r.days[0]);
    this.cdr.markForCheck();
  }

  setDate(d: string): void { if (d) { this.date.set(d); this.resetForm(); void this.load(); } }
  go(n: number): void { this.setDate(addDays(this.date(), n)); }

  holiday(d: Day): string | undefined { return this.i18n.lang() === 'de' ? d.holidayDe : d.holidayEn; }
  projectLabel(id: number): string { const p = this.projects().find(x => x.id === id); return p ? `${p.code} ${p.name}` : '#' + id; }
  activityLabel(id: number): string { return this.activities().find(x => x.id === id)?.name ?? '#' + id; }

  // ---- working time ----
  fillWorkDay(d: Day): void {
    const w = d.workDay;
    if (!w) { this.wd = { start: '', end: '', endNext: false, breaks: [] }; return; }
    this.wd = {
      start: timeOf(w.start), end: w.end ? timeOf(w.end) : '', endNext: !!w.end && w.end.substring(0, 10) !== w.start.substring(0, 10),
      breaks: w.breaks.map(b => ({ start: timeOf(b.start), end: b.end ? timeOf(b.end) : '' }))
    };
  }
  private dt(time: string, nextIfBefore = false): string {
    const next = this.wd.endNext && (nextIfBefore || (this.wd.start && time < this.wd.start));
    return `${next ? addDays(this.date(), 1) : this.date()}T${time}`;
  }
  async saveWorkDay(): Promise<void> {
    if (!this.wd.start) { this.error.set(this.t('err.START_REQUIRED')); return; }
    const breaks = this.wd.breaks.filter(b => b.start && b.end).map(b => ({ start: this.dt(b.start), end: this.dt(b.end) }));
    const body = { userId: this.userId(), date: this.date(), start: `${this.date()}T${this.wd.start}`, end: this.wd.end ? this.dt(this.wd.end, true) : null, breaks };
    if (await this.run(() => this.api.put('/api/work-days', body).then(() => true), 'common.saved')) await this.load();
  }
  async deleteWorkDay(): Promise<void> {
    const id = this.day()?.workDay?.id; if (!id) return;
    if (!confirm(this.t('common.confirmDelete'))) return;
    if (await this.run(() => this.api.delete('/api/work-days/' + id).then(() => true), 'common.deleted')) await this.load();
  }
  async act(a: 'start' | 'pause' | 'resume' | 'stop'): Promise<void> {
    if (await this.run(() => this.api.post('/api/work/' + a).then(() => true))) await this.load();
  }

  // ---- project time ----
  async startProjectTimer(): Promise<void> {
    if (await this.run(() => this.api.post('/api/entries/timer/start', { projectId: this.timerProject, activityId: this.timerActivity }).then(() => true))) await this.load();
  }
  async stopProjectTimer(): Promise<void> {
    if (await this.run(() => this.api.post('/api/entries/timer/stop').then(() => true))) await this.load();
  }
  edit(e: Entry): void {
    this.editId = e.id;
    this.form = { projectId: e.projectId, activityId: e.activityId, duration: e.start ? '' : this.hm(e.minutes), start: e.start ? timeOf(e.start) : '', end: e.end ? timeOf(e.end) : '', description: e.description ?? '' };
  }
  resetForm(): void { this.editId = 0; this.form = { ...this.form, duration: '', start: '', end: '', description: '' }; }
  async saveEntry(): Promise<void> {
    const f = this.form;
    const body: Record<string, unknown> = { userId: this.userId(), date: this.date(), projectId: f.projectId, activityId: f.activityId, description: f.description || null };
    if (f.start && f.end) { body['start'] = f.start; body['end'] = f.end; }
    else {
      const m = parseDuration(f.duration);
      if (m === null) { this.error.set(this.t('err.DURATION_REQUIRED')); return; }
      body['minutes'] = m;
    }
    const call = this.editId ? this.api.put('/api/entries/' + this.editId, body) : this.api.post('/api/entries', body);
    if (await this.run(() => call.then(() => true), 'common.saved')) { this.resetForm(); await this.load(); }
  }
  async remove(e: Entry): Promise<void> {
    if (!confirm(this.t('common.confirmDelete'))) return;
    if (await this.run(() => this.api.delete('/api/entries/' + e.id).then(() => true), 'common.deleted')) await this.load();
  }
}
