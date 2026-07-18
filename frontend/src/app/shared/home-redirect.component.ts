import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({ selector: 'app-home-redirect', standalone: true, template: '' })
export class HomeRedirectComponent implements OnInit {
  private auth = inject(AuthService);
  private router = inject(Router);
  ngOnInit(): void {
    const role = this.auth.user()?.role;
    this.router.navigate([role === 'PHARMACY' ? '/pharmacy' : '/depot']);
  }
}
