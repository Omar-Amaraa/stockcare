import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { StreamService } from '../../core/stream.service';
import { Medication, PharmacyRequest } from '../../core/models';
import { statusClass } from '../../shared/status-badge';

@Component({
  selector: 'app-requests',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
  <div class="flex items-center justify-between">
    <div><h1>Requests</h1><p class="text-sm text-slate-500">Create and track restock requests to your depot.</p></div>
    <button class="btn btn-primary" (click)="showNew.set(!showNew())">
      <span class="material-icons text-[18px]">{{ showNew() ? 'close' : 'add' }}</span>{{ showNew() ? 'Close' : 'New request' }}
    </button>
  </div>

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
    <div class="mt-4"><button class="btn btn-primary" (click)="create()">Create draft</button></div>
  </div>

  <div class="mt-5 space-y-3">
    <div *ngFor="let r of reqs()" class="card">
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
        <div class="flex gap-2">
          <button class="btn btn-primary btn-sm" *ngIf="r.status==='DRAFT'" (click)="submit(r)">Submit to depot</button>
          <button class="btn btn-ghost btn-sm text-rose-600" *ngIf="['DRAFT','SUBMITTED','RECEIVED'].includes(r.status)" (click)="cancel(r)">Cancel</button>
        </div>
      </div>
    </div>
    <div *ngIf="reqs().length === 0" class="card p-12 text-center text-slate-400">No requests yet.</div>
  </div>`
})
export class RequestsComponent implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private stream = inject(StreamService);
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
    this.stream.updates.subscribe((u) => { if (u.type === 'REQUEST_STATE' || u.type === 'PRIORITY_STATE') this.load(); });
  }
  load(): void { this.api.myRequests().subscribe((p) => this.reqs.set(p.content)); }
  toggle(id: string): void { this.expanded.set(this.expanded() === id ? null : id); }
  create(): void {
    if (!this.form.medicationId) { this.toast.error('Select a medication'); return; }
    const body = { urgency: this.form.urgency, affectedPatients: this.form.affectedPatients,
      items: [{ medicationId: this.form.medicationId, requestedQuantity: this.form.quantity }] };
    this.api.createRequest(body).subscribe(() => { this.toast.success('Draft created'); this.showNew.set(false); this.load(); });
  }
  submit(r: PharmacyRequest): void { this.api.submitRequest(r.id).subscribe(() => { this.toast.success('Submitted to depot'); this.load(); }); }
  cancel(r: PharmacyRequest): void { this.api.cancelRequest(r.id).subscribe(() => { this.toast.success('Cancelled'); this.load(); }); }
}
