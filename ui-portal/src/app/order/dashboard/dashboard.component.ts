import { Component, OnInit, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { OrderService } from '../order.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css'],
  schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class DashboardComponent implements OnInit {
  orders: any[] = [];
  loading = true;
  username: string | null = '';

  constructor(private orderService: OrderService, private authService: AuthService) {}

  ngOnInit(): void {
    this.username = this.authService.getUsername();
    this.loadOrders();
  }

  loadOrders(): void {
    this.loading = true;
    this.orderService.getOrders(0, 50).subscribe({
      next: (res) => {
        this.orders = res.content || res;
        this.loading = false;
      },
      error: (err) => {
        console.error('Error fetching orders:', err);
        this.loading = false;
      }
    });
  }
  
  logout(): void {
    this.authService.logout();
  }
}
