import { Injectable, NgZone, inject } from '@angular/core';
import { Subject } from 'rxjs';
import { AuthService } from './auth.service';
import { WorkflowUpdate } from './models';

/**
 * Subscribes to the backend workflow SSE stream so the UI reflects automated processing
 * (prediction / priority / request state) without page reloads. Auto-reconnects on drop.
 */
@Injectable({ providedIn: 'root' })
export class StreamService {
  private auth = inject(AuthService);
  private zone = inject(NgZone);
  private es?: EventSource;
  private subject = new Subject<WorkflowUpdate>();
  updates = this.subject.asObservable();

  connect(): void {
    if (this.es || !this.auth.token) return;
    this.open();
  }

  private open(): void {
    const token = this.auth.token;
    if (!token) return;
    this.es = new EventSource(`/api/stream?access_token=${token}`);
    this.es.addEventListener('workflow', (ev: MessageEvent) => {
      this.zone.run(() => {
        try { this.subject.next(JSON.parse(ev.data) as WorkflowUpdate); } catch { /* ignore */ }
      });
    });
    this.es.onerror = () => {
      this.es?.close();
      this.es = undefined;
      setTimeout(() => this.open(), 5000); // reconnect
    };
  }

  disconnect(): void { this.es?.close(); this.es = undefined; }
}
