import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService } from '../core/toast.service';

@Component({
  selector: 'app-toasts',
  standalone: true,
  imports: [CommonModule],
  template: `
  <div class="toast-wrap">
    <div *ngFor="let t of toast.toasts()"
         class="toast flex items-center gap-3 rounded-xl bg-white px-4 py-3 shadow-card ring-1 ring-slate-100 min-w-[260px]">
      <span class="material-icons text-[20px]"
            [class.text-accent-500]="t.type==='success'"
            [class.text-rose-500]="t.type==='error'"
            [class.text-brand-500]="t.type==='info'">
        {{ t.type==='success' ? 'check_circle' : t.type==='error' ? 'error' : 'info' }}
      </span>
      <span class="text-sm text-slate-700 flex-1">{{ t.text }}</span>
      <button class="material-icons text-[18px] text-slate-400 hover:text-slate-600" (click)="toast.dismiss(t.id)">close</button>
    </div>
  </div>`
})
export class ToastsComponent {
  toast = inject(ToastService);
}
