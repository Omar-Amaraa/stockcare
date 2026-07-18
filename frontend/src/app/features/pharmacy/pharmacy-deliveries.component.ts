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
  <p class="text-sm text-ink-mute">Follow deliveries heading to your pharmacy in real time.</p>

  <div class="mt-5 grid gap-6 lg:grid-cols-[360px_1fr]">
    <div class="card overflow-hidden">
      <div class="border-b border-line/70 p-4"><p class="card-title">Your deliveries</p></div>
      <div class="stagger divide-y divide-line/70">
        <button *ngFor="let d of deliveries()" (click)="select(d)"
                class="flex w-full items-center gap-3 p-4 text-left transition-colors duration-150 hover:bg-raised/50"
                [ngClass]="{'bg-brand-50 dark:bg-brand-500/10': selected()?.id === d.id}">
          <span class="stat-icon t-accent"><span class="material-icons">local_shipping</span></span>
          <div class="flex-1">
            <p class="font-medium text-ink">{{ d.reference }}</p>
            <p class="text-xs text-ink-mute">{{ d.stops.length }} stop(s) · {{ (d.progress*100)|number:'1.0-0' }}%</p>
            <div class="progress mt-1.5 max-w-[140px]">
              <span class="progress-bar bg-gradient-to-r from-brand-500 to-accent-500" [style.width.%]="d.progress*100"></span>
            </div>
          </div>
          <span [class]="cls(d.status)">{{ d.status }}</span>
        </button>
        <div *ngIf="deliveries().length===0" class="p-10 text-center text-sm text-ink-faint">No deliveries concern your pharmacy yet.</div>
      </div>
    </div>

    <div class="card card-p animate-fade-up" *ngIf="selected() as d">
      <div class="mb-4 flex items-center justify-between">
        <div><p class="card-title">{{ d.reference }}</p>
          <p class="flex items-center gap-1.5 text-xs text-ink-mute"><span class="live-dot"></span> Live tracking</p></div>
        <span [class]="cls(d.status)">{{ d.status }}</span>
      </div>
      <app-tracking-map [delivery]="d"></app-tracking-map>
    </div>
    <div class="card card-p flex items-center justify-center text-ink-faint" *ngIf="!selected()">
      <div class="text-center"><span class="material-icons text-4xl text-ink-faint/50">map</span><p class="mt-2 text-sm">Select a delivery to track it.</p></div>
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
