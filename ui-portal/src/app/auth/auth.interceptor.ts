import { HttpInterceptorFn, HttpErrorResponse, HttpRequest, HttpHandlerFn, HttpEvent } from '@angular/common/http';
import { inject } from '@angular/core';
import { throwError, BehaviorSubject, Observable } from 'rxjs';
import { catchError, filter, take, switchMap } from 'rxjs/operators';
import { AuthService } from './auth.service';

let isRefreshing = false;
let refreshTokenSubject: BehaviorSubject<any> = new BehaviorSubject<any>(null);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();
  // Use session correlation ID if logged in, otherwise generate one for the request
  const correlationId = authService.getCorrelationId() || crypto.randomUUID();

  let setHeaders: any = {
    'X-Correlation-ID': correlationId
  };

  // Only attach Bearer token to API calls that are not auth endpoints
  if (token && req.url.startsWith('/api/v1/')) {
    setHeaders['Authorization'] = `Bearer ${token}`;
  }

  const clonedReq = req.clone({ setHeaders });

  return next(clonedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // If error is 401 and it's not the refresh token endpoint itself failing
      if (error.status === 401 && !req.url.includes('/refreshtoken')) {
        return handle401Error(clonedReq, next, authService);
      }
      
      if (error.status === 401 || error.status === 403) {
        authService.logout();
      }
      
      return throwError(() => error);
    })
  );
};

function handle401Error(request: HttpRequest<unknown>, next: HttpHandlerFn, authService: AuthService): Observable<HttpEvent<unknown>> {
  if (!isRefreshing) {
    isRefreshing = true;
    refreshTokenSubject.next(null);

    const refreshToken = authService.getRefreshToken();
    if (refreshToken) {
      return authService.refreshTokenApi(refreshToken).pipe(
        switchMap((res: any) => {
          isRefreshing = false;
          refreshTokenSubject.next(res.token);
          
          // Clone the request again with the new token
          const newTokenReq = request.clone({
            setHeaders: {
              Authorization: `Bearer ${res.token}`
            }
          });
          return next(newTokenReq);
        }),
        catchError((err) => {
          isRefreshing = false;
          authService.logout();
          return throwError(() => err);
        })
      );
    } else {
      isRefreshing = false;
      authService.logout();
      return throwError(() => new Error('No refresh token available'));
    }
  } else {
    // Wait for the subject to emit a non-null token (from the active refresh request)
    return refreshTokenSubject.pipe(
      filter(token => token != null),
      take(1),
      switchMap(token => {
        const retryReq = request.clone({
          setHeaders: {
            Authorization: `Bearer ${token}`
          }
        });
        return next(retryReq);
      })
    );
  }
}
