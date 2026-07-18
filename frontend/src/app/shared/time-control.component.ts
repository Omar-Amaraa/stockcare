import { Component, OnInit, inject, signal, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { ClockStatus } from '../core/models';

@Component({
  selector: 'app-time-control',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
  <div class="card card-p">
    <div class="flex items-center gap-2">
      <span class="stat-icon bg-amber-50 text-amber-600"><span class="material-icons">schedule</span></span>
      <div>
        <p class="card-title">Simulated application time</p>
        <p class="text-xs text-slate-500">
          Mode <span class="font-semibold" [class.text-amber-600]="clock()?.simulationActive">{{ clock()?.mode }}</span>
          · now {{ clock()?.effectiveNow | date:'medium' }}
        </p>
      </div>
    </div>
    <div class="mt-4 flex flex-wrap items-end gap-3">
      <div>
        <label class="label">Simulated date & time</label>
        <input class="input" type="datetime-local" [(ngModel)]="local">
      </div>
      <label class="flex cursor-pointer items-center gap-2 pb-2 text-sm text-slate-600">
        <input type="checkbox" class="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500" [(ngModel)]="frozen"> Freeze
      </label>
    </div>
    <div class="mt-4 flex flex-wrap gap-2">
      <button class="btn btn-primary btn-sm" (click)="setTime()">Set simulated</button>
      <button class="btn btn-ghost btn-sm" (click)="advance(0,6)">+6h</button>
      <button class="btn btn-ghost btn-sm" (click)="advance(1,0)">+1 day</button>
      <button class="btn btn-ghost btn-sm" (click)="advance(7,0)">+7 days</button>
      <button class="btn btn-ghost btn-sm text-rose-600" (click)="reset()">Reset to real time</button>
    </div>
  </div>`
})
export class TimeControlComponent implements OnInit {
  private api = inject(ApiService);
  clock = signal<ClockStatus | null>(null);
  changed = output<void>();
  local = '';
  frozen = false;

  ngOnInit(): void { this.load(); }
  load(): void { this.api.clock().subscribe((c) => this.clock.set(c)); }
  setTime(): void {
    if (!this.local) return;
    this.api.setSimulated(new Date(this.local).toISOString(), this.frozen).subscribe((c) => { this.clock.set(c); this.changed.emit(); });
  }
  advance(days: number, hours: number): void { this.api.advanceTime(days, hours).subscribe((c) => { this.clock.set(c); this.changed.emit(); }); }
  reset(): void { this.api.resetTime().subscribe((c) => { this.clock.set(c); this.changed.emit(); }); }
}
