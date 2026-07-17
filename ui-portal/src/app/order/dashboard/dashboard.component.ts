import { Component, OnInit, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { OrderService } from '../order.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css'],
  schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class DashboardComponent implements OnInit {
  orders: any[] = [];
  loading = true;
  username: string | null = '';
  
  // Pagination state
  currentPage = 0;
  pageSize = 20;
  totalPages = 0;
  totalElements = 0;
  pageInput = 1;

  constructor(private orderService: OrderService, private authService: AuthService) {}

  ngOnInit(): void {
    this.username = this.authService.getUsername();
    this.loadOrders();
  }

  loadOrders(): void {
    this.loading = true;
    this.orderService.getOrders(this.currentPage, this.pageSize).subscribe({
      next: (res) => {
        // Support both paginated and non-paginated responses gracefully
        if (res.content) {
            this.orders = res.content;
            this.totalPages = res.totalPages;
            this.totalElements = res.totalElements;
            this.currentPage = res.number;
            this.pageInput = this.currentPage + 1;
        } else {
            this.orders = res;
            this.totalElements = res.length;
            this.totalPages = 1;
            this.currentPage = 0;
            this.pageInput = 1;
        }
        this.loading = false;
      },
      error: (err) => {
        console.error('Error fetching orders:', err);
        this.loading = false;
      }
    });
  }
  
  nextPage(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      this.loadOrders();
    }
  }

  previousPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.loadOrders();
    }
  }
  
  goToPage(): void {
    const targetPage = this.pageInput - 1;
    if (targetPage >= 0 && targetPage < this.totalPages && targetPage !== this.currentPage) {
      this.currentPage = targetPage;
      this.loadOrders();
    } else {
      // Reset input to current valid page if invalid
      this.pageInput = this.currentPage + 1;
    }
  }
  
  logout(): void {
    this.authService.logout();
  }

  get successfulOrdersCount(): number {
    return this.orders.filter(o => 
      o.status === 'COMPLETED' || o.status === 'SUCCESS' || o.status === 'PROCESSED'
    ).length;
  }
}
