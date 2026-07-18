import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { StreamService } from '../../core/stream.service';
import { ToastService } from '../../core/toast.service';
import { PredictionState } from '../../core/models';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-predictions',
  standalone: true,
  imports: [CommonModule],
  template: `
  <div class="flex flex-wrap items-center justify-between gap-3">
    <div>
      <h1>Predicted shortages</h1>
      <p class="mt-1 text-sm text-ink-mute">Forecasts run automatically when your inventory or the application time changes.</p>
    </div>
    <div class="flex items-center gap-2">
      <ng-container [ngSwitch]="statusKey()">
        <span *ngSwitchCase="'PROCESSING'" class="badge b-orange"><span class="material-icons mr-1 animate-spin text-[14px]">progress_activity</span>Processing</span>
        <span *ngSwitchCase="'OUTDATED'" class="badge b-orange"><span class="material-icons mr-1 text-[14px]">update</span>Updating…</span>
        <span *ngSwitchCase="'FAILED'" class="badge b-red"><span class="material-icons mr-1 text-[14px]">error</span>Failed</span>
        <span *ngSwitchCase="'COMPLETED'" class="badge b-green"><span class="material-icons mr-1 text-[14px]">check_circle</span>Up to date</span>
        <span *ngSwitchDefault class="badge b-grey">Pending</span>
      </ng-container>
      <button *ngIf="statusKey()==='FAILED'" class="btn btn-ghost btn-sm" (click)="retry()">
        <span class="material-icons text-[16px]">refresh</span> Retry
      </button>
    </div>
  </div>

  <div *ngIf="state() as s" class="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 rounded-xl bg-raised/70 px-4 py-2.5 text-xs text-ink-mute ring-1 ring-line/60">
    <span class="inline-flex items-center gap-1"><span class="material-icons text-[15px]">model_training</span> Model: <b class="text-ink-soft">{{ s.modelVersion || '—' }}</b></span>
    <span *ngIf="s.completedAt">Updated {{ s.completedAt | date:'short' }}</span>
    <span *ngIf="s.predictions.length" class="badge" [ngClass]="s.predictions[0].simulated ? 'b-orange' : 'b-teal'">{{ s.predictions[0].simulated ? 'SIMULATED' : 'MODEL' }}</span>
    <span *ngIf="s.errorMessage" class="text-rose-600 dark:text-rose-400">{{ s.errorMessage }}</span>
  </div>

  <!-- Skeleton while first processing with no results yet -->
  <div *ngIf="statusKey()==='PROCESSING' && !state()?.predictions?.length" class="card mt-4 p-5">
    <div class="space-y-3">
      <div class="skeleton h-4 w-1/3"></div>
      <div class="skeleton h-4 w-full"></div>
      <div class="skeleton h-4 w-2/3"></div>
    </div>
  </div>

  <div *ngIf="state()?.predictions?.length" class="card mt-4 overflow-hidden animate-fade-up">
    <div class="overflow-x-auto">
      <table class="tbl">
        <thead><tr><th>Medication</th><th>Current stock</th><th>Est. depletion</th><th>Days left</th><th>Shortfall</th></tr></thead>
        <tbody>
          <tr *ngFor="let p of state()!.predictions; trackBy: trackId">
            <td class="font-medium text-ink">{{ p.medication.name }}</td>
            <td class="text-ink-soft">{{ p.currentStock }}</td>
            <td><span class="badge b-orange">{{ p.predictedShortageDate }}</span></td>
            <td class="text-ink-soft">{{ p.estimatedRemainingDays ?? '—' }}</td>
            <td class="font-semibold text-rose-600 dark:text-rose-400">{{ p.predictedMissingQuantity }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>

  <div *ngIf="statusKey()==='COMPLETED' && !state()?.predictions?.length" class="card mt-4 animate-fade-up p-10 text-center">
    <span class="material-icons text-3xl text-emerald-400">verified</span>
    <p class="mt-2 font-medium text-ink">No shortages predicted</p>
    <p class="text-sm text-ink-mute">Your stock covers forecast demand for now. This updates automatically as data changes.</p>
  </div>

  <p *ngIf="state()?.predictions?.length" class="mt-3 rounded-xl bg-surface p-3 text-xs text-ink-mute ring-1 ring-line/70">
    {{ state()!.predictions[0].reason }}
  </p>`
})
export class PredictionsComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private stream = inject(StreamService);
  private toast = inject(ToastService);
  state = signal<PredictionState | null>(null);
  private sub?: Subscription;
  private poll?: any;

  ngOnInit(): void {
    this.load();
    this.stream.connect();
    this.sub = this.stream.updates.subscribe((u) => { if (u.type === 'PREDICTION_STATE') this.load(); });
  }
  ngOnDestroy(): void { this.sub?.unsubscribe(); clearInterval(this.poll); }

  statusKey(): string {
    const s = this.state();
    if (!s) return 'PENDING';
    if (s.status === 'COMPLETED' && s.outdated) return 'OUTDATED';
    return s.status;
  }
  load(): void {
    this.api.predictionState().subscribe((s) => {
      this.state.set(s);
      // Controlled polling fallback while processing, in case SSE is unavailable.
      clearInterval(this.poll);
      if (s.status === 'PROCESSING' || s.outdated) this.poll = setInterval(() => this.load(), 3000);
    });
  }
  retry(): void { this.api.retryPrediction().subscribe((s) => { this.state.set(s); this.toast.show('Retrying prediction…'); }); }
  trackId(_: number, p: { id: string }): string { return p.id; }
}
