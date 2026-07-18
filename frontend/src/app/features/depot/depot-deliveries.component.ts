import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { Delivery, Driver, PharmacyRequest, Vehicle } from '../../core/models';
import { TrackingMapComponent } from '../../shared/tracking-map.component';
import { statusClass } from '../../shared/status-badge';

@Component({
  selector: 'app-depot-deliveries',
  standalone: true,
  imports: [CommonModule, FormsModule, TrackingMapComponent],
  template: `
  <h1>Deliveries & live tracking</h1>
  <p class="text-sm text-slate-500">Plan routes from approved requests and watch them in real time.</p>

  <!-- Planner -->
  <div class="card card-p mt-5">
    <div class="mb-3 flex items-center gap-2">
      <p class="card-title">Plan a delivery</p>
      <span class="badge b-orange">mock · non-optimized</span>
    </div>
    <div *ngIf="approved().length===0" class="rounded-lg bg-slate-50 p-4 text-sm text-slate-400">
      No approved requests yet. Approve requests in the Requests queue first.
    </div>
    <div *ngIf="approved().length" class="space-y-2">
      <label *ngFor="let r of approved()" class="flex cursor-pointer items-center gap-3 rounded-lg border border-slate-100 p-3 hover:bg-slate-50">
        <input type="checkbox" class="h-4 w-4 rounded text-brand-600" [(ngModel)]="picked[r.id]" [name]="r.id">
        <div class="flex-1"><p class="text-sm font-medium text-slate-800">{{ r.pharmacyName }}</p>
          <p class="text-xs text-slate-500">{{ r.items[0]?.medication?.name }} × {{ r.items[0]?.requestedQuantity }}</p></div>
        <span *ngIf="r.priority" class="badge b-purple">priority {{ r.priority.coefficient }}</span>
      </label>
    </div>
    <div class="mt-4 flex flex-wrap items-end gap-3">
      <div><label class="label">Vehicle</label>
        <select class="input w-56" [(ngModel)]="vehicleId"><option value="">Select…</option>
          <option *ngFor="let v of vehicles()" [value]="v.id">{{ v.code }} · {{ v.capacityUnits }}u{{ v.refrigerated ? ' ❄' : '' }}</option></select></div>
      <div><label class="label">Driver</label>
        <select class="input w-56" [(ngModel)]="driverId"><option value="">Optional…</option>
          <option *ngFor="let d of drivers()" [value]="d.id">{{ d.fullName }}</option></select></div>
      <button class="btn btn-primary" (click)="plan()" [disabled]="!vehicleId || selectedIds().length===0">
        <span class="material-icons text-[18px]">route</span> Create plan</button>
    </div>
  </div>

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
        <button class="btn btn-primary btn-sm" (click)="action(d,'start')" [disabled]="d.status!=='PLANNED'"><span class="material-icons text-[16px]">play_arrow</span> Start</button>
        <button class="btn btn-ghost btn-sm" (click)="action(d,'pause')"><span class="material-icons text-[16px]">pause</span> Pause</button>
        <button class="btn btn-ghost btn-sm" (click)="action(d,'resume')"><span class="material-icons text-[16px]">play_circle</span> Resume</button>
        <button class="btn btn-accent btn-sm" (click)="action(d,'complete')"><span class="material-icons text-[16px]">done_all</span> Complete</button>
        <button class="btn btn-ghost btn-sm text-rose-600" (click)="action(d,'cancel')">Cancel</button>
      </div>
    </div>
    <div class="card card-p flex items-center justify-center text-slate-400" *ngIf="!selected()">
      <div class="text-center"><span class="material-icons text-4xl text-slate-200">local_shipping</span><p class="mt-2 text-sm">Plan or select a delivery to track it.</p></div>
    </div>
  </div>`
})
export class DepotDeliveriesComponent implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  approved = signal<PharmacyRequest[]>([]);
  vehicles = signal<Vehicle[]>([]);
  drivers = signal<Driver[]>([]);
  deliveries = signal<Delivery[]>([]);
  selected = signal<Delivery | null>(null);
  picked: Record<string, boolean> = {};
  vehicleId = '';
  driverId = '';
  cls = statusClass;

  ngOnInit(): void { this.load(); }
  load(): void {
    this.api.depotRequests('PLANNED').subscribe((p) => this.approved.set(p.content));
    this.api.vehicles().subscribe((v) => this.vehicles.set(v));
    this.api.drivers().subscribe((d) => this.drivers.set(d));
    this.api.depotDeliveries().subscribe((d) => this.deliveries.set(d));
  }
  selectedIds(): string[] { return Object.keys(this.picked).filter((k) => this.picked[k]); }
  plan(): void {
    this.api.planDelivery({ requestIds: this.selectedIds(), vehicleId: this.vehicleId, driverId: this.driverId || null }).subscribe({
      next: (d) => { this.toast.success('Delivery planned'); this.picked = {}; this.load(); this.select(d); },
      error: (e) => this.toast.error(e.error?.message || 'Planning failed')
    });
  }
  select(d: Delivery): void { this.api.getDelivery(d.id).subscribe((full) => this.selected.set(full)); }
  action(d: Delivery, a: string): void { this.api.deliveryAction(d.id, a).subscribe((u) => { this.selected.set(u); this.load(); }); }
}
