import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Page } from '../core/page';
import { BookableProject, User } from '../core/models';

@Component({
  selector: 'app-settings', imports: [FormsModule],
  template: `
  <h1>{{ t('settings.title') }}</h1>
  @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
  @if (ok()) { <div class="alert ok" role="status">{{ ok() }}</div> }
  <section class="card">
    <form (ngSubmit)="save()" class="stack">
      <div class="row">
        <label>{{ t('settings.language') }}<select name="l" [(ngModel)]="language"><option value="de">Deutsch</option><option value="en">English</option></select></label>
        <label>{{ t('settings.view') }}<select name="v" [(ngModel)]="view"><option value="day">{{ t('nav.day') }}</option><option value="week">{{ t('nav.week') }}</option></select></label>
        <label>{{ t('settings.defaultProject') }}<select name="p" [(ngModel)]="defaultProject">
          <option [ngValue]="null">–</option>@for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.code }} {{ p.name }}</option> }</select></label>
      </div>
      <label class="inline"><input type="checkbox" name="n" [(ngModel)]="notify"> {{ t('settings.notify') }}</label>
      <div><button class="btn" type="submit" [disabled]="busy()">{{ t('common.save') }}</button></div>
    </form>
  </section>
  <section class="card">
    <h2 style="margin-top:0">{{ t('settings.password') }}</h2>
    <form (ngSubmit)="changePw()" class="row">
      <label>{{ t('settings.currentPw') }}<input type="password" name="c" [(ngModel)]="current" autocomplete="current-password"></label>
      <label>{{ t('setpw.new') }}<input type="password" name="nw" [(ngModel)]="next" autocomplete="new-password" minlength="10"></label>
      <button class="btn" type="submit" [disabled]="busy()">{{ t('settings.changePw') }}</button>
    </form>
  </section>
  <section class="card"><h2 style="margin-top:0">{{ t('settings.account') }}</h2>
    <p>{{ user()?.firstName }} {{ user()?.lastName }} · {{ user()?.email }}</p>
    <div class="chips">@for (r of user()?.roles ?? []; track r) { <span class="chip">{{ t('role.' + r) }}</span> }</div>
  </section>`
})
export class SettingsPage extends Page {
  projects = signal<BookableProject[]>([]);
  user = this.auth.user;
  language: 'de' | 'en' = 'de'; view: 'day' | 'week' = 'day'; defaultProject: number | null = null; notify = true;
  current = ''; next = '';
  constructor() {
    super();
    const u = this.auth.user();
    if (u) { this.language = u.language; this.view = u.preferredView; this.defaultProject = u.defaultProjectId ?? null; this.notify = u.notifyBudget; }
    void this.api.get<BookableProject[]>('/api/lookups/projects').then(p => this.projects.set(p));
  }
  async save(): Promise<void> {
    const r = await this.run(() => this.api.put<User>('/api/me/settings', { language: this.language, defaultProjectId: this.defaultProject, preferredView: this.view, notifyBudget: this.notify }), 'common.saved');
    if (r) { this.auth.user.set(r); this.i18n.set(r.language); }
  }
  async changePw(): Promise<void> {
    if (await this.run(() => this.api.post('/api/auth/change-password', { current: this.current, next: this.next }).then(() => true), 'settings.pwChanged')) { this.current = ''; this.next = ''; }
  }
}
