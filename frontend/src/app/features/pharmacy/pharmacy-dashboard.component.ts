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
  <div class="flex items-center justify-between">
    <div>
      <h1>Dashboard</h1>
      <p class="text-sm text-slate-500">{{ auth.user()?.pharmacyName }}</p>
    </div>
    <a class="btn btn-ghost" routerLink="/pharmacy/predictions"><span class="material-icons text-[18px]">insights</span> View predictions</a>
  </div>

  <div class="mt-6 grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-5">
    <div class="card card-p">
      <div class="stat-icon bg-brand-50 text-brand-600"><span class="material-icons">inventory_2</span></div>
      <p class="mt-3 text-3xl font-bold text-slate-900">{{ inv().length }}</p>
      <p class="text-sm text-slate-500">Medications</p>
    </div>
    <div class="card card-p">
      <div class="stat-icon bg-rose-50 text-rose-600"><span class="material-icons">warning</span></div>
      <p class="mt-3 text-3xl font-bold text-rose-600">{{ lowStock() }}</p>
      <p class="text-sm text-slate-500">Low stock</p>
    </div>
    <div class="card card-p">
      <div class="stat-icon bg-amber-50 text-amber-600"><span class="material-icons">insights</span></div>
      <p class="mt-3 text-3xl font-bold text-amber-600">{{ preds().length }}</p>
      <p class="text-sm text-slate-500">Predicted shortages</p>
    </div>
    <div class="card card-p">
      <div class="stat-icon bg-violet-50 text-violet-600"><span class="material-icons">assignment</span></div>
      <p class="mt-3 text-3xl font-bold text-slate-900">{{ activeRequests() }}</p>
      <p class="text-sm text-slate-500">Active requests</p>
    </div>
    <div class="card card-p">
      <div class="stat-icon bg-accent-100 text-accent-600"><span class="material-icons">local_shipping</span></div>
      <p class="mt-3 text-3xl font-bold text-slate-900">{{ deliveries().length }}</p>
      <p class="text-sm text-slate-500">Deliveries</p>
    </div>
  </div>

  <div class="mt-6 grid gap-6 lg:grid-cols-3">
    <div class="card lg:col-span-2">
      <div class="flex items-center justify-between border-b border-slate-100 p-5">
        <p class="card-title">Upcoming shortages</p>
        <a class="text-sm font-medium text-brand-600 hover:underline" routerLink="/pharmacy/predictions">View all</a>
      </div>
      <div class="divide-y divide-slate-100">
        <div *ngFor="let p of preds().slice(0,5)" class="flex items-center gap-4 p-4">
          <div class="stat-icon bg-amber-50 text-amber-600"><span class="material-icons">medication</span></div>
          <div class="flex-1">
            <p class="font-medium text-slate-800">{{ p.medication.name }}</p>
            <p class="text-xs text-slate-500">Stock {{ p.currentStock }} · short by {{ p.predictedMissingQuantity }} around {{ p.predictedShortageDate }}</p>
          </div>
          <span class="badge b-orange">-{{ p.predictedMissingQuantity }}</span>
        </div>
        <div *ngIf="preds().length === 0" class="p-8 text-center text-sm text-slate-400">
          No shortages predicted. Run a prediction to refresh.
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
