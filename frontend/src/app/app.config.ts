import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { routes } from './app.routes';
import { Auth } from './core/auth';

/** A 401 on a protected call (session timeout, deactivated user) sends the user to the login page (A-04, A-08). */
export const unauthorizedInterceptor = (req: any, next: any) => {
  const router = inject(Router);
  const auth = inject(Auth);
  return next(req).pipe(catchError((e: unknown) => {
    if (e instanceof HttpErrorResponse && e.status === 401 && !req.url.includes('/api/auth/')) {
      auth.user.set(null);
      void router.navigate(['/login'], { queryParams: { expired: 1 } });
    }
    return throwError(() => e);
  }));
};

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([unauthorizedInterceptor]))
  ]
};
