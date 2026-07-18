import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { AuthUser, LoginResponse } from './models';

const TOKEN_KEY = 'stockcare.token';
const USER_KEY = 'stockcare.user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private _user = signal<AuthUser | null>(this.readUser());
  user = this._user.asReadonly();
  isPharmacy = computed(() => this._user()?.role === 'PHARMACY');
  isDepot = computed(() => this._user()?.role === 'DEPOT' || this._user()?.role === 'ADMIN');

  constructor(private http: HttpClient) {}

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', { email, password }).pipe(
      tap((res) => {
        localStorage.setItem(TOKEN_KEY, res.token);
        localStorage.setItem(USER_KEY, JSON.stringify(res.user));
        this._user.set(res.user);
      })
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this._user.set(null);
  }

  get token(): string | null { return localStorage.getItem(TOKEN_KEY); }
  get isAuthenticated(): boolean { return !!this.token; }

  private readUser(): AuthUser | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) as AuthUser : null;
  }
}
