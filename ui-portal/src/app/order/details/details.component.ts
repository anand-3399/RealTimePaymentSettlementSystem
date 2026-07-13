import { Component, OnInit, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { OrderService } from '../order.service';

@Component({
  selector: 'app-details',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './details.component.html',
  styleUrls: ['../../auth/login/login.component.css', './details.component.css'],
  schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class DetailsComponent implements OnInit {
  order: any = null;
  loading = true;
  errorMessage = '';

  constructor(
    private route: ActivatedRoute, 
    private orderService: OrderService,
    private router: Router
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.fetchOrderDetails(id);
    } else {
      this.router.navigate(['/dashboard']);
    }
  }

  fetchOrderDetails(id: string): void {
    this.loading = true;
    this.orderService.getOrderById(id).subscribe({
      next: (res) => {
        this.order = res;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = 'Could not load order details.';
      }
    });
  }
}
