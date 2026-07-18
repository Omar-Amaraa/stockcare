import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { StreamService } from '../../core/stream.service';
import { PharmacyRequest, RequestStatus } from '../../core/models';
import { statusClass } from '../../shared/status-badge';

@Component({
  selector: 'app-depot-requests',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
  <div class="flex flex-wrap items-center justify-between gap-3">
    <div><h1>Incoming requests</h1><p class="text-sm text-slate-500">Requests are prioritized automatically on arrival. Approve them for delivery planning.</p></div>
    <div class="flex items-center gap-2">
      <label class="text-xs font-medium text-slate-500">Status</label>
      <select class="input w-44" [(ngModel)]="filter" (ngModelChange)="load()">
        <option value="">All</option><option *ngFor="let s of statuses" [value]="s">{{ s }}</option>
      </select>
    </div>
  </div>

  <div class="mt-5 space-y-3">
    <div *ngFor="let r of reqs()" class="card">
      <button class="flex w-full flex-wrap items-center gap-3 p-4 text-left" (click)="toggle(r.id)">
        <span class="stat-icon bg-slate-50 text-slate-500"><span class="material-icons">storefront</span></span>
        <div class="flex-1 min-w-[140px]">
          <p class="font-medium text-slate-800">{{ r.pharmacyName }}</p>
          <p class="text-xs text-slate-500">{{ r.items[0]?.medication?.name }}<span *ngIf="r.items.length>1"> +{{r.items.length-1}}</span></p>
        </div>
        <span *ngIf="r.priority" class="badge b-purple">priority {{ r.priority.coefficient }}</span>
        <span [class]="cls(r.urgency)">{{ r.urgency }}</span>
        <span [class]="cls(r.status)">{{ r.status }}</span>
        <span class="material-icons text-slate-300">{{ expanded()===r.id ? 'expand_less' : 'expand_more' }}</span>
      </button>

      <div *ngIf="expanded()===r.id" class="space-y-3 border-t border-slate-100 p-4">
        <ul class="space-y-1 text-sm text-slate-600">
          <li *ngFor="let it of r.items" class="flex items-center gap-2"><span class="material-icons text-[16px] text-slate-300">chevron_right</span>{{ it.medication.name }} × <b>{{ it.requestedQuantity }}</b></li>
        </ul>
        <p *ngIf="r.notes" class="text-sm text-slate-500"><span class="font-medium text-slate-600">Pharmacy note:</span> {{ r.notes }}</p>

        <div *ngIf="r.priority" class="rounded-lg bg-violet-50 p-3">
          <p class="text-sm font-semibold text-violet-800">Priority {{ r.priority.coefficient }}/100 <span class="text-xs font-normal text-violet-500">({{ r.priority.calculationVersion }})</span></p>
          <div class="mt-2 flex flex-wrap gap-1.5">
            <span *ngFor="let f of factorList(r)" class="badge b-purple">{{ f.k }}: {{ f.v }}</span>
          </div>
          <p class="mt-2 text-xs text-violet-600">{{ r.priority.explanation }}</p>
        </div>

        <div class="flex flex-wrap items-end gap-2">
          <div class="flex-1 min-w-[200px]">
            <label class="label">Internal note</label>
            <div class="flex gap-2">
              <input class="input" [(ngModel)]="noteDraft[r.id]" [placeholder]="r.internalNotes || 'Add a depot note…'">
              <button class="btn btn-ghost btn-sm" (click)="saveNote(r)">Save</button>
            </div>
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-2 pt-1">
          <span *ngIf="['SUBMITTED','RECEIVED','PRIORITY_PENDING'].includes(r.status) && !r.priority" class="badge b-orange">
            <span class="material-icons mr-1 animate-spin text-[14px]">progress_activity</span>Calculating priority…
          </span>
          <button class="btn btn-accent btn-sm" *ngIf="r.status==='PRIORITIZED'" (click)="approve(r)">
            <span class="material-icons text-[16px]">verified</span> Approve for planning</button>
          <button class="btn btn-ghost btn-sm" *ngIf="r.status==='RECEIVED' && !r.priority" (click)="prioritize(r)" title="Priority calculation failed — retry">
            <span class="material-icons text-[16px]">refresh</span> Retry priority</button>
          <button class="btn btn-ghost btn-sm text-rose-600" *ngIf="!['DELIVERED','CANCELLED','REJECTED'].includes(r.status)" (click)="setStatus(r,'REJECTED')">Reject</button>
        </div>
      </div>
    </div>
    <div *ngIf="reqs().length===0" class="card p-12 text-center text-slate-400">No requests match this filter.</div>
  </div>`
})
export class DepotRequestsComponent implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private stream = inject(StreamService);
  statuses = ['SUBMITTED', 'RECEIVED', 'PRIORITY_PENDING', 'PRIORITIZED', 'PLANNED', 'IN_DELIVERY', 'DELIVERED', 'REJECTED'];
  filter: RequestStatus | '' = '';
  reqs = signal<PharmacyRequest[]>([]);
  expanded = signal<string | null>(null);
  noteDraft: Record<string, string> = {};
  cls = statusClass;

  ngOnInit(): void {
    this.load();
    this.stream.connect();
    this.stream.updates.subscribe((u) => { if (u.type === 'REQUEST_STATE' || u.type === 'PRIORITY_STATE') this.load(); });
  }
  load(): void { this.api.depotRequests(this.filter || undefined).subscribe((p) => this.reqs.set(p.content)); }
  toggle(id: string): void { this.expanded.set(this.expanded() === id ? null : id); }
  factorList(r: PharmacyRequest): { k: string; v: number }[] {
    return Object.entries(r.priority?.factors ?? {}).map(([k, v]) => ({ k, v }));
  }
  prioritize(r: PharmacyRequest): void { this.api.prioritize(r.id).subscribe(() => { this.toast.success('Priority calculated'); this.load(); }); }
  approve(r: PharmacyRequest): void { this.api.approveRequest(r.id).subscribe(() => { this.toast.success('Approved for planning'); this.load(); }); }
  setStatus(r: PharmacyRequest, s: RequestStatus): void { this.api.changeStatus(r.id, s).subscribe(() => this.load()); }
  saveNote(r: PharmacyRequest): void {
    const note = this.noteDraft[r.id];
    if (!note) return;
    this.api.internalNote(r.id, note).subscribe(() => { this.toast.success('Note saved'); this.load(); });
  }
}
