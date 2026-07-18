import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { StreamService } from '../../core/stream.service';
import { TimeControlComponent } from '../../shared/time-control.component';
import { InventoryItem, PharmacyRequest, Prediction, Delivery } from '../../core/models';
import { statusClass } from '../../shared/status-badge';

@Component({
  selector: 'app-pharmacy-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, TimeControlComponent],
  template: `
  <div class="flex flex-wrap items-center justify-between gap-3">
    <div>
      <h1>Dashboard</h1>
      <p class="text-sm text-ink-mute">{{ auth.user()?.pharmacyName }}</p>
    </div>
    <a class="btn btn-ghost" routerLink="/pharmacy/predictions"><span class="material-icons text-[18px]">insights</span> View predictions</a>
  </div>

  <div class="stagger mt-6 grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-5">
    <div class="card card-hover card-p group">
      <div class="stat-icon t-brand group-hover:scale-110"><span class="material-icons">inventory_2</span></div>
      <p class="mt-3 font-display text-3xl font-bold text-ink">{{ inv().length }}</p>
      <p class="text-sm text-ink-mute">Medications</p>
    </div>
    <div class="card card-hover card-p group">
      <div class="stat-icon t-rose group-hover:scale-110"><span class="material-icons">warning</span></div>
      <p class="mt-3 font-display text-3xl font-bold text-rose-600 dark:text-rose-400">{{ lowStock() }}</p>
      <p class="text-sm text-ink-mute">Low stock</p>
    </div>
    <div class="card card-hover card-p group">
      <div class="stat-icon t-amber group-hover:scale-110"><span class="material-icons">insights</span></div>
      <p class="mt-3 font-display text-3xl font-bold text-amber-600 dark:text-amber-400">{{ preds().length }}</p>
      <p class="text-sm text-ink-mute">Predicted shortages</p>
    </div>
    <div class="card card-hover card-p group">
      <div class="stat-icon t-violet group-hover:scale-110"><span class="material-icons">assignment</span></div>
      <p class="mt-3 font-display text-3xl font-bold text-ink">{{ activeRequests() }}</p>
      <p class="text-sm text-ink-mute">Active requests</p>
    </div>
    <div class="card card-hover card-p group">
      <div class="stat-icon t-accent group-hover:scale-110"><span class="material-icons">local_shipping</span></div>
      <p class="mt-3 font-display text-3xl font-bold text-ink">{{ deliveries().length }}</p>
      <p class="text-sm text-ink-mute">Deliveries</p>
    </div>
  </div>

  <div class="mt-6 grid gap-6 lg:grid-cols-3">
    <div class="card lg:col-span-2">
      <div class="flex items-center justify-between border-b border-line/70 p-5">
        <p class="card-title">Upcoming shortages</p>
        <a class="text-sm font-medium text-brand-600 transition-colors hover:text-brand-700 hover:underline dark:text-brand-300" routerLink="/pharmacy/predictions">View all</a>
      </div>
      <div class="stagger divide-y divide-line/70">
        <div *ngFor="let p of preds().slice(0,5)" class="flex items-center gap-4 p-4 transition-colors hover:bg-raised/40">
          <div class="stat-icon t-amber"><span class="material-icons">medication</span></div>
          <div class="flex-1">
            <p class="font-medium text-ink">{{ p.medication.name }}</p>
            <p class="text-xs text-ink-mute">Stock {{ p.currentStock }} · short by {{ p.predictedMissingQuantity }} around {{ p.predictedShortageDate }}</p>
          </div>
          <span class="badge b-orange">-{{ p.predictedMissingQuantity }}</span>
        </div>
        <div *ngIf="preds().length === 0" class="p-8 text-center text-sm text-ink-faint">
          <span class="material-icons text-3xl text-accent-400">verified</span>
          <p class="mt-2">No shortages predicted — forecasts refresh automatically as your data changes.</p>
        </div>
      </div>
    </div>
    <app-time-control (changed)="load()"></app-time-control>
  </div>`
})
export class PharmacyDashboardComponent implements OnInit {
  private api = inject(ApiService);
  auth = inject(AuthService);
  private stream = inject(StreamService);
  inv = signal<InventoryItem[]>([]);
  preds = signal<Prediction[]>([]);
  reqs = signal<PharmacyRequest[]>([]);
  deliveries = signal<Delivery[]>([]);
  cls = statusClass;

  ngOnInit(): void {
    this.load();
    this.stream.connect();
    this.stream.updates.subscribe(() => this.load());
  }
  load(): void {
    this.api.myInventory().subscribe((r) => this.inv.set(r));
    this.api.myPredictions().subscribe((r) => this.preds.set(r));
    this.api.myRequests().subscribe((r) => this.reqs.set(r.content));
    this.api.myDeliveries().subscribe((r) => this.deliveries.set(r));
  }
  lowStock(): number { return this.inv().filter((i) => i.lowStock).length; }
  activeRequests(): number { return this.reqs().filter((r) => !['DELIVERED', 'CANCELLED', 'REJECTED'].includes(r.status)).length; }
}
