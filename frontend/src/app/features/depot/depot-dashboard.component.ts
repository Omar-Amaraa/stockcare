import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { StreamService } from '../../core/stream.service';
import { Depot, PharmacyRequest, Delivery } from '../../core/models';
import { TimeControlComponent } from '../../shared/time-control.component';
import { statusClass } from '../../shared/status-badge';

@Component({
  selector: 'app-depot-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, TimeControlComponent],
  template: `
  <div class="flex items-center justify-between">
    <div><h1>Depot dashboard</h1><p class="text-sm text-slate-500">{{ depot()?.name }} · {{ depot()?.region }}</p></div>
    <button class="btn btn-primary" routerLink="/depot/requests"><span class="material-icons text-[18px]">inbox</span> Manage requests</button>
  </div>

  <div class="mt-6 grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-5">
    <div class="card card-p"><div class="stat-icon bg-brand-50 text-brand-600"><span class="material-icons">storefront</span></div>
      <p class="mt-3 text-3xl font-bold text-slate-900">{{ depot()?.pharmacyCount ?? 0 }}</p><p class="text-sm text-slate-500">Pharmacies</p></div>
    <div class="card card-p"><div class="stat-icon bg-brand-50 text-brand-600"><span class="material-icons">mark_email_unread</span></div>
      <p class="mt-3 text-3xl font-bold text-brand-600">{{ count('SUBMITTED') }}</p><p class="text-sm text-slate-500">New requests</p></div>
    <div class="card card-p"><div class="stat-icon bg-violet-50 text-violet-600"><span class="material-icons">leaderboard</span></div>
      <p class="mt-3 text-3xl font-bold text-violet-600">{{ count('SUBMITTED') + count('RECEIVED') }}</p><p class="text-sm text-slate-500">To prioritize</p></div>
    <div class="card card-p"><div class="stat-icon bg-accent-100 text-accent-600"><span class="material-icons">verified</span></div>
      <p class="mt-3 text-3xl font-bold text-accent-600">{{ count('PLANNED') }}</p><p class="text-sm text-slate-500">Approved</p></div>
    <div class="card card-p"><div class="stat-icon bg-amber-50 text-amber-600"><span class="material-icons">local_shipping</span></div>
      <p class="mt-3 text-3xl font-bold text-amber-600">{{ activeDeliveries() }}</p><p class="text-sm text-slate-500">Active deliveries</p></div>
  </div>

  <div class="mt-6 grid gap-6 lg:grid-cols-3">
    <div class="card lg:col-span-2">
      <div class="flex items-center justify-between border-b border-slate-100 p-5">
        <p class="card-title">Requests by urgency</p>
        <a class="text-sm font-medium text-brand-600 hover:underline" routerLink="/depot/requests">Open queue</a>
      </div>
      <div class="grid grid-cols-2 gap-3 p-5 sm:grid-cols-4">
        <div *ngFor="let u of urgencies" class="rounded-xl border border-slate-100 p-4 text-center">
          <p class="text-2xl font-bold text-slate-900">{{ byUrgency(u) }}</p>
          <span [class]="cls(u)">{{ u }}</span>
        </div>
      </div>
      <div class="border-t border-slate-100 p-5">
        <p class="mb-3 text-sm font-semibold text-slate-600">Latest requests</p>
        <div class="space-y-2">
          <div *ngFor="let r of reqs().slice(0,5)" class="flex items-center gap-3 rounded-lg border border-slate-100 p-3">
            <span class="stat-icon h-9 w-9 bg-slate-50 text-slate-500"><span class="material-icons text-[18px]">storefront</span></span>
            <div class="flex-1"><p class="text-sm font-medium text-slate-800">{{ r.pharmacyName }}</p>
              <p class="text-xs text-slate-500">{{ r.items[0]?.medication?.name }}</p></div>
            <span [class]="cls(r.urgency)">{{ r.urgency }}</span>
            <span [class]="cls(r.status)">{{ r.status }}</span>
          </div>
          <div *ngIf="reqs().length===0" class="p-6 text-center text-sm text-slate-400">No requests yet.</div>
        </div>
      </div>
    </div>
    <app-time-control (changed)="load()"></app-time-control>
  </div>`
})
export class DepotDashboardComponent implements OnInit {
  private api = inject(ApiService);
  private stream = inject(StreamService);
  depot = signal<Depot | null>(null);
  reqs = signal<PharmacyRequest[]>([]);
  deliveries = signal<Delivery[]>([]);
  urgencies = ['LOW', 'NORMAL', 'HIGH', 'CRITICAL'];
  cls = statusClass;

  ngOnInit(): void {
    this.load();
    this.stream.connect();
    this.stream.updates.subscribe(() => this.load());
  }
  load(): void {
    this.api.myDepot().subscribe((d) => this.depot.set(d));
    this.api.depotRequests().subscribe((p) => this.reqs.set(p.content));
    this.api.depotDeliveries().subscribe((r) => this.deliveries.set(r));
  }
  count(s: string): number { return this.reqs().filter((r) => r.status === s).length; }
  byUrgency(u: string): number { return this.reqs().filter((r) => r.urgency === u && !['DELIVERED','CANCELLED','REJECTED'].includes(r.status)).length; }
  activeDeliveries(): number { return this.deliveries().filter((d) => ['STARTED','IN_TRANSIT','ARRIVED_AT_STOP','PLANNED'].includes(d.status)).length; }
}
