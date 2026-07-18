import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { StreamService } from '../../core/stream.service';
import { Delivery } from '../../core/models';
import { TrackingMapComponent } from '../../shared/tracking-map.component';
import { statusClass } from '../../shared/status-badge';

/**
 * Fully automated planning: approved requests are solved by the MILP optimizer in the background
 * (vehicle assignment + stop order). This screen only shows the optimizer's proposals — the only
 * human decision is Approve (dispatch) or Deny (cancel). No vehicle or request picking.
 */
@Component({
  selector: 'app-depot-deliveries',
  standalone: true,
  imports: [CommonModule, TrackingMapComponent],
  template: `
  <h1>Route proposals & live tracking</h1>
  <p class="text-sm text-slate-500">The MILP optimizer plans routes automatically when requests are approved. You only approve or deny each proposed route.</p>

  <!-- Optimizer status banner -->
  <div *ngIf="optimizing()" class="mt-4 flex items-center gap-3 rounded-xl border border-indigo-100 bg-indigo-50 p-4">
    <span class="material-icons animate-spin text-indigo-500">progress_activity</span>
    <div><p class="text-sm font-semibold text-indigo-800">MILP optimizer running</p>
      <p class="text-xs text-indigo-600">{{ optimizing() }}</p></div>
  </div>
  <div *ngIf="planError()" class="mt-4 flex items-center justify-between gap-3 rounded-xl border border-rose-100 bg-rose-50 p-4">
    <div class="flex items-center gap-3"><span class="material-icons text-rose-500">error</span>
      <p class="text-sm text-rose-700">{{ planError() }}</p></div>
    <button class="btn btn-ghost btn-sm" (click)="planError.set(null)">Dismiss</button>
  </div>

  <!-- Proposed routes: the single human decision point -->
  <div class="mt-5" *ngIf="proposals().length">
    <div class="mb-2 flex items-center gap-2">
      <h2 class="text-base font-semibold text-slate-800">Proposed routes</h2>
      <span class="badge b-purple">{{ proposals().length }} awaiting your decision</span>
    </div>
    <div class="grid gap-4 xl:grid-cols-2">
      <div *ngFor="let d of proposals()" class="card card-p border-l-4 border-l-indigo-400">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <p class="card-title">{{ d.reference }}</p>
            <p class="text-xs text-slate-500">
              Vehicle <b>{{ d.vehicle?.code }}</b>{{ d.vehicle?.refrigerated ? ' ❄' : '' }} (chosen by optimizer)
              <span *ngIf="d.driver"> · {{ d.driver?.fullName }}</span>
            </p>
          </div>
          <span class="badge" [class.b-green]="d.optimized" [class.b-orange]="!d.optimized">
            {{ d.optimized ? 'MILP optimal' : 'heuristic' }} · {{ d.optimizerVersion }}</span>
        </div>
        <ol class="mt-3 space-y-1">
          <li *ngFor="let s of d.stops" class="flex items-center gap-2 text-sm text-slate-600">
            <span class="flex h-5 w-5 items-center justify-center rounded-full bg-indigo-100 text-[11px] font-bold text-indigo-700">{{ s.sequence }}</span>
            {{ s.pharmacyName }}
            <span *ngIf="s.estimatedArrivalMinute != null" class="text-xs text-slate-400">~{{ s.estimatedArrivalMinute }} min</span>
          </li>
        </ol>
        <p class="mt-2 text-xs text-slate-500">
          {{ d.stops.length }} stop(s) · {{ d.totalDistanceKm | number:'1.0-1' }} km ·
          {{ d.totalDurationMinutes | number:'1.0-0' }} min · {{ d.items.length }} item line(s)</p>
        <div class="mt-3 flex flex-wrap gap-2">
          <button class="btn btn-primary btn-sm" (click)="approve(d)">
            <span class="material-icons text-[16px]">check_circle</span> Approve & dispatch</button>
          <button class="btn btn-ghost btn-sm text-rose-600" (click)="deny(d)">
            <span class="material-icons text-[16px]">cancel</span> Deny</button>
          <button class="btn btn-ghost btn-sm" (click)="select(d)">
            <span class="material-icons text-[16px]">map</span> View on map</button>
        </div>
      </div>
    </div>
  </div>
  <div *ngIf="proposals().length===0 && !optimizing()" class="mt-5 card p-6 text-sm text-slate-400">
    No route proposals pending. Approve pharmacy requests in the Requests queue — the optimizer plans routes automatically from there.
  </div>

  <!-- Active + past deliveries with live tracking -->
  <div class="mt-6 grid gap-6 lg:grid-cols-[360px_1fr]">
    <div class="card overflow-hidden">
      <div class="border-b border-slate-100 p-4"><p class="card-title">Deliveries</p></div>
      <div class="divide-y divide-slate-100">
        <button *ngFor="let d of deliveries()" (click)="select(d)"
                class="flex w-full items-center gap-3 p-4 text-left hover:bg-slate-50" [class.bg-brand-50]="selected()?.id===d.id">
          <span class="stat-icon bg-accent-100 text-accent-600"><span class="material-icons">local_shipping</span></span>
          <div class="flex-1"><p class="font-medium text-slate-800">{{ d.reference }}</p>
            <p class="text-xs text-slate-500">{{ d.stops.length }} stops · {{ d.totalDistanceKm|number:'1.0-1' }} km · {{ (d.progress*100)|number:'1.0-0' }}%</p></div>
          <span [class]="cls(d.status)">{{ d.status }}</span>
        </button>
        <div *ngIf="deliveries().length===0" class="p-10 text-center text-sm text-slate-400">No deliveries yet.</div>
      </div>
    </div>

    <div class="card card-p" *ngIf="selected() as d">
      <div class="mb-4 flex items-center justify-between">
        <div><p class="card-title">{{ d.reference }}</p><p class="text-xs text-slate-500">{{ d.vehicle?.code }} · {{ d.driver?.fullName || 'no driver' }}</p></div>
        <span [class]="cls(d.status)">{{ d.status }}</span>
      </div>
      <app-tracking-map [delivery]="d"></app-tracking-map>
      <div class="mt-4 flex flex-wrap gap-2">
        <button *ngIf="d.status==='PLANNED'" class="btn btn-primary btn-sm" (click)="approve(d)">
          <span class="material-icons text-[16px]">check_circle</span> Approve & dispatch</button>
        <button *ngIf="d.status==='PLANNED'" class="btn btn-ghost btn-sm text-rose-600" (click)="deny(d)">Deny</button>
        <ng-container *ngIf="d.status!=='PLANNED' && d.status!=='DELIVERED' && d.status!=='CANCELLED'">
          <button class="btn btn-ghost btn-sm" (click)="action(d,'pause')"><span class="material-icons text-[16px]">pause</span> Pause</button>
          <button class="btn btn-ghost btn-sm" (click)="action(d,'resume')"><span class="material-icons text-[16px]">play_circle</span> Resume</button>
          <button class="btn btn-accent btn-sm" (click)="action(d,'complete')"><span class="material-icons text-[16px]">done_all</span> Complete</button>
        </ng-container>
      </div>
    </div>
    <div class="card card-p flex items-center justify-center text-slate-400" *ngIf="!selected()">
      <div class="text-center"><span class="material-icons text-4xl text-slate-200">local_shipping</span><p class="mt-2 text-sm">Select a delivery to track it.</p></div>
    </div>
  </div>`
})
export class DepotDeliveriesComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private stream = inject(StreamService);
  private sub?: Subscription;
  private timer?: ReturnType<typeof setInterval>;

  deliveries = signal<Delivery[]>([]);
  selected = signal<Delivery | null>(null);
  optimizing = signal<string | null>(null);
  planError = signal<string | null>(null);
  cls = statusClass;

  ngOnInit(): void {
    this.load();
    this.stream.connect();
    this.sub = this.stream.updates.subscribe((u) => {
      if (u.type !== 'ROUTE_STATE') return;
      if (u.status === 'OPTIMIZING') { this.optimizing.set(u.message); this.planError.set(null); }
      if (u.status === 'PLANNED') { this.optimizing.set(null); this.toast.success(u.message); this.load(); }
      if (u.status === 'FAILED') { this.optimizing.set(null); this.planError.set(u.message); this.load(); }
    });
    this.timer = setInterval(() => this.load(), 10000);
  }
  ngOnDestroy(): void { this.sub?.unsubscribe(); if (this.timer) clearInterval(this.timer); }

  load(): void { this.api.depotDeliveries().subscribe((d) => this.deliveries.set(d)); }
  proposals(): Delivery[] { return this.deliveries().filter((d) => d.status === 'PLANNED'); }

  approve(d: Delivery): void {
    this.api.deliveryAction(d.id, 'start').subscribe({
      next: (u) => { this.toast.success('Route approved — vehicle dispatched'); this.selected.set(u); this.load(); },
      error: (e) => this.toast.error(e.error?.message || 'Could not start delivery')
    });
  }
  deny(d: Delivery): void {
    this.api.deliveryAction(d.id, 'cancel').subscribe({
      next: () => { this.toast.success('Route denied — requests stay approved for the next solve'); this.selected.set(null); this.load(); },
      error: (e) => this.toast.error(e.error?.message || 'Could not cancel delivery')
    });
  }
  select(d: Delivery): void { this.api.getDelivery(d.id).subscribe((full) => this.selected.set(full)); }
  action(d: Delivery, a: string): void { this.api.deliveryAction(d.id, a).subscribe((u) => { this.selected.set(u); this.load(); }); }
}
