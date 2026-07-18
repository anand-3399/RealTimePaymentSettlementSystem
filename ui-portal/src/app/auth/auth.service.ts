import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import { OrderService } from '../order/order.service';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = '/api/auth';
  private tokenKey = 'rtps_token';
  private refreshTokenKey = 'rtps_refresh_token';
  private userKey = 'rtps_username';
  private correlationIdKey = 'rtps_correlation_id';

  constructor(private http: HttpClient, private router: Router, private orderService: OrderService) { }

  register(data: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/registeruser`, data, { responseType: 'text' });
  }

  login(data: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/loginuser`, data).pipe(
      tap((res: any) => {
        if (res && res.token) {
          localStorage.setItem(this.tokenKey, res.token);
          localStorage.setItem(this.refreshTokenKey, res.refreshToken);
          localStorage.setItem(this.userKey, res.username);
          if (!localStorage.getItem(this.correlationIdKey)) {
            localStorage.setItem(this.correlationIdKey, crypto.randomUUID());
          }
          this.orderService.clearDashboardState();
        }
      })
    );
  }

  logout(): void {
    const refreshToken = this.getRefreshToken();
    if (refreshToken) {
      this.http.post(`${this.apiUrl}/logoutuser`, { refreshToken }).subscribe({
        next: () => this.clearLocalData(),
        error: () => this.clearLocalData()
      });
    } else {
      this.clearLocalData();
    }
  }

  private clearLocalData(): void {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.refreshTokenKey);
    localStorage.removeItem(this.userKey);
    localStorage.removeItem(this.correlationIdKey);
    this.orderService.clearDashboardState();
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.refreshTokenKey);
  }

  refreshTokenApi(refreshToken: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/refreshtoken`, { refreshToken }).pipe(
      tap((res: any) => {
        if (res && res.token) {
          localStorage.setItem(this.tokenKey, res.token);
          if (res.refreshToken) {
            localStorage.setItem(this.refreshTokenKey, res.refreshToken);
          }
        }
      })
    );
  }

  getUsername(): string | null {
    return localStorage.getItem(this.userKey);
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  getCorrelationId(): string | null {
    return localStorage.getItem(this.correlationIdKey);
  }
}
