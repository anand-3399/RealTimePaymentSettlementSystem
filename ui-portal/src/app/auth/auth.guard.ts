import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from './auth.service';
import { catchError, map, of } from 'rxjs';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return true;
  }

  const refreshToken = authService.getRefreshToken();
  if (refreshToken) {
    return authService.refreshTokenApi(refreshToken).pipe(
      map(res => {
        if (res && res.token) {
          return true;
        }
        return router.createUrlTree(['/login']);
      }),
      catchError(() => {
        authService.logout();
        return of(router.createUrlTree(['/login']));
      })
    );
  }

  return router.createUrlTree(['/login']);
};

export const publicGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return router.createUrlTree(['/dashboard']);
  }

  const refreshToken = authService.getRefreshToken();
  if (refreshToken) {
    return authService.refreshTokenApi(refreshToken).pipe(
      map(res => {
        if (res && res.token) {
          return router.createUrlTree(['/dashboard']);
        }
        return true;
      }),
      catchError(() => {
        return of(true); // Allow them to see login/register since refresh failed
      })
    );
  }

  return true;
};
