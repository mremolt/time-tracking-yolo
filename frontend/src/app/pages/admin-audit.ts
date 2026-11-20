import { Component, signal } from '@angular/core';
import { Page } from '../core/page';

interface Log { id: number; at: string; kind: string; actorName: string; entityType: string; entityId?: number; action: string; oldValue?: string; newValue?: string; entityCreatedAt?: string; subjectUserId?: number; }

@Component({
  selector: 'app-admin-audit',
  template: `
  <h1>{{ t('audit.title') }}</h1>
  <div class="tabs" role="tablist">
    <button type="button" role="tab" [attr.aria-selected]="kind() === 'admin-log'" (click)="select('admin-log')">{{ t('audit.adminLog') }}</button>
    <button type="button" role="tab" [attr.aria-selected]="kind() === 'data-log'" (click)="select('data-log')">{{ t('audit.dataLog') }}</button>
  </div>
  @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
  <div class="card tablewrap"><table>
    <thead><tr><th>{{ t('audit.when') }}</th><th>{{ t('audit.who') }}</th><th>{{ t('audit.object') }}</th><th>{{ t('audit.action') }}</th><th>{{ t('audit.old') }}</th><th>{{ t('audit.new') }}</th>@if (kind() === 'data-log') { <th>{{ t('audit.created') }}</th> }</tr></thead>
    <tbody>@for (l of logs(); track l.id) {
      <tr><td class="nowrap">{{ i18n.dateTime(l.at) }}</td><td>{{ l.actorName }}</td><td>{{ l.entityType }} #{{ l.entityId }}</td><td>{{ l.action }}</td>
        <td class="small">{{ l.oldValue }}</td><td class="small">{{ l.newValue }}</td>@if (kind() === 'data-log') { <td class="nowrap small">{{ l.entityCreatedAt ? i18n.dateTime(l.entityCreatedAt) : '' }}</td> }</tr>
    } @empty { <tr><td colspan="7" class="muted">{{ t('common.none') }}</td></tr> }</tbody>
  </table></div>`
})
export class AdminAuditPage extends Page {
  kind = signal<'admin-log' | 'data-log'>('admin-log');
  logs = signal<Log[]>([]);
  constructor() { super(); void this.load(); }
  select(k: 'admin-log' | 'data-log'): void { this.kind.set(k); void this.load(); }
  async load(): Promise<void> { const r = await this.run(() => this.api.get<Log[]>('/api/audit/' + this.kind(), { limit: 300 })); if (r) this.logs.set(r); }
}
