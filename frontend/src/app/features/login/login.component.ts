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
    <div class="relative hidden overflow-hidden bg-brand-950 lg:block">
      <div class="absolute inset-0"
           style="background:
             radial-gradient(720px 540px at 12% -10%, rgba(47,102,228,.55), transparent 60%),
             radial-gradient(640px 520px at 100% 30%, rgba(16,185,129,.28), transparent 60%),
             radial-gradient(560px 480px at 30% 110%, rgba(26,75,176,.5), transparent 65%);"></div>
      <div class="absolute -right-24 -top-24 h-96 w-96 rounded-full bg-white/5 backdrop-blur-3xl"></div>
      <div class="absolute -bottom-32 -left-16 h-96 w-96 rounded-full bg-white/5 backdrop-blur-3xl"></div>

      <div class="relative flex h-full flex-col justify-between p-12 text-white">
        <div class="flex items-center gap-3 animate-fade-up">
          <img src="assets/logo-mark.svg" class="h-10 w-10 rounded-xl bg-white/90 p-1 shadow-lg" alt="StockCare" />
          <span class="font-display text-2xl font-extrabold tracking-tight">StockCare</span>
        </div>
        <div>
          <h1 class="max-w-md font-display text-4xl font-extrabold leading-[1.15] tracking-tight text-white animate-fade-up xl:text-5xl" style="animation-delay:.1s">
            Predict shortages.<br>
            <span class="bg-gradient-to-r from-accent-300 to-brand-300 bg-clip-text text-transparent">Prioritize</span> what matters.<br>
            Deliver on time.
          </h1>
          <p class="mt-5 max-w-md text-brand-100/90 animate-fade-up" style="animation-delay:.2s">
            A closed-loop platform linking pharmacy demand forecasting, depot prioritization and
            priority-aware last-mile delivery across Tunisia's pharmacies.
          </p>
          <div class="mt-8 flex flex-wrap gap-3 animate-fade-up" style="animation-delay:.3s">
            <div class="flex items-center gap-2 rounded-full border border-white/15 bg-white/10 px-4 py-2 text-sm backdrop-blur-md">
              <span class="material-icons text-[18px] text-accent-300">insights</span> Demand forecasting
            </div>
            <div class="flex items-center gap-2 rounded-full border border-white/15 bg-white/10 px-4 py-2 text-sm backdrop-blur-md">
              <span class="material-icons text-[18px] text-accent-300">route</span> Smart routing
            </div>
            <div class="flex items-center gap-2 rounded-full border border-white/15 bg-white/10 px-4 py-2 text-sm backdrop-blur-md">
              <span class="material-icons text-[18px] text-accent-300">local_shipping</span> Live tracking
            </div>
          </div>
        </div>
        <p class="text-xs text-brand-200/80">© {{ year }} Team Chaneb+ · StockCare</p>
      </div>
    </div>

    <!-- Form -->
    <div class="flex items-center justify-center bg-canvas p-6">
      <div class="w-full max-w-sm animate-fade-up">
        <div class="mb-8 flex justify-center lg:hidden"><app-logo [size]="40"></app-logo></div>
        <h2 class="font-display text-2xl font-bold tracking-tight text-ink">Welcome back</h2>
        <p class="mt-1 text-sm text-ink-mute">Sign in to your StockCare workspace.</p>

        <div class="mt-8 space-y-4">
          <div>
            <label class="label" for="login-email">Email</label>
            <input id="login-email" class="input" [(ngModel)]="email" (keyup.enter)="submit()" autocomplete="username" placeholder="you@stockcare.tn">
          </div>
          <div>
            <label class="label" for="login-password">Password</label>
            <input id="login-password" class="input" type="password" [(ngModel)]="password" (keyup.enter)="submit()" autocomplete="current-password" placeholder="••••••••">
          </div>
          <p *ngIf="error()" class="flex animate-scale-in items-center gap-2 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-600 dark:bg-rose-500/10 dark:text-rose-300" role="alert">
            <span class="material-icons text-[18px]">error</span>{{ error() }}
          </p>
          <button class="btn btn-primary w-full" (click)="submit()" [disabled]="loading()">
            <span *ngIf="loading()" class="material-icons animate-spin text-[18px]">progress_activity</span>
            {{ loading() ? 'Signing in…' : 'Sign in' }}
          </button>
        </div>

        <div class="card mt-6 border-dashed p-4 text-xs text-ink-mute">
          <p class="font-semibold text-ink-soft">Demo accounts</p>
          <div class="mt-2 grid gap-1.5">
            <button class="flex items-center justify-between rounded-lg px-2 py-1.5 text-left transition-colors hover:bg-raised" (click)="fill('ph.tunis@stockcare.tn')">
              <span><span class="badge b-blue mr-1">Pharmacy</span> ph.tunis&#64;stockcare.tn</span><span class="material-icons text-[16px]">login</span>
            </button>
            <button class="flex items-center justify-between rounded-lg px-2 py-1.5 text-left transition-colors hover:bg-raised" (click)="fill('depot@stockcare.tn')">
              <span><span class="badge b-teal mr-1">Depot</span> depot&#64;stockcare.tn</span><span class="material-icons text-[16px]">login</span>
            </button>
          </div>
          <p class="mt-2">Password: <b class="text-ink-soft">Password123!</b></p>
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
