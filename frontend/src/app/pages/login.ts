import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Page } from '../core/page';

const LANG = `
  <div class="seg" role="group" [attr.aria-label]="t('nav.language')">
    <button type="button" [class.on]="i18n.lang()==='de'" [attr.aria-pressed]="i18n.lang()==='de'" (click)="i18n.set('de')">DE</button>
    <button type="button" [class.on]="i18n.lang()==='en'" [attr.aria-pressed]="i18n.lang()==='en'" (click)="i18n.set('en')">EN</button>
  </div>`;

@Component({
  selector: 'app-login', imports: [FormsModule, RouterLink],
  template: `
  <div class="login-wrap"><div class="card login-card stack">
    <div class="row between"><h1>⏱ {{ t('app.name') }}</h1>${LANG}</div>
    <form (ngSubmit)="submit()" class="stack">
      @if (route.snapshot.queryParamMap.get('expired')) { <div class="alert info" role="status">{{ t('login.expired') }}</div> }
      @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
      <label>{{ t('login.email') }}<input type="email" name="email" [(ngModel)]="email" autocomplete="username" required autofocus></label>
      <label>{{ t('login.password') }}<input type="password" name="password" [(ngModel)]="password" autocomplete="current-password" required></label>
      <button class="btn" type="submit" [disabled]="busy()">{{ t('login.submit') }}</button>
      <a routerLink="/forgot-password">{{ t('login.forgot') }}</a>
    </form>
  </div></div>`
})
export class LoginPage extends Page {
  route = inject(ActivatedRoute);
  private router = inject(Router);
  email = ''; password = '';
  async submit(): Promise<void> {
    const r = await this.run(() => this.auth.login(this.email, this.password).then(() => true));
    if (r) await this.router.navigateByUrl(this.auth.home());
  }
}

@Component({
  selector: 'app-forgot', imports: [FormsModule, RouterLink],
  template: `
  <div class="login-wrap"><div class="card login-card stack">
    <div class="row between"><h1>{{ t('forgot.title') }}</h1>${LANG}</div>
    @if (sent()) { <div class="alert ok" role="status">{{ t('forgot.sent') }}</div> }
    @else {
      <form (ngSubmit)="submit()" class="stack">
        <p class="muted">{{ t('forgot.help') }}</p>
        @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
        <label>{{ t('login.email') }}<input type="email" name="email" [(ngModel)]="email" required autofocus></label>
        <button class="btn" type="submit" [disabled]="busy()">{{ t('forgot.submit') }}</button>
      </form>
    }
    <a routerLink="/login">{{ t('forgot.back') }}</a>
    <a href="/mailbox">{{ t('mailbox.link') }}</a>
  </div></div>`
})
export class ForgotPage extends Page {
  email = ''; sent = signal(false);
  async submit(): Promise<void> {
    if (await this.run(() => this.api.post('/api/auth/forgot-password', { email: this.email }).then(() => true))) this.sent.set(true);
  }
}

@Component({
  selector: 'app-set-password', imports: [FormsModule, RouterLink],
  template: `
  <div class="login-wrap"><div class="card login-card stack">
    <div class="row between"><h1>{{ invite ? t('invite.title') : t('reset.title') }}</h1>${LANG}</div>
    @if (done()) {
      <div class="alert ok" role="status">{{ t('setpw.done') }}</div>
      <a class="btn" routerLink="/login">{{ t('login.submit') }}</a>
    } @else if (invalid()) {
      <div class="alert error" role="alert">{{ t('err.TOKEN_INVALID') }}</div>
      <a routerLink="/forgot-password">{{ t('login.forgot') }}</a>
    } @else {
      <form (ngSubmit)="submit()" class="stack">
        <p class="muted">{{ t('setpw.help', { min: 10 }) }}</p>
        @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
        <label>{{ t('setpw.new') }}<input type="password" name="pw" [(ngModel)]="pw" autocomplete="new-password" minlength="10" required autofocus></label>
        <label>{{ t('setpw.repeat') }}<input type="password" name="pw2" [(ngModel)]="pw2" autocomplete="new-password" required></label>
        <button class="btn" type="submit" [disabled]="busy()">{{ t('setpw.submit') }}</button>
      </form>
    }
  </div></div>`
})
export class SetPasswordPage extends Page {
  private route = inject(ActivatedRoute);
  invite = !!this.route.snapshot.data['invite'];
  token = this.route.snapshot.queryParamMap.get('token') ?? '';
  pw = ''; pw2 = '';
  done = signal(false); invalid = signal(false);
  constructor() {
    super();
    this.api.get('/api/auth/token-info', { token: this.token, invite: this.invite }).catch(() => this.invalid.set(true));
  }
  async submit(): Promise<void> {
    if (this.pw !== this.pw2) { this.error.set(this.t('setpw.mismatch')); return; }
    const url = this.invite ? '/api/auth/accept-invite' : '/api/auth/reset-password';
    if (await this.run(() => this.api.post(url, { token: this.token, password: this.pw }).then(() => true))) this.done.set(true);
  }
}
