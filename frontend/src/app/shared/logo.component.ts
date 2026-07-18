import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-logo',
  standalone: true,
  imports: [CommonModule],
  template: `
  <span class="inline-flex select-none items-center gap-2.5">
    <img src="assets/logo-mark.svg" [style.height.px]="size" [style.width.px]="size" alt="StockCare" />
    <span *ngIf="!compact" class="font-display font-extrabold tracking-tight" [style.fontSize.px]="size*0.62">
      <span class="text-brand-600 dark:text-brand-300">Stock</span><span class="text-accent-500 dark:text-accent-300">Care</span>
    </span>
  </span>`
})
export class LogoComponent {
  @Input() size = 34;
  @Input() compact = false;
}
