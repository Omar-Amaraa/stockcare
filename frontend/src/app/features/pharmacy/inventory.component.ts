import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { InventoryItem, Medication, StockAdjustment } from '../../core/models';

@Component({
  selector: 'app-inventory',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
  <div class="flex items-center justify-between">
    <div><h1>Inventory</h1><p class="text-sm text-slate-500">Manage your pharmacy's stock manually.</p></div>
    <button class="btn btn-primary" (click)="showAdd.set(!showAdd())">
      <span class="material-icons text-[18px]">{{ showAdd() ? 'close' : 'add' }}</span>{{ showAdd() ? 'Close' : 'Add medication' }}
    </button>
  </div>

  <!-- Add form -->
  <div *ngIf="showAdd()" class="card card-p mt-5">
    <div class="mb-4 flex items-center gap-4 text-sm">
      <label class="flex items-center gap-2"><input type="radio" [value]="false" [(ngModel)]="createNew" name="mode" class="text-brand-600"> Existing medication</label>
      <label class="flex items-center gap-2"><input type="radio" [value]="true" [(ngModel)]="createNew" name="mode" class="text-brand-600"> New medication</label>
    </div>

    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <div *ngIf="!createNew">
        <label class="label">Medication</label>
        <select class="input" [(ngModel)]="form.medicationId">
          <option value="">Select…</option>
          <option *ngFor="let m of meds()" [value]="m.id">{{ m.name }}{{ m.coldChain ? ' ❄' : '' }}</option>
        </select>
      </div>
      <ng-container *ngIf="createNew">
        <div><label class="label">Name</label><input class="input" [(ngModel)]="newMed.name" placeholder="e.g. Paracetamol 500mg"></div>
        <div><label class="label">Form</label><input class="input" [(ngModel)]="newMed.pharmaceuticalForm" placeholder="TABLET"></div>
        <div><label class="label">Category</label><input class="input" [(ngModel)]="newMed.category" placeholder="Analgesic"></div>
        <label class="flex items-center gap-2 pt-6 text-sm text-slate-600"><input type="checkbox" class="h-4 w-4 rounded text-brand-600" [(ngModel)]="newMed.coldChain"> Cold chain ❄</label>
      </ng-container>
      <div><label class="label">Current qty</label><input class="input" type="number" [(ngModel)]="form.currentQuantity"></div>
      <div><label class="label">Min desired</label><input class="input" type="number" [(ngModel)]="form.minimumQuantity"></div>
      <div><label class="label">Avg daily use</label><input class="input" type="number" [(ngModel)]="form.averageDailyConsumption"></div>
      <div><label class="label">Reorder threshold</label><input class="input" type="number" [(ngModel)]="form.reorderThreshold"></div>
      <div><label class="label">Expiration</label><input class="input" type="date" [(ngModel)]="form.expirationDate"></div>
    </div>
    <div class="mt-4"><button class="btn btn-primary" (click)="add()">Add to inventory</button></div>
  </div>

  <!-- Table -->
  <div class="card mt-5 overflow-hidden">
    <div class="overflow-x-auto">
      <table class="tbl">
        <thead><tr>
          <th>Medication</th><th>Stock</th><th>Daily use</th><th>Expiry</th><th class="text-right">Actions</th>
        </tr></thead>
        <tbody>
          <ng-container *ngFor="let i of items()">
            <tr>
              <td>
                <div class="font-medium text-slate-800">{{ i.medication.name }}
                  <span *ngIf="i.medication.coldChain" title="Cold chain" class="ml-1 text-sky-500">❄</span>
                </div>
                <div class="text-xs text-slate-400">{{ i.medication.category || i.medication.pharmaceuticalForm }}</div>
              </td>
              <td>
                <span class="text-base font-semibold" [class.text-rose-600]="i.lowStock">{{ i.currentQuantity }}</span>
                <span class="text-xs text-slate-400"> / min {{ i.minimumQuantity }}</span>
                <span *ngIf="i.lowStock" class="badge b-red ml-2">LOW</span>
              </td>
              <td class="text-slate-600">{{ i.averageDailyConsumption }}/day</td>
              <td class="text-slate-600">{{ i.expirationDate || '—' }}</td>
              <td>
                <div class="flex justify-end gap-1">
                  <button class="btn btn-ghost btn-sm" (click)="adjust(i, 10)">+10</button>
                  <button class="btn btn-ghost btn-sm" (click)="adjust(i, -5)">−5</button>
                  <button class="btn btn-ghost btn-sm" (click)="edit(i)" title="Edit"><span class="material-icons text-[18px]">edit</span></button>
                  <button class="btn btn-ghost btn-sm" (click)="openHistory(i)" title="History"><span class="material-icons text-[18px]">history</span></button>
                  <button class="btn btn-ghost btn-sm text-rose-600" (click)="remove(i)" title="Delete"><span class="material-icons text-[18px]">delete</span></button>
                </div>
              </td>
            </tr>
            <!-- inline edit -->
            <tr *ngIf="editing()?.id === i.id">
              <td colspan="5" class="bg-slate-50">
                <div class="flex flex-wrap items-end gap-3">
                  <div><label class="label">Min desired</label><input class="input w-28" type="number" [(ngModel)]="editForm.minimumQuantity"></div>
                  <div><label class="label">Avg daily</label><input class="input w-28" type="number" [(ngModel)]="editForm.averageDailyConsumption"></div>
                  <div><label class="label">Reorder</label><input class="input w-28" type="number" [(ngModel)]="editForm.reorderThreshold"></div>
                  <div><label class="label">Expiration</label><input class="input" type="date" [(ngModel)]="editForm.expirationDate"></div>
                  <button class="btn btn-primary btn-sm" (click)="saveEdit(i)">Save</button>
                  <button class="btn btn-ghost btn-sm" (click)="editing.set(null)">Cancel</button>
                </div>
              </td>
            </tr>
          </ng-container>
          <tr *ngIf="items().length === 0"><td colspan="5" class="py-10 text-center text-slate-400">No inventory yet — add a medication above.</td></tr>
        </tbody>
      </table>
    </div>
  </div>

  <!-- History modal -->
  <div *ngIf="historyItem()" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4" (click)="historyItem.set(null)">
    <div class="card w-full max-w-lg" (click)="$event.stopPropagation()">
      <div class="flex items-center justify-between border-b border-slate-100 p-5">
        <div><p class="card-title">Stock history</p><p class="text-xs text-slate-500">{{ historyItem()?.medication?.name }}</p></div>
        <button class="material-icons text-slate-400 hover:text-slate-600" (click)="historyItem.set(null)">close</button>
      </div>
      <div class="max-h-[60vh] overflow-y-auto divide-y divide-slate-100">
        <div *ngFor="let a of history()" class="flex items-center gap-3 p-4">
          <span class="badge" [ngClass]="a.delta >= 0 ? 'b-green' : 'b-red'">{{ a.delta >= 0 ? '+' : '' }}{{ a.delta }}</span>
          <div class="flex-1">
            <p class="text-sm text-slate-700">{{ a.reason }} <span class="text-slate-400">→ {{ a.quantityAfter }}</span></p>
            <p class="text-xs text-slate-400">{{ a.occurredAt | date:'medium' }}{{ a.note ? ' · ' + a.note : '' }}</p>
          </div>
        </div>
        <div *ngIf="history().length === 0" class="p-8 text-center text-sm text-slate-400">No adjustments recorded.</div>
      </div>
    </div>
  </div>`
})
export class InventoryComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  items = signal<InventoryItem[]>([]);
  meds = signal<Medication[]>([]);
  showAdd = signal(false);
  createNew = false;
  editing = signal<InventoryItem | null>(null);
  historyItem = signal<InventoryItem | null>(null);
  history = signal<StockAdjustment[]>([]);

  form: any = { medicationId: '', currentQuantity: 0, minimumQuantity: 0, averageDailyConsumption: 0, reorderThreshold: null, expirationDate: null };
  newMed: any = { name: '', pharmaceuticalForm: '', category: '', coldChain: false };
  editForm: any = {};

  ngOnInit(): void { this.load(); this.loadMeds(); }
  private pid(): string { return this.auth.user()!.pharmacyId!; }
  load(): void { this.api.myInventory().subscribe((r) => this.items.set(r)); }
  loadMeds(): void { this.api.medications().subscribe((p) => this.meds.set(p.content)); }

  add(): void {
    const proceed = (medicationId: string) => {
      this.api.addInventory(this.pid(), { ...this.form, medicationId }).subscribe({
        next: () => { this.toast.success('Added to inventory'); this.showAdd.set(false); this.form.medicationId = ''; this.load(); },
        error: (e) => this.toast.error(e.error?.message || 'Could not add')
      });
    };
    if (this.createNew) {
      if (!this.newMed.name) { this.toast.error('Medication name required'); return; }
      this.api.createMedication({ ...this.newMed, coldChain: !!this.newMed.coldChain }).subscribe({
        next: (m) => { this.loadMeds(); proceed(m.id); },
        error: (e) => this.toast.error(e.error?.message || 'Could not create medication')
      });
    } else {
      if (!this.form.medicationId) { this.toast.error('Select a medication'); return; }
      proceed(this.form.medicationId);
    }
  }

  adjust(i: InventoryItem, delta: number): void {
    this.api.adjustStock(this.pid(), i.id, { delta, reason: delta < 0 ? 'SALE' : 'RESTOCK' }).subscribe({
      next: () => this.load(), error: (e) => this.toast.error(e.error?.message || 'Adjustment failed')
    });
  }
  edit(i: InventoryItem): void {
    this.editing.set(i);
    this.editForm = { minimumQuantity: i.minimumQuantity, averageDailyConsumption: i.averageDailyConsumption, reorderThreshold: i.reorderThreshold, expirationDate: i.expirationDate };
  }
  saveEdit(i: InventoryItem): void {
    this.api.updateInventory(this.pid(), i.id, this.editForm).subscribe(() => { this.toast.success('Updated'); this.editing.set(null); this.load(); });
  }
  remove(i: InventoryItem): void { this.api.deleteInventory(this.pid(), i.id).subscribe(() => { this.toast.success('Removed'); this.load(); }); }
  openHistory(i: InventoryItem): void {
    this.historyItem.set(i);
    this.api.adjustmentHistory(this.pid(), i.id).subscribe((p) => this.history.set(p.content));
  }
}
