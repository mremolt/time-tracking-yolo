import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet, NavigationEnd } from '@angular/router';
import { Auth } from './core/auth';
import { I18n } from './core/i18n';
import { Api } from './core/api';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html'
})
export class App {
  auth = inject(Auth);
  i18n = inject(I18n);
  private api = inject(Api);
  private router = inject(Router);
  menuOpen = signal(false);
  bellOpen = signal(false);
  showShell = signal(false);

  constructor() {
    document.documentElement.lang = this.i18n.lang();
    this.router.events.subscribe(e => {
      if (e instanceof NavigationEnd) {
        this.menuOpen.set(false);
        this.showShell.set(!/^\/(login|forgot-password|reset-password|accept-invite|mailbox)/.test(e.urlAfterRedirects));
        if (this.auth.user()) void this.auth.loadNotifications();
      }
    });
  }

  initials = computed(() => { const u = this.auth.user(); return u ? (u.firstName[0] + u.lastName[0]).toUpperCase() : ''; });

  async lang(l: 'de' | 'en'): Promise<void> {
    this.i18n.set(l);
    if (this.auth.user()) await this.auth.setLanguage(l);
  }
  async toggleBell(): Promise<void> {
    this.bellOpen.update(v => !v);
    if (this.bellOpen() && this.auth.unread() > 0) {
      await this.api.post('/api/me/notifications/read');
      setTimeout(() => void this.auth.loadNotifications(), 3000);
    }
  }
  notifText(n: { messageKey: string; params: string }): string {
    const [project, threshold] = (n.params ?? '').split('|');
    return this.i18n.t('notif.' + n.messageKey, { project, threshold });
  }
}
