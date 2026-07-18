import { Component, ElementRef, Input, OnChanges, OnDestroy, ViewChild, AfterViewInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import * as L from 'leaflet';
import { AuthService } from '../core/auth.service';
import { Delivery, TrackingUpdate } from '../core/models';

@Component({
  selector: 'app-tracking-map',
  standalone: true,
  imports: [CommonModule],
  template: `<div #mapEl class="map"></div>
    <div *ngIf="delivery" class="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-ink-mute">
      <span class="inline-flex items-center gap-1.5"><span class="material-icons text-[16px]">local_shipping</span>{{ delivery.vehicle?.code || '—' }}</span>
      <span class="inline-flex min-w-[140px] flex-1 items-center gap-2">
        <span class="progress max-w-[160px]"><span class="progress-bar bg-gradient-to-r from-brand-500 to-accent-500" [style.width.%]="delivery.progress*100"></span></span>
        {{ (delivery.progress*100) | number:'1.0-0' }}%
      </span>
      <span class="inline-flex items-center gap-1.5"><span class="material-icons text-[16px]">schedule</span>ETA {{ delivery.etaMinutes != null ? (delivery.etaMinutes | number:'1.0-0') + ' min' : '—' }}</span>
      <span *ngIf="delivery.simulated" class="badge b-orange">SIMULATED</span>
    </div>`
})
export class TrackingMapComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() delivery: Delivery | null = null;
  @ViewChild('mapEl', { static: true }) mapEl!: ElementRef<HTMLDivElement>;
  private auth = inject(AuthService);
  private map?: L.Map;
  private vehicleMarker?: L.Marker;
  private layer = L.layerGroup();
  private es?: EventSource;
  private currentId?: string;

  ngAfterViewInit(): void {
    this.map = L.map(this.mapEl.nativeElement).setView([36.8, 10.18], 8);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap', maxZoom: 18
    }).addTo(this.map);
    this.layer.addTo(this.map);
    this.render();
  }

  ngOnChanges(): void {
    if (this.map) this.render();
    if (this.delivery && this.delivery.id !== this.currentId) this.subscribe(this.delivery.id);
  }

  private render(): void {
    if (!this.map || !this.delivery) return;
    this.layer.clearLayers();
    const d = this.delivery;
    const pts: L.LatLngExpression[] = [];
    if (d.depotLatitude != null && d.depotLongitude != null) {
      const depot: L.LatLngExpression = [d.depotLatitude, d.depotLongitude];
      pts.push(depot);
      L.marker(depot, { icon: this.icon('#3f51b5', 'D') }).bindPopup('Depot').addTo(this.layer);
    }
    for (const s of d.stops) {
      const p: L.LatLngExpression = [s.latitude, s.longitude];
      pts.push(p);
      const done = s.status === 'COMPLETED';
      L.marker(p, { icon: this.icon(done ? '#43a047' : '#e53935', String(s.sequence)) })
        .bindPopup(`${s.sequence}. ${s.pharmacyName} (${s.status})`).addTo(this.layer);
    }
    if (pts.length > 1) {
      L.polyline(pts, { color: '#1e88e5', weight: 3, dashArray: '6' }).addTo(this.layer);
      this.map.fitBounds(L.latLngBounds(pts).pad(0.2));
    }
    if (d.currentLatitude != null && d.currentLongitude != null) {
      const pos: L.LatLngExpression = [d.currentLatitude, d.currentLongitude];
      this.vehicleMarker = L.marker(pos, { icon: this.icon('#fb8c00', '🚚') }).addTo(this.layer);
    }
  }

  private subscribe(id: string): void {
    this.currentId = id;
    this.es?.close();
    const token = this.auth.token;
    this.es = new EventSource(`/api/tracking/deliveries/${id}/stream?access_token=${token}`);
    this.es.addEventListener('tracking', (ev: MessageEvent) => {
      const u = JSON.parse(ev.data) as TrackingUpdate;
      if (this.delivery) {
        this.delivery = { ...this.delivery, status: u.status, currentLatitude: u.latitude,
          currentLongitude: u.longitude, progress: u.progress, etaMinutes: u.etaMinutes,
          currentStopIndex: u.currentStopIndex };
      }
      if (u.latitude != null && u.longitude != null) {
        const pos: L.LatLngExpression = [u.latitude, u.longitude];
        if (this.vehicleMarker) this.vehicleMarker.setLatLng(pos);
      }
    });
  }

  private icon(color: string, label: string): L.DivIcon {
    return L.divIcon({
      className: 'stop-icon',
      html: `<div style="background:${color};color:#fff;border-radius:50%;width:26px;height:26px;
        display:flex;align-items:center;justify-content:center;font-size:12px;border:2px solid #fff;
        box-shadow:0 1px 4px rgba(0,0,0,.4)">${label}</div>`,
      iconSize: [26, 26], iconAnchor: [13, 13]
    });
  }

  ngOnDestroy(): void { this.es?.close(); this.map?.remove(); }
}
