import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Page } from '../core/page';

interface Named { id: number; name: string; active?: boolean; }
interface Hol { id: number; date: string; federalState?: string; nameDe: string; nameEn: string; }

@Component({
  selector: 'app-admin-master', imports: [FormsModule],
  template: `
  <h1>{{ t('master.title') }}</h1>
  @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
  @if (ok()) { <div class="alert ok" role="status">{{ ok() }}</div> }
  <div class="row" style="align-items:flex-start">
    <section class="card grow"><h2 style="margin-top:0">{{ t('master.teams') }}</h2>
      <ul style="list-style:none;padding:0">@for (x of teams(); track x.id) { <li class="row" style="margin-bottom:.3rem">
        <input [value]="x.name" (change)="rename('teams', x, $any($event.target).value)" [attr.aria-label]="t('proj.name')"></li> }</ul>
      <form (ngSubmit)="add('teams', newTeam); newTeam = ''" class="row"><label class="grow">{{ t('master.newTeam') }}<input name="nt" [(ngModel)]="newTeam" required></label><button class="btn" type="submit">{{ t('common.add') }}</button></form></section>
    <section class="card grow"><h2 style="margin-top:0">{{ t('master.activities') }}</h2>
      <ul style="list-style:none;padding:0">@for (x of activities(); track x.id) { <li class="row" style="margin-bottom:.3rem">
        <input [value]="x.name" (change)="rename('activities', x, $any($event.target).value)" [attr.aria-label]="t('proj.name')">
        <label class="inline"><input type="checkbox" [checked]="x.active" (change)="setActive(x, $any($event.target).checked)"> {{ t('users.activeState') }}</label></li> }</ul>
      <form (ngSubmit)="add('activities', newActivity); newActivity = ''" class="row"><label class="grow">{{ t('master.newActivity') }}<input name="na" [(ngModel)]="newActivity" required></label><button class="btn" type="submit">{{ t('common.add') }}</button></form></section>
  </div>
  <section class="card"><h2 style="margin-top:0">{{ t('master.holidays') }}</h2>
    <p class="muted small">{{ t('master.holidaysHelp') }}</p>
    <div class="row">
      <label>{{ t('master.year') }}<input type="number" name="y" [(ngModel)]="year" (change)="loadHolidays()" min="2000" max="2100"></label>
      <button class="btn secondary" type="button" (click)="generate()">{{ t('master.generate') }}</button>
    </div>
    <form (ngSubmit)="addHoliday()" class="row" style="margin:.8rem 0">
      <label>{{ t('common.date') }}<input type="date" name="hd" [(ngModel)]="h.date" required></label>
      <label>{{ t('users.state') }}<select name="hs" [(ngModel)]="h.state"><option value="">{{ t('master.nationwide') }}</option>@for (s of states(); track s) { <option [value]="s">{{ s }}</option> }</select></label>
      <label class="grow">DE<input name="hn" [(ngModel)]="h.nameDe" required></label><label class="grow">EN<input name="he" [(ngModel)]="h.nameEn"></label>
      <button class="btn" type="submit">{{ t('common.add') }}</button>
    </form>
    <div class="tablewrap"><table><thead><tr><th>{{ t('common.date') }}</th><th>{{ t('users.state') }}</th><th>DE</th><th>EN</th><th></th></tr></thead>
      <tbody>@for (x of holidays(); track x.id) { <tr><td>{{ d(x.date) }}</td><td>{{ x.federalState || t('master.nationwide') }}</td><td>{{ x.nameDe }}</td><td>{{ x.nameEn }}</td>
        <td><button class="btn small secondary" type="button" (click)="delHoliday(x)">{{ t('common.delete') }}</button></td></tr> }</tbody></table></div>
  </section>`
})
export class AdminMasterPage extends Page {
  teams = signal<Named[]>([]); activities = signal<Named[]>([]); holidays = signal<Hol[]>([]); states = signal<string[]>([]);
  newTeam = ''; newActivity = '';
  year = new Date().getFullYear();
  h = { date: '', state: '', nameDe: '', nameEn: '' };
  constructor() { super(); void this.init(); }
  async init(): Promise<void> { this.states.set(await this.api.get<string[]>('/api/lookups/states')); await this.load(); await this.loadHolidays(); }
  async load(): Promise<void> {
    const r = await this.run(async () => ({ t: await this.api.get<Named[]>('/api/admin/teams'), a: await this.api.get<Named[]>('/api/admin/activities') }));
    if (r) { this.teams.set(r.t); this.activities.set(r.a); }
  }
  async loadHolidays(): Promise<void> { const r = await this.run(() => this.api.get<Hol[]>('/api/admin/holidays', { year: this.year })); if (r) this.holidays.set(r); }
  async add(kind: 'teams' | 'activities', name: string): Promise<void> { if (await this.run(() => this.api.post('/api/admin/' + kind, { name }).then(() => true), 'common.saved')) await this.load(); }
  async rename(kind: 'teams' | 'activities', x: Named, name: string): Promise<void> {
    if (await this.run(() => this.api.put('/api/admin/' + kind + '/' + x.id, { name, active: x.active }).then(() => true), 'common.saved')) await this.load();
  }
  async setActive(x: Named, active: boolean): Promise<void> { if (await this.run(() => this.api.put('/api/admin/activities/' + x.id, { name: x.name, active }).then(() => true), 'common.saved')) await this.load(); }
  async generate(): Promise<void> {
    const r = await this.run(() => this.api.post<{ created: number }>('/api/admin/holidays/generate?year=' + this.year));
    if (r) { this.ok.set(this.t('master.generated', { n: r.created })); await this.loadHolidays(); }
  }
  async addHoliday(): Promise<void> {
    if (await this.run(() => this.api.post('/api/admin/holidays', { date: this.h.date, federalState: this.h.state || null, nameDe: this.h.nameDe, nameEn: this.h.nameEn || this.h.nameDe }).then(() => true), 'common.saved')) {
      this.h = { date: '', state: '', nameDe: '', nameEn: '' }; await this.loadHolidays();
    }
  }
  async delHoliday(x: Hol): Promise<void> { if (await this.run(() => this.api.delete('/api/admin/holidays/' + x.id).then(() => true))) await this.loadHolidays(); }
}
