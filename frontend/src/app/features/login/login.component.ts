import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { LogoComponent } from '../../shared/logo.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, LogoComponent],
  template: `
  <div class="grid min-h-screen lg:grid-cols-2">
    <!-- Brand panel -->
    <div class="relative hidden overflow-hidden bg-gradient-to-br from-brand-700 via-brand-600 to-accent-600 lg:block">
      <div class="absolute -right-24 -top-24 h-96 w-96 rounded-full bg-white/10"></div>
      <div class="absolute -bottom-32 -left-16 h-96 w-96 rounded-full bg-white/10"></div>
      <div class="relative flex h-full flex-col justify-between p-12 text-white">
        <div class="flex items-center gap-3">
          <img src="assets/logo-mark.svg" class="h-10 w-10 rounded-lg bg-white/90 p-1" alt="StockCare" />
          <span class="text-2xl font-extrabold">StockCare</span>
        </div>
        <div>
          <h1 class="max-w-md text-4xl font-extrabold leading-tight text-white">
            Predict shortages. Prioritize what matters. Deliver on time.
          </h1>
          <p class="mt-4 max-w-md text-brand-100">
            A closed-loop platform linking pharmacy demand forecasting, depot prioritization and
            priority-aware last-mile delivery across Tunisia's pharmacies.
          </p>
          <div class="mt-8 flex gap-6 text-sm text-brand-100">
            <div class="flex items-center gap-2"><span class="material-icons text-accent-300">insights</span> Demand forecasting</div>
            <div class="flex items-center gap-2"><span class="material-icons text-accent-300">route</span> Smart routing</div>
            <div class="flex items-center gap-2"><span class="material-icons text-accent-300">local_shipping</span> Live tracking</div>
          </div>
        </div>
        <p class="text-xs text-brand-200">© {{ year }} Team Chaneb+ · StockCare</p>
      </div>
    </div>

    <!-- Form -->
    <div class="flex items-center justify-center bg-slate-50 p-6">
      <div class="w-full max-w-sm">
        <div class="mb-8 flex justify-center lg:hidden"><app-logo [size]="40"></app-logo></div>
        <h2 class="text-2xl font-bold text-slate-900">Welcome back</h2>
        <p class="mt-1 text-sm text-slate-500">Sign in to your StockCare workspace.</p>

        <div class="mt-8 space-y-4">
          <div>
            <label class="label">Email</label>
            <input class="input" [(ngModel)]="email" (keyup.enter)="submit()" autocomplete="username" placeholder="you@stockcare.tn">
          </div>
          <div>
            <label class="label">Password</label>
            <input class="input" type="password" [(ngModel)]="password" (keyup.enter)="submit()" autocomplete="current-password" placeholder="••••••••">
          </div>
          <p *ngIf="error()" class="flex items-center gap-2 text-sm text-rose-600">
            <span class="material-icons text-[18px]">error</span>{{ error() }}
          </p>
          <button class="btn btn-primary w-full" (click)="submit()" [disabled]="loading()">
            <span *ngIf="loading()" class="material-icons animate-spin text-[18px]">progress_activity</span>
            {{ loading() ? 'Signing in…' : 'Sign in' }}
          </button>
        </div>

        <div class="mt-6 rounded-xl border border-dashed border-slate-200 bg-white p-4 text-xs text-slate-500">
          <p class="font-semibold text-slate-600">Demo accounts</p>
          <div class="mt-2 grid gap-1.5">
            <button class="flex items-center justify-between rounded-lg px-2 py-1 text-left hover:bg-slate-50" (click)="fill('ph.tunis@stockcare.tn')">
              <span><span class="badge b-blue mr-1">Pharmacy</span> ph.tunis&#64;stockcare.tn</span><span class="material-icons text-[16px]">login</span>
            </button>
            <button class="flex items-center justify-between rounded-lg px-2 py-1 text-left hover:bg-slate-50" (click)="fill('depot@stockcare.tn')">
              <span><span class="badge b-teal mr-1">Depot</span> depot&#64;stockcare.tn</span><span class="material-icons text-[16px]">login</span>
            </button>
          </div>
          <p class="mt-2">Password: <b class="text-slate-700">Password123!</b></p>
        </div>
      </div>
    </div>
  </div>`
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  email = 'ph.tunis@stockcare.tn';
  password = 'Password123!';
  loading = signal(false);
  error = signal('');
  year = new Date().getFullYear();

  fill(email: string): void { this.email = email; this.password = 'Password123!'; }

  submit(): void {
    this.loading.set(true); this.error.set('');
    this.auth.login(this.email, this.password).subscribe({
      next: () => { this.loading.set(false); this.router.navigate(['/']); },
      error: () => { this.loading.set(false); this.error.set('Invalid email or password'); }
    });
  }
}
