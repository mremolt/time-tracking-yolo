import { Component, EventEmitter, Input, OnInit, Output, signal } from '@angular/core';
import { Page } from '../core/page';

interface Log { id: number; at: string; actorName: string; action: string; oldValue?: string; newValue?: string; entityCreatedAt?: string; }

/** Change history of one object (H-01). */
@Component({
  selector: 'app-audit-dialog',
  template: `
  <div class="dialog-back" (click)="closed.emit()">
    <div class="dialog" role="dialog" aria-modal="true" [attr.aria-label]="t('audit.history')" (click)="$event.stopPropagation()" (keydown.escape)="closed.emit()">
      <div class="row between"><h2 style="margin:0">{{ t('audit.history') }} ({{ type }} #{{ id }})</h2><button class="btn secondary" type="button" (click)="closed.emit()" autofocus>{{ t('common.close') }}</button></div>
      @if (error()) { <div class="alert error">{{ error() }}</div> }
      <div class="tablewrap"><table>
        <thead><tr><th>{{ t('audit.when') }}</th><th>{{ t('audit.who') }}</th><th>{{ t('audit.action') }}</th><th>{{ t('audit.old') }}</th><th>{{ t('audit.new') }}</th></tr></thead>
        <tbody>@for (l of logs(); track l.id) {
          <tr><td class="nowrap">{{ i18n.dateTime(l.at) }}</td><td>{{ l.actorName }}</td><td>{{ l.action }}</td><td class="small">{{ l.oldValue }}</td><td class="small">{{ l.newValue }}</td></tr>
        } @empty { <tr><td colspan="5" class="muted">{{ t('common.none') }}</td></tr> }</tbody>
      </table></div>
    </div>
  </div>`
})
export class AuditDialog extends Page implements OnInit {
  @Input({ required: true }) type!: string;
  @Input({ required: true }) id!: number;
  @Output() closed = new EventEmitter<void>();
  logs = signal<Log[]>([]);
  async ngOnInit(): Promise<void> {
    const r = await this.run(() => this.api.get<Log[]>(`/api/audit/object/${this.type}/${this.id}`));
    if (r) this.logs.set(r);
  }
}
