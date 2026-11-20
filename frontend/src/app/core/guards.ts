import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from './auth';
import { Role } from './models';

// inject() only works synchronously in the injection context, so resolve dependencies before the first await.
export const loggedIn: CanActivateFn = async () => {
  const auth = inject(Auth);
  const router = inject(Router);
  if (!auth.loaded()) await auth.restore();
  return auth.user() ? true : router.parseUrl('/login');
};

export const withRole = (...roles: Role[]): CanActivateFn => async () => {
  const auth = inject(Auth);
  const router = inject(Router);
  if (!auth.loaded()) await auth.restore();
  if (!auth.user()) return router.parseUrl('/login');
  return auth.has(...roles) ? true : router.parseUrl(auth.home());
};
