import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Api } from './api';
import { I18n } from './i18n';
import { Notification, Role, User } from './models';

@Injectable({ providedIn: 'root' })
export class Auth {
  private api = inject(Api);
  private i18n = inject(I18n);
  private router = inject(Router);
  readonly user = signal<User | null>(null);
  readonly loaded = signal(false);
  readonly notifications = signal<Notification[]>([]);
  readonly unread = computed(() => this.notifications().filter(n => !n.read).length);

  has(...roles: Role[]): boolean { const u = this.user(); return !!u && roles.some(r => u.roles.includes(r)); }
  readonly isAdmin = computed(() => !!this.user()?.roles.includes('BACKOFFICE_ADMIN'));
  readonly isLead = computed(() => !!this.user()?.roles.includes('PROJECT_LEAD'));
  readonly isManagement = computed(() => !!this.user()?.roles.includes('MANAGEMENT'));
  readonly canReport = computed(() => this.isAdmin() || this.isLead() || this.isManagement() || true);

  async restore(): Promise<void> {
    try { this.set(await this.api.get<User>('/api/auth/me')); } catch { this.user.set(null); }
    this.loaded.set(true);
  }
  set(u: User): void {
    this.user.set(u);
    this.i18n.lang.set(u.language);
    void this.loadNotifications();
  }
  async login(email: string, password: string): Promise<void> { this.set(await this.api.post<User>('/api/auth/login', { email, password })); }
  async logout(): Promise<void> {
    try { await this.api.post('/api/auth/logout'); } catch { /* ignore */ }
    this.user.set(null);
    this.notifications.set([]);
    await this.router.navigate(['/login']);
  }
  async loadNotifications(): Promise<void> {
    try { this.notifications.set(await this.api.get<Notification[]>('/api/me/notifications')); } catch { /* ignore */ }
  }
  async setLanguage(lang: 'de' | 'en'): Promise<void> {
    this.i18n.lang.set(lang);
    const u = this.user();
    if (u) this.user.set(await this.api.put<User>('/api/me/settings', { language: lang, defaultProjectId: u.defaultProjectId, preferredView: u.preferredView, notifyBudget: u.notifyBudget }));
  }
  /** Where to go after login. */
  home(): string {
    const u = this.user();
    if (!u) return '/login';
    if (u.roles.length === 1 && u.roles[0] === 'MANAGEMENT') return '/dashboard';
    return u.preferredView === 'week' ? '/week' : '/day';
  }
}
