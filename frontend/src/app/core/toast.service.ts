import { Injectable, signal } from '@angular/core';

export interface Toast { id: number; text: string; type: 'success' | 'error' | 'info'; }

@Injectable({ providedIn: 'root' })
export class ToastService {
  private seq = 0;
  toasts = signal<Toast[]>([]);

  show(text: string, type: Toast['type'] = 'info', ms = 3200): void {
    const id = ++this.seq;
    this.toasts.update((t) => [...t, { id, text, type }]);
    setTimeout(() => this.dismiss(id), ms);
  }
  success(text: string): void { this.show(text, 'success'); }
  error(text: string): void { this.show(text, 'error', 4500); }
  dismiss(id: number): void { this.toasts.update((t) => t.filter((x) => x.id !== id)); }
}
