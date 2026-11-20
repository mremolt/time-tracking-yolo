import { Routes } from '@angular/router';
import { loggedIn, withRole } from './core/guards';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login').then(m => m.LoginPage) },
  { path: 'forgot-password', loadComponent: () => import('./pages/login').then(m => m.ForgotPage) },
  { path: 'reset-password', loadComponent: () => import('./pages/login').then(m => m.SetPasswordPage), data: { invite: false } },
  { path: 'accept-invite', loadComponent: () => import('./pages/login').then(m => m.SetPasswordPage), data: { invite: true } },
  { path: 'mailbox', loadComponent: () => import('./pages/mailbox').then(m => m.MailboxPage) },
  {
    path: '', canActivate: [loggedIn], children: [
      { path: 'day', loadComponent: () => import('./pages/day').then(m => m.DayPage) },
      { path: 'week', loadComponent: () => import('./pages/week').then(m => m.WeekPage) },
      { path: 'absences', loadComponent: () => import('./pages/absences').then(m => m.AbsencesPage) },
      { path: 'summary', loadComponent: () => import('./pages/summary').then(m => m.SummaryPage) },
      { path: 'settings', loadComponent: () => import('./pages/settings').then(m => m.SettingsPage) },
      { path: 'help', loadComponent: () => import('./pages/help').then(m => m.HelpPage) },
      { path: 'reports', loadComponent: () => import('./pages/reports').then(m => m.ReportsPage) },
      { path: 'dashboard', canActivate: [withRole('BACKOFFICE_ADMIN', 'MANAGEMENT')], loadComponent: () => import('./pages/dashboard').then(m => m.DashboardPage) },
      { path: 'admin/users', canActivate: [withRole('BACKOFFICE_ADMIN')], loadComponent: () => import('./pages/admin-users').then(m => m.AdminUsersPage) },
      { path: 'admin/projects', canActivate: [withRole('BACKOFFICE_ADMIN')], loadComponent: () => import('./pages/admin-projects').then(m => m.AdminProjectsPage) },
      { path: 'admin/master', canActivate: [withRole('BACKOFFICE_ADMIN')], loadComponent: () => import('./pages/admin-master').then(m => m.AdminMasterPage) },
      { path: 'admin/import', canActivate: [withRole('BACKOFFICE_ADMIN')], loadComponent: () => import('./pages/admin-import').then(m => m.AdminImportPage) },
      { path: 'admin/periods', canActivate: [withRole('BACKOFFICE_ADMIN')], loadComponent: () => import('./pages/admin-periods').then(m => m.AdminPeriodsPage) },
      { path: 'admin/audit', canActivate: [withRole('BACKOFFICE_ADMIN')], loadComponent: () => import('./pages/admin-audit').then(m => m.AdminAuditPage) },
      { path: '', pathMatch: 'full', redirectTo: 'day' }
    ]
  },
  { path: '**', redirectTo: '' }
];
