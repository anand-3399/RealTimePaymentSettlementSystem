import { Component, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './register.component.html',
  styleUrls: ['../login/login.component.css'], // reuse login styles
  schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class RegisterComponent {
  user = { username: '', email: '', password: '', accountNumber: '' };
  errorMessage = '';
  loading = false;
  successMessage = '';

  constructor(private authService: AuthService, private router: Router) {}

  register() {
    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';
    
    this.authService.register(this.user).subscribe({
      next: (res) => {
        this.loading = false;
        this.successMessage = 'Registration successful! Redirecting to login...';
        setTimeout(() => this.router.navigate(['/login']), 2000);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || (typeof err.error === 'string' ? err.error : 'Registration failed.');
      }
    });
  }
}
