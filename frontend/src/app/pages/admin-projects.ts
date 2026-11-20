import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Page } from '../core/page';
import { Simple } from '../core/models';
import { AuditDialog } from './audit-dialog';

interface Proj { id: number; code: string; name: string; description?: string; leadId?: number; leadName?: string; startDate?: string; endDate?: string; archived: boolean;
  openToAll: boolean; internal: boolean; budgetMinutes?: number; budgetPeriod: 'TOTAL' | 'MONTHLY'; memberIds: number[]; usedMinutes?: number; }

@Component({
  selector: 'app-admin-projects', imports: [FormsModule, AuditDialog],
  template: `
  <div class="row between"><h1>{{ t('proj.title') }}</h1>
    <div class="row"><label class="inline"><input type="checkbox" [ngModel]="showArchived()" (ngModelChange)="showArchived.set($event)" name="sa"> {{ t('proj.showArchived') }}</label>
    <button class="btn" type="button" (click)="open(null)">+ {{ t('proj.new') }}</button></div></div>
  @if (error() && !dlg()) { <div class="alert error" role="alert">{{ error() }}</div> }
  @if (ok() && !dlg()) { <div class="alert ok" role="status">{{ ok() }}</div> }
  <div class="card tablewrap"><table>
    <thead><tr><th>{{ t('proj.code') }}</th><th>{{ t('proj.name') }}</th><th>{{ t('col.lead') }}</th><th>{{ t('proj.access') }}</th><th class="num">{{ t('col.budget') }}</th><th class="num">{{ t('col.used') }}</th><th></th></tr></thead>
    <tbody>@for (p of shown(); track p.id) {
      <tr [class.inactive]="p.archived">
        <td>{{ p.code }}</td><td>{{ p.name }} @if (p.archived) { <span class="chip">{{ t('proj.archived') }}</span> } @if (p.internal) { <span class="chip">{{ t('proj.internal') }}</span> }</td>
        <td>{{ p.leadName }}</td><td>{{ p.openToAll ? t('proj.openToAll') : t('proj.members', { n: p.memberIds.length }) }}</td>
        <td class="num">{{ p.budgetMinutes ? hm(p.budgetMinutes) + (p.budgetPeriod === 'MONTHLY' ? ' /' + t('proj.month') : '') : '' }}</td>
        <td class="num" [class.neg]="p.budgetMinutes && (p.usedMinutes ?? 0) >= p.budgetMinutes">{{ p.usedMinutes != null ? hm(p.usedMinutes) : '' }}</td>
        <td class="nowrap"><button class="btn small secondary" type="button" (click)="open(p)">{{ t('common.edit') }}</button>
          <button class="btn small secondary" type="button" (click)="archive(p)">{{ p.archived ? t('proj.unarchive') : t('proj.archive') }}</button>
          <button class="btn small secondary" type="button" (click)="hist = p.id" [attr.aria-label]="t('audit.history')">🕘</button></td></tr> }</tbody>
  </table></div>

  @if (dlg()) {
    <div class="dialog-back"><div class="dialog stack" role="dialog" aria-modal="true" (keydown.escape)="dlg.set(false)" [attr.aria-label]="editing ? t('proj.edit') : t('proj.new')">
      <div class="row between"><h2 style="margin:0">{{ editing ? t('proj.edit') : t('proj.new') }}</h2><button class="btn secondary" type="button" (click)="dlg.set(false)">{{ t('common.close') }}</button></div>
      @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
      @if (ok()) { <div class="alert ok" role="status">{{ ok() }}</div> }
      <form (ngSubmit)="save()" class="stack">
        <div class="row">
          <label>{{ t('proj.code') }}<input name="c" [(ngModel)]="f.code" required maxlength="20"></label>
          <label class="grow">{{ t('proj.name') }}<input name="n" [(ngModel)]="f.name" required></label>
          <label>{{ t('col.lead') }}<select name="l" [(ngModel)]="f.leadId"><option [ngValue]="null">–</option>@for (u of users(); track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }</select></label>
        </div>
        <label>{{ t('entry.description') }}<textarea name="d" rows="2" [(ngModel)]="f.description"></textarea></label>
        <div class="row">
          <label>{{ t('proj.start') }}<input type="date" name="s" [(ngModel)]="f.startDate"></label>
          <label>{{ t('proj.end') }}<input type="date" name="e" [(ngModel)]="f.endDate"></label>
          <label>{{ t('col.budget') }}<input name="b" [(ngModel)]="f.budget" placeholder="{{ t('proj.noBudget') }}"></label>
          <label>{{ t('col.period') }}<select name="bp" [(ngModel)]="f.period"><option value="TOTAL">{{ t('period.TOTAL') }}</option><option value="MONTHLY">{{ t('period.MONTHLY') }}</option></select></label>
          <label class="inline"><input type="checkbox" name="o" [(ngModel)]="f.openToAll"> {{ t('proj.openToAll') }}</label>
        </div>
        <div><button class="btn" type="submit" [disabled]="busy()">{{ t('common.save') }}</button></div>
      </form>
      @if (editing) {
        <h3>{{ t('proj.membersTitle') }}</h3>
        @if (editing.openToAll) { <p class="muted small">{{ t('proj.openHelp') }}</p> }
        <div class="row">@for (u of users(); track u.id) { <label class="inline"><input type="checkbox" [name]="'m'+u.id" [checked]="members().includes(u.id)" (change)="toggle(u.id)"> {{ u.name }}</label> }</div>
        <div><button class="btn secondary" type="button" (click)="saveMembers()">{{ t('proj.saveMembers') }}</button></div>
      }
    </div></div>
  }
  @if (hist) { <app-audit-dialog type="Project" [id]="hist" (closed)="hist = 0" /> }`
})
export class AdminProjectsPage extends Page {
  projects = signal<Proj[]>([]);
  users = signal<Simple[]>([]);
  showArchived = signal(false);
  members = signal<number[]>([]);
  dlg = signal(false);
  editing: Proj | null = null;
  hist = 0;
  f = this.blank();
  shown = () => this.projects().filter(p => !p.archived || this.showArchived());
  constructor() { super(); void this.init(); }
  blank() { return { code: '', name: '', description: '', leadId: null as number | null, startDate: '', endDate: '', budget: '', period: 'TOTAL', openToAll: false }; }
  async init(): Promise<void> { this.users.set((await this.api.get<Simple[]>('/api/lookups/users')).filter(u => !u.name.endsWith('(inactive)'))); await this.load(); }
  async load(): Promise<void> { const r = await this.run(() => this.api.get<Proj[]>('/api/admin/projects')); if (r) this.projects.set(r); }
  open(p: Proj | null): void {
    this.error.set(''); this.ok.set(''); this.editing = p;
    this.f = p ? { code: p.code, name: p.name, description: p.description ?? '', leadId: p.leadId ?? null, startDate: p.startDate ?? '', endDate: p.endDate ?? '',
      budget: p.budgetMinutes ? this.hm(p.budgetMinutes) : '', period: p.budgetPeriod, openToAll: p.openToAll } : this.blank();
    this.members.set(p ? [...p.memberIds] : []);
    this.dlg.set(true);
  }
  toggle(id: number): void { this.members.update(m => m.includes(id) ? m.filter(x => x !== id) : [...m, id]); }
  private minutes(v: string): number | null {
    const s = (v ?? '').trim().replace(',', '.'); if (!s) return null;
    return s.includes(':') ? +s.split(':')[0] * 60 + +s.split(':')[1] : Math.round(parseFloat(s) * 60);
  }
  async save(): Promise<void> {
    const f = this.f;
    const body = { code: f.code, name: f.name, description: f.description, leadId: f.leadId, startDate: f.startDate || null, endDate: f.endDate || null,
      openToAll: f.openToAll, internal: this.editing?.internal ?? false, budgetMinutes: this.minutes(f.budget), budgetPeriod: f.period };
    const r = await this.run(() => this.editing ? this.api.put<Proj>('/api/admin/projects/' + this.editing.id, body) : this.api.post<Proj>('/api/admin/projects', body), 'common.saved');
    if (r) { await this.load(); this.editing = this.projects().find(p => p.id === r.id) ?? null; }
  }
  async saveMembers(): Promise<void> { await this.run(() => this.api.put('/api/admin/projects/' + this.editing!.id + '/members', { ids: this.members() }), 'common.saved'); await this.load(); }
  async archive(p: Proj): Promise<void> {
    if (!p.archived && !confirm(this.t('proj.confirmArchive', { name: p.name }))) return;
    if (await this.run(() => this.api.post('/api/admin/projects/' + p.id + '/archived', { active: !p.archived }).then(() => true), 'common.saved')) await this.load();
  }
}
