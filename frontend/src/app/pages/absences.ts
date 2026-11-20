import { Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Page } from '../core/page';
import { Day, Simple } from '../core/models';
import { monthEnd, monthStart, today, addDays, parseIso, iso } from '../core/util';

@Component({
  selector: 'app-absences', imports: [FormsModule],
  template: `
  <div class="row between">
    <h1>{{ t('abs.title') }}</h1>
    <div class="row">
      @if (auth.isAdmin()) {
        <label>{{ t('day.forUser') }}<select [ngModel]="userId()" (ngModelChange)="userId.set($event); load()" name="u">
          <option [ngValue]="null">{{ t('day.myself') }}</option>@for (u of users(); track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }</select></label>
      }
      <button class="btn secondary" type="button" (click)="shift(-1)" [attr.aria-label]="t('common.prev')">‹</button>
      <strong>{{ d(month(), {month:'long', year:'numeric'}) }}</strong>
      <button class="btn secondary" type="button" (click)="shift(1)" [attr.aria-label]="t('common.next')">›</button>
    </div>
  </div>
  <p class="muted">{{ t('abs.help') }}</p>
  @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
  <div class="card">
    <div class="row">
      <label>{{ t('abs.from') }}<input type="date" name="f" [(ngModel)]="from"></label>
      <label>{{ t('abs.to') }}<input type="date" name="t" [(ngModel)]="to"></label>
      <label>{{ t('abs.type') }}<select name="ty" [(ngModel)]="type">
        @for (a of types; track a) { <option [value]="a">{{ t('absence.' + a) }}</option> }</select></label>
      <label class="inline"><input type="checkbox" name="h" [(ngModel)]="half"> {{ t('abs.half') }}</label>
      <button class="btn" type="button" [disabled]="busy()" (click)="mark()">{{ t('abs.mark') }}</button>
    </div>
    <p class="muted small">{{ t('abs.rangeHelp') }}</p>
  </div>
  <div class="card tablewrap">
    <table>
      <thead><tr><th>{{ t('common.date') }}</th><th>{{ t('abs.status') }}</th><th class="num">{{ t('day.target') }}</th><th></th></tr></thead>
      <tbody>
        @for (dy of days(); track dy.date) {
          <tr [class.weekend]="dy.baseTargetMinutes === 0 && !dy.absence" [class.today]="dy.date === todayIso">
            <td>{{ d(dy.date) }}</td>
            <td>@if (dy.absence) { <span class="chip">{{ t('absence.' + dy.absence) }}{{ dy.absenceHalf ? ' ½' : '' }}</span> }
                @if (holiday(dy)) { <span class="chip ok">🎉 {{ holiday(dy) }}</span> }</td>
            <td class="num">{{ hm(dy.targetMinutes) }}</td>
            <td class="right">@if (dy.absence) { <button class="btn small secondary" type="button" (click)="clear(dy.date)">{{ t('abs.remove') }}</button> }</td>
          </tr>
        }
      </tbody>
    </table>
  </div>`
})
export class AbsencesPage extends Page {
  todayIso = today();
  month = signal(monthStart(today()));
  days = signal<Day[]>([]);
  users = signal<Simple[]>([]);
  userId = signal<number | null>(null);
  types = ['VACATION', 'SICK', 'HOLIDAY', 'OTHER'];
  type = 'VACATION'; half = false;
  from = today(); to = today();
  constructor() { super(); void this.init(); }
  async init(): Promise<void> {
    if (this.auth.isAdmin()) this.users.set(await this.api.get<Simple[]>('/api/lookups/users'));
    await this.load();
  }
  async load(): Promise<void> {
    const r = await this.run(() => this.api.get<Day[]>('/api/days', { from: this.month(), to: monthEnd(this.month()), userId: this.userId() ?? undefined }));
    if (r) this.days.set(r);
  }
  shift(n: number): void { const d = parseIso(this.month()); this.month.set(iso(new Date(d.getFullYear(), d.getMonth() + n, 1))); void this.load(); }
  holiday(d: Day): string | undefined { return this.i18n.lang() === 'de' ? d.holidayDe : d.holidayEn; }
  async mark(): Promise<void> {
    if (!this.from || !this.to || this.to < this.from) { this.error.set(this.t('err.DATE_RANGE_INVALID')); return; }
    const info = await this.run(async () => {
      // only days with a working-day target can be marked; weekends and public holidays are skipped
      const days = await this.api.get<Day[]>('/api/days', { from: this.from, to: this.to, userId: this.userId() ?? undefined });
      let n = 0;
      for (const dy of days) {
        if (dy.baseTargetMinutes === 0 && this.type !== 'HOLIDAY') continue;
        await this.api.put('/api/absences', { userId: this.userId(), date: dy.date, type: this.type, halfDay: this.half });
        n++;
      }
      return n;
    });
    if (info !== undefined) { this.ok.set(this.t('abs.marked', { n: info })); await this.load(); }
  }
  async clear(date: string): Promise<void> {
    if (await this.run(() => this.api.delete('/api/absences', { date, userId: this.userId() ?? undefined }).then(() => true))) await this.load();
  }
}
