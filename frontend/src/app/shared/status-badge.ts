// Maps request/delivery statuses and urgencies to CSS badge classes.
export function statusClass(s: string): string {
  const map: Record<string, string> = {
    DRAFT: 'b-grey', SUBMITTED: 'b-blue', RECEIVED: 'b-blue', PRIORITY_PENDING: 'b-purple', PRIORITIZED: 'b-purple',
    PENDING: 'b-grey', PROCESSING: 'b-orange', COMPLETED: 'b-green', CALCULATING: 'b-orange', CALCULATED: 'b-green',
    PLANNED: 'b-teal', PREPARING: 'b-orange', IN_DELIVERY: 'b-orange', STARTED: 'b-orange',
    IN_TRANSIT: 'b-orange', ARRIVED_AT_STOP: 'b-orange', DELIVERED: 'b-green',
    CANCELLED: 'b-grey', REJECTED: 'b-red', FAILED: 'b-red', RETURNED: 'b-red', READY: 'b-teal',
    LOW: 'b-grey', NORMAL: 'b-blue', HIGH: 'b-orange', CRITICAL: 'b-red'
  };
  return 'badge ' + (map[s] ?? 'b-grey');
}
