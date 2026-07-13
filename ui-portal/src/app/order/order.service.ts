import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private apiUrl = '/api/v1/orders';

  constructor(private http: HttpClient) { }

  createOrder(data: any): Observable<any> {
    const idempotencyKey = crypto.randomUUID();
    const headers = new HttpHeaders({
      'Idempotency-Key': idempotencyKey
    });
    return this.http.post(this.apiUrl, data, { headers });
  }

  getOrders(page: number = 0, size: number = 20): Observable<any> {
    return this.http.get(`${this.apiUrl}?page=${page}&size=${size}`);
  }

  getOrderById(id: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/${id}`);
  }
}
