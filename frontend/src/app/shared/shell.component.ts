import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ApiService } from '../core/api.service';
import { StreamService } from '../core/stream.service';
import { ClockStatus } from '../core/models';
import { LogoComponent } from './logo.component';
import { ToastsComponent } from './toast.component';

interface NavItem { label: string; icon: string; link: string; exact?: boolean; }

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, LogoComponent, ToastsComponent],
  template: `
  <div class="min-h-screen lg:grid lg:grid-cols-[264px_1fr]">
    <!-- Sidebar -->
    <aside class="fixed inset-y-0 left-0 z-40 flex w-[264px] transform flex-col border-r border-line bg-surface
                  transition-transform duration-300 ease-out lg:static lg:translate-x-0"
           [class.-translate-x-full]="!open()">
      <div class="flex h-16 items-center gap-2 border-b border-line/70 px-5">
        <app-logo [size]="30"></app-logo>
      </div>
      <nav class="flex-1 overflow-y-auto p-3" aria-label="Main navigation">
        <p class="px-3 pt-2 pb-1 text-[11px] font-semibold uppercase tracking-wider text-ink-faint">
          {{ auth.isDepot() ? 'Depot' : 'Pharmacy' }} workspace
        </p>
        <div class="space-y-1">
          <a *ngFor="let n of nav()" [routerLink]="n.link" (click)="open.set(false)"
             routerLinkActive="active" [routerLinkActiveOptions]="{exact: !!n.exact}" class="nav-link">
            <span class="material-icons">{{ n.icon }}</span>{{ n.label }}
          </a>
        </div>
      </nav>
      <div class="p-3">
        <div class="flex items-center gap-3 rounded-xl bg-raised/70 p-3 ring-1 ring-line/60">
          <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-brand-500 to-accent-500 text-sm font-bold text-white shadow-soft">
            {{ initials() }}
          </div>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-semibold text-ink">{{ auth.user()?.fullName }}</p>
            <p class="truncate text-xs text-ink-mute">{{ scopeLabel() }}</p>
          </div>
          <button class="material-icons rounded-lg p-1 text-ink-faint transition-colors hover:bg-rose-50 hover:text-rose-500 dark:hover:bg-rose-500/10"
                  (click)="logout()" title="Sign out" aria-label="Sign out">logout</button>
        </div>
      </div>
    </aside>

    <!-- Mobile overlay -->
    <div *ngIf="open()" (click)="open.set(false)" class="fixed inset-0 z-30 bg-slate-900/40 backdrop-blur-sm animate-fade-in lg:hidden"></div>

    <!-- Main -->
    <div class="flex min-h-screen flex-col">
      <header class="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-line/70 bg-surface/85 px-4 backdrop-blur-md sm:px-6">
        <button class="material-icons rounded-lg p-1.5 text-ink-mute transition-colors hover:bg-raised lg:hidden"
                (click)="open.set(!open())" aria-label="Toggle menu">menu</button>
        <span class="hidden items-center gap-2 text-sm font-medium text-ink-mute sm:inline-flex">
          <span class="live-dot" title="Live updates connected"></span>
          {{ auth.isDepot() ? 'Depot workspace' : 'Pharmacy workspace' }}
        </span>
        <div class="flex-1"></div>
        <span *ngIf="clock()?.simulationActive"
              class="hidden items-center gap-1.5 rounded-full border border-dashed border-amber-400/60 bg-amber-50 px-3 py-1 text-xs font-medium text-amber-700 dark:bg-amber-500/10 dark:text-amber-300 sm:inline-flex">
          <span class="material-icons text-[16px]">schedule</span>{{ clock()?.effectiveNow | date:'MMM d, HH:mm' }}
        </span>
        <button class="rounded-lg p-2 text-ink-mute transition-all duration-200 hover:bg-raised hover:text-ink"
                (click)="toggleTheme()" [title]="theme() === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'"
                [attr.aria-label]="theme() === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'">
          <span class="material-icons">{{ theme() === 'dark' ? 'light_mode' : 'dark_mode' }}</span>
        </button>
        <a routerLink="/notifications" class="relative rounded-lg p-2 text-ink-mute transition-colors hover:bg-raised hover:text-ink" aria-label="Notifications">
          <span class="material-icons">notifications</span>
          <span *ngIf="unread() > 0"
                class="absolute -right-0.5 -top-0.5 flex h-4 min-w-[16px] animate-scale-in items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white">
            {{ unread() }}
          </span>
        </a>
        <div class="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-brand-500 to-accent-500 text-sm font-bold text-white shadow-soft">
          {{ initials() }}
        </div>
      </header>

      <div *ngIf="clock()?.simulationActive"
           class="border-b border-dashed border-amber-300/70 bg-amber-50/80 px-6 py-1.5 text-center text-xs text-amber-700 dark:border-amber-400/30 dark:bg-amber-500/10 dark:text-amber-300">
        Simulated time is active — {{ clock()?.effectiveNow | date:'medium' }} {{ clock()?.frozen ? '· frozen' : '' }}
      </div>

      <main class="flex-1 px-4 py-6 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-7xl animate-fade-up">
          <router-outlet></router-outlet>
        </div>
      </main>
    </div>
  </div>
  <app-toasts></app-toasts>
  `
})
export class ShellComponent implements OnInit {
  auth = inject(AuthService);
  private api = inject(ApiService);
  private stream = inject(StreamService);
  private router = inject(Router);
  open = signal(false);
  clock = signal<ClockStatus | null>(null);
  unread = signal(0);
  theme = signal<'light' | 'dark'>(
    (document.documentElement.getAttribute('data-theme') as 'light' | 'dark') ?? 'light'
  );

  private pharmacyNav: NavItem[] = [
    { label: 'Dashboard', icon: 'dashboard', link: '/pharmacy', exact: true },
    { label: 'Inventory', icon: 'inventory_2', link: '/pharmacy/inventory' },
    { label: 'Predictions', icon: 'insights', link: '/pharmacy/predictions' },
    { label: 'Requests', icon: 'assignment', link: '/pharmacy/requests' },
    { label: 'Deliveries', icon: 'local_shipping', link: '/pharmacy/deliveries' }
  ];
  private depotNav: NavItem[] = [
    { label: 'Dashboard', icon: 'dashboard', link: '/depot', exact: true },
    { label: 'Requests', icon: 'inbox', link: '/depot/requests' },
    { label: 'Deliveries', icon: 'local_shipping', link: '/depot/deliveries' }
  ];
  nav = computed<NavItem[]>(() => this.auth.isPharmacy() ? this.pharmacyNav : this.depotNav);

  ngOnInit(): void {
    this.api.clock().subscribe((c) => this.clock.set(c));
    this.refreshUnread();
    setInterval(() => this.refreshUnread(), 15000);
    this.stream.connect();
    // Refresh the simulated-time indicator + unread badge on any workflow update.
    this.stream.updates.subscribe(() => {
      this.api.clock().subscribe((c) => this.clock.set(c));
      this.refreshUnread();
    });
  }
  refreshUnread(): void { this.api.unreadCount().subscribe((r) => this.unread.set(r.count)); }
  toggleTheme(): void {
    const next = this.theme() === 'dark' ? 'light' : 'dark';
    this.theme.set(next);
    document.documentElement.setAttribute('data-theme', next);
    try { localStorage.setItem('sc-theme', next); } catch { /* storage unavailable */ }
  }
  initials(): string {
    const n = this.auth.user()?.fullName ?? '';
    return n.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase();
  }
  scopeLabel(): string {
    const u = this.auth.user();
    return u?.pharmacyName ?? u?.depotName ?? (u?.role ?? '');
  }
  logout(): void { this.auth.logout(); this.router.navigate(['/login']); }
}
