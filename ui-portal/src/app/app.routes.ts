import { Routes } from '@angular/router';
import { LoginComponent } from './auth/login/login.component';
import { RegisterComponent } from './auth/register/register.component';
import { DashboardComponent } from './order/dashboard/dashboard.component';
import { CreateComponent } from './order/create/create.component';
import { DetailsComponent } from './order/details/details.component';
import { authGuard, publicGuard } from './auth/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  { path: 'login', component: LoginComponent, canActivate: [publicGuard] },
  { path: 'register', component: RegisterComponent, canActivate: [publicGuard] },
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'create-order', component: CreateComponent, canActivate: [authGuard] },
  { path: 'order-details/:id', component: DetailsComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: '/dashboard' }
];
