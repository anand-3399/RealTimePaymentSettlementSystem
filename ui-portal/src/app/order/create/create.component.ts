import { Component, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { OrderService } from '../order.service';

@Component({
  selector: 'app-create',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './create.component.html',
  styleUrls: ['../../auth/login/login.component.css', './create.component.css'],
  schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class CreateComponent {
  orderData = { amount: null, currency: 'INR', recipientBankAccount: '', description: '' };
  loading = false;
  errorMessage = '';

  constructor(private orderService: OrderService, private router: Router) {}

  createOrder(): void {
    this.loading = true;
    this.errorMessage = '';
    
    this.orderService.createOrder(this.orderData).subscribe({
      next: (res) => {
        this.loading = false;
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Failed to initiate payment.';
      }
    });
  }
}
