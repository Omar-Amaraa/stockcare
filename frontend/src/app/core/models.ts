export type Role = 'PHARMACY' | 'DEPOT' | 'ADMIN';

export interface AuthUser {
  id: string; email: string; fullName: string; role: Role;
  pharmacyId?: string; pharmacyName?: string; depotId?: string; depotName?: string;
}
export interface LoginResponse { token: string; tokenType: string; expiresAt: string; user: AuthUser; }

export interface Medication {
  id: string; name: string; genericName?: string; dosage?: string; pharmaceuticalForm?: string;
  packageSize?: string; sku?: string; barcode?: string; atcCode?: string; category?: string;
  coldChain: boolean; criticalityScore?: number;
}
export interface InventoryItem {
  id: string; pharmacyId: string; medication: Medication; currentQuantity: number;
  minimumQuantity: number; averageDailyConsumption: number; reorderThreshold?: number;
  expirationDate?: string; lowStock: boolean;
}
export interface StockAdjustment {
  id: string; delta: number; quantityBefore: number; quantityAfter: number;
  reason: string; note?: string; occurredAt: string;
}
export interface Prediction {
  id: string; pharmacyId: string; medication: Medication; currentStock: number;
  predictedShortageDate?: string; estimatedRemainingDays?: number; predictedMissingQuantity: number;
  confidence?: number; reason: string; modelVersion: string; simulated: boolean; predictionTime: string;
}
export interface PriorityResult {
  id: string; requestId: string; coefficient: number; factors: Record<string, number>;
  explanation: string; calculationVersion: string; simulated: boolean; calculatedAt: string;
}
export interface RequestItem { id: string; medication: Medication; requestedQuantity: number; note?: string; }
export type RequestStatus = 'DRAFT'|'SUBMITTED'|'RECEIVED'|'PRIORITY_PENDING'|'PRIORITIZED'|'PLANNED'|'PREPARING'|'IN_DELIVERY'|'DELIVERED'|'CANCELLED'|'REJECTED';
export type PredictionRunStatus = 'PENDING'|'PROCESSING'|'COMPLETED'|'FAILED';
export interface PredictionState {
  status: PredictionRunStatus; outdated: boolean; correlationId?: string; modelVersion?: string;
  retryCount: number; shortageCount: number; startedAt?: string; completedAt?: string;
  errorMessage?: string; predictions: Prediction[];
}
export interface WorkflowUpdate { type: string; entityType: string; entityId?: string; status: string; message: string; at: string; }
export type Urgency = 'LOW'|'NORMAL'|'HIGH'|'CRITICAL';
export interface PharmacyRequest {
  id: string; pharmacyId: string; pharmacyName: string; depotId: string; status: RequestStatus;
  urgency: Urgency; notes?: string; internalNotes?: string; affectedPatients?: number;
  sourcePredictionId?: string; submittedAt?: string; createdAt: string;
  items: RequestItem[]; priority?: PriorityResult;
}
export interface Pharmacy {
  id: string; code: string; name: string; region?: string; city?: string;
  latitude?: number; longitude?: number; phone?: string;
}
export interface Depot {
  id: string; code: string; name: string; region?: string; city?: string;
  latitude?: number; longitude?: number; pharmacyCount: number;
}
export interface Vehicle { id: string; code: string; plateNumber?: string; capacityUnits: number; refrigerated: boolean; active: boolean; }
export interface Driver { id: string; fullName: string; phone?: string; active: boolean; }
export type DeliveryStatus = 'PLANNED'|'PREPARING'|'READY'|'STARTED'|'IN_TRANSIT'|'ARRIVED_AT_STOP'|'DELIVERED'|'FAILED'|'RETURNED'|'CANCELLED';
export interface RouteStop {
  id: string; sequence: number; pharmacyId: string; pharmacyName: string; requestId?: string;
  latitude: number; longitude: number; status: string; estimatedArrivalMinute?: number;
}
export interface DeliveryItemDto { id: string; pharmacyId: string; requestId?: string; medicationId: string; medicationName: string; quantity: number; }
export interface Delivery {
  id: string; reference: string; status: DeliveryStatus; depotId: string;
  depotLatitude?: number; depotLongitude?: number; vehicle?: Vehicle; driver?: Driver;
  simulated: boolean; optimized: boolean; optimizerVersion?: string;
  totalDistanceKm?: number; totalDurationMinutes?: number;
  currentLatitude?: number; currentLongitude?: number; currentStopIndex?: number;
  progress: number; etaMinutes?: number; plannedAt?: string; startedAt?: string; completedAt?: string;
  stops: RouteStop[]; items: DeliveryItemDto[];
}
export interface TrackingUpdate {
  deliveryId: string; status: DeliveryStatus; latitude?: number; longitude?: number;
  currentStopIndex?: number; progress: number; etaMinutes?: number; timestamp: string;
}
export interface ClockStatus { mode: 'REAL'|'SIMULATED'; simulationActive: boolean; frozen: boolean; effectiveNow: string; realNow: string; }
export interface Notification { id: string; type: string; title: string; message: string; read: boolean; referenceId?: string; referenceType?: string; createdAt: string; }
export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; last: boolean; }
