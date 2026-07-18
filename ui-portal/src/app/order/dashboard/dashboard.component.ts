import { Component, OnInit, OnDestroy, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
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
export class DashboardComponent implements OnInit, OnDestroy {
  orders: any[] = [];
  loading = true;
  username: string | null = '';
  
  // Pagination state
  currentPage = 0;
  pageSize = 20;
  totalPages = 0;
  totalElements = 0;
  pageInput = 1;
  
  // Filtering state
  isFilterSidebarOpen: boolean = false;
  startDate: string = '';
  endDate: string = '';
  statusFilter: string = 'All';
  accountFilter: string = '';
  totalSuccess: number = 0;
  totalFailure: number = 0;

  constructor(private orderService: OrderService, private authService: AuthService) {}

  ngOnInit(): void {
    this.username = this.authService.getUsername();
    
    const savedState = this.orderService.getDashboardState();
    if (savedState) {
      this.orders = savedState.orders;
      this.currentPage = savedState.currentPage;
      this.pageSize = savedState.pageSize;
      this.totalPages = savedState.totalPages;
      this.totalElements = savedState.totalElements;
      this.pageInput = savedState.pageInput;
      this.startDate = savedState.startDate;
      this.endDate = savedState.endDate;
      this.statusFilter = savedState.statusFilter;
      this.accountFilter = savedState.accountFilter;
      this.totalSuccess = savedState.totalSuccess;
      this.totalFailure = savedState.totalFailure;
      this.loading = false;
    } else {
      this.loadOrders();
    }
  }

  ngOnDestroy(): void {
    this.orderService.saveDashboardState({
      orders: this.orders,
      currentPage: this.currentPage,
      pageSize: this.pageSize,
      totalPages: this.totalPages,
      totalElements: this.totalElements,
      pageInput: this.pageInput,
      startDate: this.startDate,
      endDate: this.endDate,
      statusFilter: this.statusFilter,
      accountFilter: this.accountFilter,
      totalSuccess: this.totalSuccess,
      totalFailure: this.totalFailure
    });
  }

  loadOrders(): void {
    this.loading = true;
    
    let startIso = this.startDate ? new Date(this.startDate).toISOString() : undefined;
    let endIso = this.endDate ? new Date(this.endDate).toISOString() : undefined;

    this.orderService.getOrders(this.currentPage, this.pageSize, startIso, endIso, this.statusFilter, this.accountFilter).subscribe({
      next: (res) => {
        if (res.content) {
            this.orders = res.content;
            this.totalElements = res.total;
            this.totalPages = Math.ceil(res.total / this.pageSize);
            this.totalSuccess = res.success;
            this.totalFailure = res.failure;
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

  applyFilter(): void {
    this.currentPage = 0;
    this.isFilterSidebarOpen = false;
    this.loadOrders();
  }

  clearFilter(): void {
    this.startDate = '';
    this.endDate = '';
    this.statusFilter = 'All';
    this.accountFilter = '';
    this.currentPage = 0;
    this.loadOrders();
  }
  
  toggleFilterSidebar(): void {
    this.isFilterSidebarOpen = !this.isFilterSidebarOpen;
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
    this.orderService.clearDashboardState();
    this.authService.logout();
  }


}
