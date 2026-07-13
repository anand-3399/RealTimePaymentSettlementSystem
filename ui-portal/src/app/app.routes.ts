import { Routes } from '@angular/router';
import { LoginComponent } from './auth/login/login.component';
import { RegisterComponent } from './auth/register/register.component';
import { DashboardComponent } from './order/dashboard/dashboard.component';
import { CreateComponent } from './order/create/create.component';
import { DetailsComponent } from './order/details/details.component';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'create-order', component: CreateComponent },
  { path: 'order-details/:id', component: DetailsComponent },
  { path: '**', redirectTo: '/login' }
];
