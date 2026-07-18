import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth.guard';
import { LoginComponent } from './features/login/login.component';
import { ShellComponent } from './shared/shell.component';
import { HomeRedirectComponent } from './shared/home-redirect.component';
import { PharmacyDashboardComponent } from './features/pharmacy/pharmacy-dashboard.component';
import { InventoryComponent } from './features/pharmacy/inventory.component';
import { PredictionsComponent } from './features/pharmacy/predictions.component';
import { RequestsComponent } from './features/pharmacy/requests.component';
import { PharmacyDeliveriesComponent } from './features/pharmacy/pharmacy-deliveries.component';
import { DepotDashboardComponent } from './features/depot/depot-dashboard.component';
import { DepotRequestsComponent } from './features/depot/depot-requests.component';
import { DepotDeliveriesComponent } from './features/depot/depot-deliveries.component';
import { NotificationsComponent } from './shared/notifications.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '', canActivate: [authGuard], component: ShellComponent, children: [
      { path: '', pathMatch: 'full', component: HomeRedirectComponent },
      { path: 'pharmacy', canActivate: [roleGuard(['PHARMACY'])], children: [
        { path: '', component: PharmacyDashboardComponent },
        { path: 'inventory', component: InventoryComponent },
        { path: 'predictions', component: PredictionsComponent },
        { path: 'requests', component: RequestsComponent },
        { path: 'deliveries', component: PharmacyDeliveriesComponent }
      ]},
      { path: 'depot', canActivate: [roleGuard(['DEPOT', 'ADMIN'])], children: [
        { path: '', component: DepotDashboardComponent },
        { path: 'requests', component: DepotRequestsComponent },
        { path: 'deliveries', component: DepotDeliveriesComponent }
      ]},
      { path: 'notifications', component: NotificationsComponent }
    ]
  },
  { path: '**', redirectTo: '' }
];
