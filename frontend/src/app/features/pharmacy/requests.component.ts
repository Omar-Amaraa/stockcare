import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { StreamService } from '../../core/stream.service';
import { Medication, PharmacyRequest } from '../../core/models';
import { statusClass } from '../../shared/status-badge';

/**
 * Automated restock flow: the prediction model detects an upcoming shortage and drafts the
 * request itself (medication, quantity, urgency). The pharmacist's only decision is yes/no.
 */
@Component({
  selector: 'app-requests',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
  <div class="flex items-center justify-between">
    <div><h1>Restock requests</h1>
      <p class="text-sm text-slate-500">StockCare watches your stock and proposes requests before you run out. You only say yes or no.</p></div>
    <button class="btn btn-ghost btn-sm" (click)="showNew.set(!showNew())">
      <span class="material-icons text-[16px]">{{ showNew() ? 'close' : 'add' }}</span>{{ showNew() ? 'Close' : 'Manual request' }}
    </button>
  </div>

  <!-- Proposed requests: the yes/no decision point -->
  <div class="mt-5" *ngIf="proposals().length">
    <div class="mb-2 flex items-center gap-2">
      <h2 class="text-base font-semibold text-slate-800">Proposed for you</h2>
      <span class="badge b-purple">{{ proposals().length }} awaiting your answer</span>
    </div>
    <div class="grid gap-4 lg:grid-cols-2">
      <div *ngFor="let r of proposals()" class="card card-p border-l-4 border-l-amber-400">
        <div class="flex items-start gap-3">
          <span class="stat-icon bg-amber-50 text-amber-600"><span class="material-icons">notification_important</span></span>
          <div class="flex-1">
            <p class="font-medium text-slate-800">
              {{ r.items[0]?.medication?.name }}<span *ngIf="r.items.length>1" class="text-slate-400"> +{{ r.items.length-1 }} more</span>
              is running out
            </p>
            <p class="mt-0.5 text-xs text-slate-500">{{ r.notes }}</p>
            <ul class="mt-2 space-y-1 text-sm text-slate-600">
              <li *ngFor="let it of r.items">Send <b>{{ it.requestedQuantity }}</b> × {{ it.medication.name }}</li>
            </ul>
          </div>
          <span [class]="cls(r.urgency)">{{ r.urgency }}</span>
        </div>
        <div class="mt-3 flex gap-2">
          <button class="btn btn-primary btn-sm" (click)="accept(r)">
            <span class="material-icons text-[16px]">check_circle</span> Yes, send to depot</button>
          <button class="btn btn-ghost btn-sm text-rose-600" (click)="dismiss(r)">
            <span class="material-icons text-[16px]">cancel</span> No, dismiss</button>
        </div>
      </div>
    </div>
  </div>
  <div *ngIf="proposals().length===0" class="mt-5 card p-5 text-sm text-slate-400">
    No proposed requests right now — your stock looks healthy. New proposals appear here automatically when a shortage is predicted.
  </div>

  <!-- Optional manual request (collapsed by default) -->
  <div *ngIf="showNew()" class="card card-p mt-5">
    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <div class="sm:col-span-2">
        <label class="label">Medication</label>
        <select class="input" [(ngModel)]="form.medicationId"><option value="">Select…</option><option *ngFor="let m of meds()" [value]="m.id">{{ m.name }}</option></select>
      </div>
      <div><label class="label">Quantity</label><input class="input" type="number" [(ngModel)]="form.quantity"></div>
      <div><label class="label">Urgency</label><select class="input" [(ngModel)]="form.urgency"><option *ngFor="let u of urgencies" [value]="u">{{ u }}</option></select></div>
      <div><label class="label">Patients affected</label><input class="input" type="number" [(ngModel)]="form.affectedPatients"></div>
    </div>
    <div class="mt-4"><button class="btn btn-primary" (click)="create()">Create & send</button></div>
  </div>

  <!-- Sent requests: tracking only, no decisions -->
  <h2 class="mt-6 text-base font-semibold text-slate-800">Your requests</h2>
  <div class="mt-2 space-y-3">
    <div *ngFor="let r of sent()" class="card">
      <button class="flex w-full items-center gap-3 p-4 text-left" (click)="toggle(r.id)">
        <span class="stat-icon bg-slate-50 text-slate-500"><span class="material-icons">medication</span></span>
        <div class="flex-1">
          <p class="font-medium text-slate-800">{{ r.items[0]?.medication?.name }}<span *ngIf="r.items.length>1" class="text-slate-400"> +{{ r.items.length-1 }}</span></p>
          <p class="text-xs text-slate-500">{{ r.createdAt | date:'medium' }}</p>
        </div>
        <span [class]="cls(r.urgency)">{{ r.urgency }}</span>
        <span [class]="cls(r.status)">{{ r.status }}</span>
        <span class="material-icons text-slate-300">{{ expanded() === r.id ? 'expand_less' : 'expand_more' }}</span>
      </button>
      <div *ngIf="expanded() === r.id" class="border-t border-slate-100 p-4">
        <ul class="mb-3 space-y-1 text-sm text-slate-600">
          <li *ngFor="let it of r.items" class="flex items-center gap-2"><span class="material-icons text-[16px] text-slate-300">chevron_right</span>{{ it.medication.name }} × <b>{{ it.requestedQuantity }}</b></li>
        </ul>
        <div *ngIf="r.priority" class="mb-3 rounded-lg bg-violet-50 p-3 text-sm text-violet-800">
          <b>Priority {{ r.priority.coefficient }}/100</b> — {{ r.priority.explanation }}
        </div>
        <button class="btn btn-ghost btn-sm text-rose-600" *ngIf="['SUBMITTED','RECEIVED'].includes(r.status)" (click)="dismiss(r)">Cancel request</button>
      </div>
    </div>
    <div *ngIf="sent().length === 0" class="card p-8 text-center text-sm text-slate-400">No requests sent yet.</div>
  </div>`
})
export class RequestsComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private stream = inject(StreamService);
  private sub?: Subscription;
  urgencies = ['LOW', 'NORMAL', 'HIGH', 'CRITICAL'];
  reqs = signal<PharmacyRequest[]>([]);
  meds = signal<Medication[]>([]);
  showNew = signal(false);
  expanded = signal<string | null>(null);
  form: any = { medicationId: '', quantity: 10, urgency: 'NORMAL', affectedPatients: null };
  cls = statusClass;

  ngOnInit(): void {
    this.load();
    this.api.medications().subscribe((p) => this.meds.set(p.content));
    this.stream.connect();
    this.sub = this.stream.updates.subscribe((u) => {
      if (u.type === 'REQUEST_STATE' || u.type === 'PRIORITY_STATE') this.load();
    });
  }
  ngOnDestroy(): void { this.sub?.unsubscribe(); }

  load(): void { this.api.myRequests().subscribe((p) => this.reqs.set(p.content)); }
  proposals(): PharmacyRequest[] { return this.reqs().filter((r) => r.status === 'DRAFT'); }
  sent(): PharmacyRequest[] { return this.reqs().filter((r) => r.status !== 'DRAFT' && r.status !== 'CANCELLED'); }
  toggle(id: string): void { this.expanded.set(this.expanded() === id ? null : id); }

  accept(r: PharmacyRequest): void {
    this.api.submitRequest(r.id).subscribe({
      next: () => { this.toast.success('Sent to depot — priority and routing happen automatically'); this.load(); },
      error: (e) => this.toast.error(e.error?.message || 'Could not submit')
    });
  }
  dismiss(r: PharmacyRequest): void {
    this.api.cancelRequest(r.id).subscribe({
      next: () => { this.toast.success('Dismissed'); this.load(); },
      error: (e) => this.toast.error(e.error?.message || 'Could not cancel')
    });
  }
  create(): void {
    if (!this.form.medicationId) { this.toast.error('Select a medication'); return; }
    const body = { urgency: this.form.urgency, affectedPatients: this.form.affectedPatients,
      items: [{ medicationId: this.form.medicationId, requestedQuantity: this.form.quantity }] };
    this.api.createRequest(body).subscribe((created) => {
      this.api.submitRequest(created.id).subscribe(() => { this.toast.success('Request sent to depot'); this.showNew.set(false); this.load(); });
    });
  }
}
