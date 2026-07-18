import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService } from '../core/toast.service';

@Component({
  selector: 'app-toasts',
  standalone: true,
  imports: [CommonModule],
  template: `
  <div class="toast-wrap" aria-live="polite">
    <div *ngFor="let t of toast.toasts()"
         class="toast flex min-w-[260px] items-center gap-3 rounded-xl bg-surface/95 px-4 py-3 shadow-lift ring-1 ring-line backdrop-blur-md">
      <span class="material-icons text-[20px]"
            [class.text-accent-500]="t.type==='success'"
            [class.text-rose-500]="t.type==='error'"
            [class.text-brand-500]="t.type==='info'">
        {{ t.type==='success' ? 'check_circle' : t.type==='error' ? 'error' : 'info' }}
      </span>
      <span class="flex-1 text-sm text-ink-soft">{{ t.text }}</span>
      <button class="material-icons text-[18px] text-ink-faint transition-colors hover:text-ink" (click)="toast.dismiss(t.id)" aria-label="Dismiss">close</button>
    </div>
  </div>`
})
export class ToastsComponent {
  toast = inject(ToastService);
}
