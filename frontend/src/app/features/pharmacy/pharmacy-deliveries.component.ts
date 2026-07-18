import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { Delivery } from '../../core/models';
import { TrackingMapComponent } from '../../shared/tracking-map.component';
import { statusClass } from '../../shared/status-badge';

@Component({
  selector: 'app-pharmacy-deliveries',
  standalone: true,
  imports: [CommonModule, TrackingMapComponent],
  template: `
  <h1>Deliveries</h1>
  <p class="text-sm text-slate-500">Follow deliveries heading to your pharmacy in real time.</p>

  <div class="mt-5 grid gap-6 lg:grid-cols-[360px_1fr]">
    <div class="card overflow-hidden">
      <div class="border-b border-slate-100 p-4"><p class="card-title">Your deliveries</p></div>
      <div class="divide-y divide-slate-100">
        <button *ngFor="let d of deliveries()" (click)="select(d)"
                class="flex w-full items-center gap-3 p-4 text-left hover:bg-slate-50"
                [class.bg-brand-50]="selected()?.id === d.id">
          <span class="stat-icon bg-accent-100 text-accent-600"><span class="material-icons">local_shipping</span></span>
          <div class="flex-1">
            <p class="font-medium text-slate-800">{{ d.reference }}</p>
            <p class="text-xs text-slate-500">{{ d.stops.length }} stop(s) · {{ (d.progress*100)|number:'1.0-0' }}%</p>
          </div>
          <span [class]="cls(d.status)">{{ d.status }}</span>
        </button>
        <div *ngIf="deliveries().length===0" class="p-10 text-center text-sm text-slate-400">No deliveries concern your pharmacy yet.</div>
      </div>
    </div>

    <div class="card card-p" *ngIf="selected() as d">
      <div class="mb-4 flex items-center justify-between">
        <div><p class="card-title">{{ d.reference }}</p><p class="text-xs text-slate-500">Live tracking</p></div>
        <span [class]="cls(d.status)">{{ d.status }}</span>
      </div>
      <app-tracking-map [delivery]="d"></app-tracking-map>
    </div>
    <div class="card card-p flex items-center justify-center text-slate-400" *ngIf="!selected()">
      <div class="text-center"><span class="material-icons text-4xl text-slate-200">map</span><p class="mt-2 text-sm">Select a delivery to track it.</p></div>
    </div>
  </div>`
})
export class PharmacyDeliveriesComponent implements OnInit {
  private api = inject(ApiService);
  deliveries = signal<Delivery[]>([]);
  selected = signal<Delivery | null>(null);
  cls = statusClass;
  ngOnInit(): void { this.api.myDeliveries().subscribe((r) => this.deliveries.set(r)); }
  select(d: Delivery): void { this.api.getDelivery(d.id).subscribe((full) => this.selected.set(full)); }
}
