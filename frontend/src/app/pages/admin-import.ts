import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Page } from '../core/page';

interface Result { imported: number; errors: { line: number; code: string; detail: string }[]; }

@Component({
  selector: 'app-admin-import', imports: [FormsModule],
  template: `
  <h1>{{ t('imp.title') }}</h1>
  <p class="muted">{{ t('imp.help') }}</p>
  @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
  <section class="card">
    <div class="row">
      <label>{{ t('imp.kind') }}<select name="k" [(ngModel)]="kind"><option value="users">{{ t('nav.users') }}</option><option value="projects">{{ t('nav.projects') }}</option></select></label>
      <label class="grow">{{ t('imp.file') }}<input type="file" accept=".csv,.xlsx" (change)="file = $any($event.target).files?.[0] ?? null"></label>
      <button class="btn" type="button" [disabled]="busy() || !file" (click)="upload()">{{ t('imp.upload') }}</button>
    </div>
    <h3>{{ t('imp.columns') }}</h3>
    <pre class="small" style="overflow:auto;background:#f3f5f9;padding:.6rem;border-radius:6px">{{ kind === 'users' ? usersTpl : projectsTpl }}</pre>
    <p class="muted small">{{ kind === 'users' ? t('imp.usersNote') : t('imp.projectsNote') }}</p>
  </section>
  @if (result(); as r) {
    @if (r.errors.length === 0) { <div class="alert ok" role="status">{{ t('imp.done', { n: r.imported }) }}</div> }
    @else {
      <div class="alert error" role="alert">{{ t('imp.failed', { n: r.errors.length }) }}</div>
      <div class="card tablewrap"><table><thead><tr><th>{{ t('imp.line') }}</th><th>{{ t('imp.problem') }}</th></tr></thead>
        <tbody>@for (e of r.errors; track $index) { <tr><td>{{ e.line }}</td><td>{{ errText(e) }}</td></tr> }</tbody></table></div>
    }
  }`
})
export class AdminImportPage extends Page {
  kind: 'users' | 'projects' = 'users';
  file: File | null = null;
  result = signal<Result | null>(null);
  usersTpl = 'email;firstName;lastName;personnelNumber;team;roles;weeklyHours;workingDays;federalState;entryDate;openingBalanceHours\nmax.mustermann@firma.de;Max;Mustermann;4711;Engineering;EMPLOYEE|PROJECT_LEAD;40;;BE;2026-01-01;12,5';
  projectsTpl = 'code;name;description;leadEmail;openToAll;budgetHours;budgetPeriod;members\nWEB;Website;Relaunch;max.mustermann@firma.de;no;200;TOTAL;anna@firma.de|ben@firma.de';
  async upload(): Promise<void> {
    if (!this.file) return;
    const r = await this.run(() => this.api.upload<Result>('/api/admin/import/' + this.kind, this.file!));
    if (r) this.result.set(r);
  }
  errText(e: { code: string; detail: string }): string {
    const txt = this.t('err.' + e.code);
    return (txt === 'err.' + e.code ? e.code : txt) + (e.detail ? ' (' + e.detail + ')' : '');
  }
}
