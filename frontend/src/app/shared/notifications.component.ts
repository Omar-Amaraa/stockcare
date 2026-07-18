import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../core/api.service';
import { Notification } from '../core/models';

const ICONS: Record<string, string> = {
  SHORTAGE_PREDICTED: 'insights', NEW_REQUEST: 'assignment', REQUEST_STATUS_UPDATED: 'sync',
  PRIORITY_CALCULATED: 'leaderboard', ROUTE_PLANNED: 'route', DELIVERY_STARTED: 'local_shipping',
  DELIVERY_APPROACHING: 'near_me', DELIVERY_COMPLETED: 'check_circle', DELIVERY_FAILED: 'error'
};

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule],
  template: `
  <div class="flex items-center justify-between">
    <h1>Notifications</h1>
    <span class="badge b-blue">{{ items().length }}</span>
  </div>

  <div class="card mt-5 divide-y divide-slate-100">
    <div *ngFor="let n of items()" class="flex items-start gap-4 p-4" [ngClass]="{'bg-brand-50': !n.read}">
      <span class="stat-icon" [ngClass]="n.read ? 'bg-slate-100 text-slate-400' : 'bg-brand-50 text-brand-600'">
        <span class="material-icons">{{ icon(n.type) }}</span>
      </span>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2">
          <p class="font-semibold text-slate-800">{{ n.title }}</p>
          <span *ngIf="!n.read" class="h-2 w-2 rounded-full bg-brand-500"></span>
        </div>
        <p class="text-sm text-slate-600">{{ n.message }}</p>
        <p class="mt-0.5 text-xs text-slate-400">{{ n.createdAt | date:'medium' }}</p>
      </div>
      <button *ngIf="!n.read" class="btn btn-ghost btn-sm" (click)="read(n)">Mark read</button>
    </div>
    <div *ngIf="items().length === 0" class="p-10 text-center text-sm text-slate-400">
      <span class="material-icons text-3xl text-slate-300">notifications_off</span>
      <p class="mt-2">No notifications yet.</p>
    </div>
  </div>`
})
export class NotificationsComponent implements OnInit {
  private api = inject(ApiService);
  items = signal<Notification[]>([]);
  ngOnInit(): void { this.load(); }
  load(): void { this.api.notifications().subscribe((p) => this.items.set(p.content)); }
  read(n: Notification): void { this.api.markRead(n.id).subscribe(() => this.load()); }
  icon(type: string): string { return ICONS[type] ?? 'notifications'; }
}
