import { Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Page } from '../core/page';
import { Role, Simple, User } from '../core/models';
import { today } from '../core/util';
import { AuditDialog } from './audit-dialog';

interface Sched { id: number; validFrom: string; weeklyMinutes: number; workingDays: string[]; federalState: string; }
const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const ROLES: Role[] = ['EMPLOYEE', 'PROJECT_LEAD', 'BACKOFFICE_ADMIN', 'MANAGEMENT'];

@Component({
  selector: 'app-admin-users', imports: [FormsModule, AuditDialog],
  template: `
  <div class="row between"><h1>{{ t('users.title') }}</h1>
    <div class="row"><label class="inline"><input type="checkbox" [ngModel]="showInactive()" (ngModelChange)="showInactive.set($event)" name="si"> {{ t('users.showInactive') }}</label>
    <button class="btn" type="button" (click)="open(null)">+ {{ t('users.new') }}</button></div></div>
  @if (error() && !dlg()) { <div class="alert error" role="alert">{{ error() }}</div> }
  @if (ok() && !dlg()) { <div class="alert ok" role="status">{{ ok() }}</div> }
  <div class="card tablewrap"><table>
    <thead><tr><th>{{ t('users.name') }}</th><th>{{ t('login.email') }}</th><th>{{ t('users.nr') }}</th><th>{{ t('col.team') }}</th><th>{{ t('users.roles') }}</th><th>{{ t('abs.status') }}</th><th></th></tr></thead>
    <tbody>@for (u of shown(); track u.id) {
      <tr [class.inactive]="!u.active">
        <td>{{ u.lastName }}, {{ u.firstName }}</td><td>{{ u.email }}</td><td>{{ u.personnelNumber }}</td><td>{{ u.teamName }}</td>
        <td><div class="chips">@for (r of u.roles; track r) { <span class="chip">{{ t('role.' + r) }}</span> }</div></td>
        <td>@if (!u.active) { <span class="chip err">{{ t('users.inactive') }}</span> } @else if (u.invitePending) { <span class="chip warn">{{ t('users.invited') }}</span> } @else { <span class="chip ok">{{ t('users.activeState') }}</span> }
            @if (u.locked) { <span class="chip err">🔒</span> }</td>
        <td class="nowrap">
          <button class="btn small secondary" type="button" (click)="open(u)">{{ t('common.edit') }}</button>
          <button class="btn small secondary" type="button" (click)="toggle(u)">{{ u.active ? t('users.deactivate') : t('users.reactivate') }}</button>
          @if (u.active && u.invitePending) { <button class="btn small secondary" type="button" (click)="invite(u)">{{ t('users.resend') }}</button> }
          <button class="btn small secondary" type="button" (click)="hist = u.id" [attr.aria-label]="t('audit.history')">🕘</button>
        </td></tr> }</tbody>
  </table></div>

  @if (dlg()) {
    <div class="dialog-back"><div class="dialog stack" role="dialog" aria-modal="true" [attr.aria-label]="editing ? t('users.edit') : t('users.new')" (keydown.escape)="dlg.set(false)">
      <div class="row between"><h2 style="margin:0">{{ editing ? t('users.edit') : t('users.new') }}</h2><button class="btn secondary" type="button" (click)="dlg.set(false)">{{ t('common.close') }}</button></div>
      @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
      @if (ok()) { <div class="alert ok" role="status">{{ ok() }}</div> }
      <form (ngSubmit)="save()" class="stack">
        <div class="row">
          <label class="grow">{{ t('login.email') }}<input type="email" name="email" [(ngModel)]="f.email" required></label>
          <label class="grow">{{ t('users.first') }}<input name="fn" [(ngModel)]="f.firstName" required></label>
          <label class="grow">{{ t('users.last') }}<input name="ln" [(ngModel)]="f.lastName" required></label>
          <label>{{ t('users.nr') }}<input name="nr" [(ngModel)]="f.personnelNumber"></label>
        </div>
        <div class="row">
          <label>{{ t('col.team') }}<select name="team" [(ngModel)]="f.teamId"><option [ngValue]="null">–</option>@for (x of teams(); track x.id) { <option [ngValue]="x.id">{{ x.name }}</option> }</select></label>
          <label>{{ t('users.entry') }}<input type="date" name="en" [(ngModel)]="f.entryDate"></label>
          <label>{{ t('users.exit') }}<input type="date" name="ex" [(ngModel)]="f.exitDate"></label>
          <label>{{ t('users.opening') }}<input name="ob" [(ngModel)]="f.opening" placeholder="0:00" [attr.title]="t('users.openingHelp')"></label>
          <label>{{ t('users.openingDate') }}<input type="date" name="od" [(ngModel)]="f.openingDate"></label>
        </div>
        <fieldset style="border:0;padding:0"><legend class="small muted">{{ t('users.roles') }}</legend><div class="row">
          @for (r of roles; track r) { <label class="inline"><input type="checkbox" [name]="'r'+r" [checked]="f.roles.includes(r)" (change)="toggleRole(r)"> {{ t('role.' + r) }}</label> }</div></fieldset>
        @if (!editing) { <label class="inline"><input type="checkbox" name="inv" [(ngModel)]="f.sendInvite"> {{ t('users.sendInvite') }}</label> }
        <div><button class="btn" type="submit" [disabled]="busy()">{{ t('common.save') }}</button></div>
      </form>

      @if (editing) {
        <h3>{{ t('users.schedules') }}</h3>
        <p class="muted small">{{ t('users.schedulesHelp') }}</p>
        <div class="tablewrap"><table><thead><tr><th>{{ t('users.validFrom') }}</th><th class="num">{{ t('users.weekly') }}</th><th>{{ t('users.days') }}</th><th>{{ t('users.state') }}</th><th></th></tr></thead>
          <tbody>@for (s of schedules(); track s.id) { <tr><td>{{ s.validFrom }}</td><td class="num">{{ hm(s.weeklyMinutes) }}</td><td>{{ dayList(s) }}</td><td>{{ s.federalState }}</td>
            <td><button class="btn small secondary" type="button" (click)="delSchedule(s)">{{ t('common.delete') }}</button></td></tr> }</tbody></table></div>
        <form (ngSubmit)="addSchedule()" class="row">
          <label>{{ t('users.validFrom') }}<input type="date" name="svf" [(ngModel)]="sf.validFrom" required></label>
          <label>{{ t('users.weekly') }}<input name="swh" [(ngModel)]="sf.weekly" placeholder="40" required></label>
          <label>{{ t('users.state') }}<select name="sst" [(ngModel)]="sf.state">@for (s of states(); track s) { <option [value]="s">{{ s }}</option> }</select></label>
          <fieldset style="border:0;padding:0"><legend class="small muted" style="font-weight:600">{{ t('users.days') }}</legend><div class="row">
            @for (dd of days; track dd) { <label class="inline"><input type="checkbox" [name]="'sd'+dd" [checked]="sf.days.includes(dd)" (change)="toggleDay(dd)"> {{ t('day.' + dd) }}</label> }</div></fieldset>
          <button class="btn secondary" type="submit">{{ t('users.addSchedule') }}</button>
        </form>

        <h3>{{ t('users.projects') }}</h3>
        <div class="row">@for (p of allProjects(); track p.id) { <label class="inline"><input type="checkbox" [name]="'up'+p.id" [checked]="assigned().includes(p.id)" (change)="toggleProject(p.id)"> {{ p.name }}</label> }</div>
        <div><button class="btn secondary" type="button" (click)="saveProjects()">{{ t('users.saveProjects') }}</button></div>
      }
    </div></div>
  }
  @if (hist) { <app-audit-dialog type="AppUser" [id]="hist" (closed)="hist = 0" /> }`
})
export class AdminUsersPage extends Page {
  users = signal<User[]>([]);
  showInactive = signal(false);
  shown = computed(() => this.users().filter(u => u.active || this.showInactive()));
  teams = signal<Simple[]>([]);
  allProjects = signal<Simple[]>([]);
  states = signal<string[]>([]);
  schedules = signal<Sched[]>([]);
  assigned = signal<number[]>([]);
  dlg = signal(false);
  hist = 0;
  editing: User | null = null;
  roles = ROLES; days = DAYS;
  f = this.blank();
  sf = { validFrom: today(), weekly: '40', state: 'BE', days: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'] };

  constructor() { super(); void this.init(); }
  blank() { return { email: '', firstName: '', lastName: '', personnelNumber: '', teamId: null as number | null, entryDate: '', exitDate: '', opening: '', openingDate: '', roles: ['EMPLOYEE'] as Role[], sendInvite: true }; }
  async init(): Promise<void> {
    this.teams.set(await this.api.get<Simple[]>('/api/lookups/teams'));
    this.states.set(await this.api.get<string[]>('/api/lookups/states'));
    await this.load();
  }
  async load(): Promise<void> {
    const r = await this.run(async () => ({ users: await this.api.get<User[]>('/api/admin/users'), projects: await this.api.get<{ id: number; code: string; name: string }[]>('/api/admin/projects') }));
    if (r) { this.users.set(r.users); this.allProjects.set(r.projects.map(p => ({ id: p.id, name: `${p.code} ${p.name}` }))); }
  }
  async open(u: User | null): Promise<void> {
    this.error.set(''); this.ok.set('');
    this.editing = u;
    if (!u) { this.f = this.blank(); this.dlg.set(true); return; }
    this.f = { email: u.email, firstName: u.firstName, lastName: u.lastName, personnelNumber: u.personnelNumber ?? '', teamId: u.teamId ?? null, entryDate: u.entryDate ?? '', exitDate: u.exitDate ?? '',
      opening: this.hm(u.openingBalanceMinutes), openingDate: u.openingBalanceDate ?? '', roles: [...u.roles], sendInvite: false };
    this.dlg.set(true);
    await this.loadDetail(u.id);
  }
  async loadDetail(id: number): Promise<void> {
    const d = await this.run(() => this.api.get<{ schedules: Sched[]; projectIds: number[] }>('/api/admin/users/' + id));
    if (d) { this.schedules.set(d.schedules); this.assigned.set(d.projectIds); }
  }
  toggleRole(r: Role): void { this.f.roles = this.f.roles.includes(r) ? this.f.roles.filter(x => x !== r) : [...this.f.roles, r]; }
  toggleDay(d: string): void { this.sf.days = this.sf.days.includes(d) ? this.sf.days.filter(x => x !== d) : [...this.sf.days, d]; }
  toggleProject(id: number): void { this.assigned.update(a => a.includes(id) ? a.filter(x => x !== id) : [...a, id]); }
  dayList(s: Sched): string { return s.workingDays.map(d => this.t('day.' + d + '.short')).join(', '); }

  private minutes(v: string): number {
    const s = (v ?? '').trim().replace(',', '.');
    if (!s) return 0;
    const neg = s.startsWith('-'); const a = s.replace('-', '');
    const m = a.includes(':') ? (+a.split(':')[0] * 60 + +a.split(':')[1]) : Math.round(parseFloat(a) * 60);
    return neg ? -m : m;
  }
  async save(): Promise<void> {
    const f = this.f;
    const body = { email: f.email, firstName: f.firstName, lastName: f.lastName, personnelNumber: f.personnelNumber || null, roles: f.roles, teamId: f.teamId,
      entryDate: f.entryDate || null, exitDate: f.exitDate || null, openingBalanceMinutes: this.minutes(f.opening), openingBalanceDate: f.openingDate || f.entryDate || null, sendInvite: f.sendInvite };
    const r = await this.run(() => this.editing ? this.api.put<User>('/api/admin/users/' + this.editing.id, body) : this.api.post<User>('/api/admin/users', body), 'common.saved');
    if (r) { await this.load(); if (!this.editing) { this.editing = r; await this.loadDetail(r.id); } }
  }
  async toggle(u: User): Promise<void> {
    if (u.active && !confirm(this.t('users.confirmDeactivate', { name: u.firstName + ' ' + u.lastName }))) return;
    if (await this.run(() => this.api.post('/api/admin/users/' + u.id + '/active', { active: !u.active }).then(() => true), 'common.saved')) await this.load();
  }
  async invite(u: User): Promise<void> { await this.run(() => this.api.post('/api/admin/users/' + u.id + '/invite'), 'users.inviteSent'); }
  async addSchedule(): Promise<void> {
    if (!this.editing) return;
    const wm = this.minutes(this.sf.weekly);
    if (await this.run(() => this.api.post('/api/admin/users/' + this.editing!.id + '/schedules', { validFrom: this.sf.validFrom, weeklyMinutes: wm, workingDays: this.sf.days, federalState: this.sf.state }).then(() => true), 'common.saved'))
      await this.loadDetail(this.editing.id);
  }
  async delSchedule(s: Sched): Promise<void> {
    if (!confirm(this.t('common.confirmDelete'))) return;
    if (await this.run(() => this.api.delete('/api/admin/schedules/' + s.id).then(() => true))) await this.loadDetail(this.editing!.id);
  }
  async saveProjects(): Promise<void> { await this.run(() => this.api.put('/api/admin/users/' + this.editing!.id + '/projects', { ids: this.assigned() }), 'common.saved'); }
}
