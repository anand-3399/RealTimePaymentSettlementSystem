import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private apiUrl = '/api/v1/orders';
  private cachedDashboardState: any = null;

  saveDashboardState(state: any) {
    this.cachedDashboardState = state;
  }

  getDashboardState(): any {
    return this.cachedDashboardState;
  }
  
  clearDashboardState() {
    this.cachedDashboardState = null;
  }

  constructor(private http: HttpClient) { }

  createOrder(data: any): Observable<any> {
    const idempotencyKey = crypto.randomUUID();
    const headers = new HttpHeaders({
      'Idempotency-Key': idempotencyKey
    });
    return this.http.post(this.apiUrl, data, { headers }).pipe(
      tap(() => this.clearDashboardState())
    );
  }

  getOrders(page: number, size: number, startDate?: string, endDate?: string, status?: string, account?: string): Observable<any> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (startDate) {
      params = params.set('startDate', startDate);
    }
    if (endDate) {
      params = params.set('endDate', endDate);
    }
    if (status && status !== 'All') {
      params = params.set('status', status);
    }
    if (account) {
      params = params.set('account', account);
    }

    return this.http.get(this.apiUrl, { params });
  }

  getOrderById(id: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/${id}`).pipe(
      tap((order: any) => {
        if (this.cachedDashboardState && this.cachedDashboardState.orders) {
          const index = this.cachedDashboardState.orders.findIndex((o: any) => o.orderId === order.orderId);
          if (index !== -1) {
            this.cachedDashboardState.orders[index] = order;
          }
        }
      })
    );
  }
}
