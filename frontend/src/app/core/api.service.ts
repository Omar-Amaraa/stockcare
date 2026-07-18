import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ClockStatus, Delivery, Depot, Driver, InventoryItem, Medication, Notification, Page,
  Pharmacy, PharmacyRequest, Prediction, PredictionState, PriorityResult, RequestStatus, StockAdjustment, Urgency, Vehicle
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) {}

  // Pharmacy / depot
  myPharmacy(): Observable<Pharmacy> { return this.http.get<Pharmacy>('/api/pharmacies/me'); }
  pharmacies(): Observable<Pharmacy[]> { return this.http.get<Pharmacy[]>('/api/pharmacies'); }
  myDepot(): Observable<Depot> { return this.http.get<Depot>('/api/depots/me'); }

  // Medications
  medications(query = ''): Observable<Page<Medication>> {
    return this.http.get<Page<Medication>>('/api/medications', { params: new HttpParams().set('query', query).set('size', '100') });
  }
  createMedication(body: Partial<Medication>): Observable<Medication> { return this.http.post<Medication>('/api/medications', body); }

  // Inventory
  myInventory(): Observable<InventoryItem[]> { return this.http.get<InventoryItem[]>('/api/inventory/me'); }
  addInventory(pharmacyId: string, body: any): Observable<InventoryItem> {
    return this.http.post<InventoryItem>(`/api/inventory/pharmacies/${pharmacyId}/items`, body);
  }
  updateInventory(pharmacyId: string, itemId: string, body: any): Observable<InventoryItem> {
    return this.http.put<InventoryItem>(`/api/inventory/pharmacies/${pharmacyId}/items/${itemId}`, body);
  }
  deleteInventory(pharmacyId: string, itemId: string): Observable<void> {
    return this.http.delete<void>(`/api/inventory/pharmacies/${pharmacyId}/items/${itemId}`);
  }
  adjustStock(pharmacyId: string, itemId: string, body: any): Observable<InventoryItem> {
    return this.http.post<InventoryItem>(`/api/inventory/pharmacies/${pharmacyId}/items/${itemId}/adjustments`, body);
  }
  adjustmentHistory(pharmacyId: string, itemId: string): Observable<Page<StockAdjustment>> {
    return this.http.get<Page<StockAdjustment>>(
      `/api/inventory/pharmacies/${pharmacyId}/items/${itemId}/adjustments`,
      { params: new HttpParams().set('size', '50') });
  }

  // Predictions (run automatically; these read state / recover from failure)
  myPredictions(): Observable<Prediction[]> { return this.http.get<Prediction[]>('/api/predictions/me'); }
  predictionState(): Observable<PredictionState> { return this.http.get<PredictionState>('/api/predictions/me/state'); }
  retryPrediction(): Observable<PredictionState> { return this.http.post<PredictionState>('/api/predictions/me/retry', {}); }

  // Requests (pharmacy)
  myRequests(): Observable<Page<PharmacyRequest>> { return this.http.get<Page<PharmacyRequest>>('/api/requests/me', { params: new HttpParams().set('size', '100') }); }
  getRequest(id: string): Observable<PharmacyRequest> { return this.http.get<PharmacyRequest>(`/api/requests/${id}`); }
  createRequest(body: any): Observable<PharmacyRequest> { return this.http.post<PharmacyRequest>('/api/requests', body); }
  draftFromPrediction(body: any): Observable<PharmacyRequest> { return this.http.post<PharmacyRequest>('/api/requests/from-prediction', body); }
  submitRequest(id: string): Observable<PharmacyRequest> { return this.http.post<PharmacyRequest>(`/api/requests/${id}/submit`, {}); }
  cancelRequest(id: string): Observable<PharmacyRequest> { return this.http.post<PharmacyRequest>(`/api/requests/${id}/cancel`, {}); }

  // Requests (depot)
  depotRequests(status?: RequestStatus, urgency?: Urgency): Observable<Page<PharmacyRequest>> {
    let p = new HttpParams().set('size', '100');
    if (status) p = p.set('status', status);
    if (urgency) p = p.set('urgency', urgency);
    return this.http.get<Page<PharmacyRequest>>('/api/depot/requests', { params: p });
  }
  changeStatus(id: string, status: RequestStatus): Observable<PharmacyRequest> { return this.http.post<PharmacyRequest>(`/api/depot/requests/${id}/status`, { status }); }
  internalNote(id: string, note: string): Observable<PharmacyRequest> { return this.http.post<PharmacyRequest>(`/api/depot/requests/${id}/internal-note`, { note }); }
  prioritize(id: string): Observable<PriorityResult> { return this.http.post<PriorityResult>(`/api/depot/requests/${id}/prioritize`, {}); }
  approveRequest(id: string): Observable<PharmacyRequest> { return this.http.post<PharmacyRequest>(`/api/depot/requests/${id}/approve`, {}); }

  // Fleet + deliveries
  vehicles(): Observable<Vehicle[]> { return this.http.get<Vehicle[]>('/api/vehicles'); }
  drivers(): Observable<Driver[]> { return this.http.get<Driver[]>('/api/drivers'); }
  depotDeliveries(): Observable<Delivery[]> { return this.http.get<Delivery[]>('/api/deliveries'); }
  myDeliveries(): Observable<Delivery[]> { return this.http.get<Delivery[]>('/api/deliveries/me'); }
  getDelivery(id: string): Observable<Delivery> { return this.http.get<Delivery>(`/api/deliveries/${id}`); }
  planDelivery(body: any): Observable<Delivery> { return this.http.post<Delivery>('/api/deliveries/plan', body); }
  deliveryAction(id: string, action: string): Observable<Delivery> { return this.http.post<Delivery>(`/api/deliveries/${id}/${action}`, {}); }

  // Time
  clock(): Observable<ClockStatus> { return this.http.get<ClockStatus>('/api/time'); }
  setSimulated(dateTime: string, frozen: boolean): Observable<ClockStatus> { return this.http.post<ClockStatus>('/api/time/simulate', { dateTime, frozen }); }
  advanceTime(days: number, hours: number): Observable<ClockStatus> { return this.http.post<ClockStatus>('/api/time/advance', { days, hours }); }
  resetTime(): Observable<ClockStatus> { return this.http.post<ClockStatus>('/api/time/reset', {}); }

  // Notifications
  notifications(): Observable<Page<Notification>> { return this.http.get<Page<Notification>>('/api/notifications', { params: new HttpParams().set('size', '50') }); }
  unreadCount(): Observable<{ count: number }> { return this.http.get<{ count: number }>('/api/notifications/unread-count'); }
  markRead(id: string): Observable<Notification> { return this.http.post<Notification>(`/api/notifications/${id}/read`, {}); }
}
