
export interface Contact {
  name: string;
  phone: string;
  relationship: string;
}

export interface HistoryItem {
  id?: string;
  emotion: string;
  latitude: number;
  longitude: number;
  status: string;
  timestamp: number;
  audioUrl?: string;
  reason?: string;
}

export interface User {
  id?: string;
  name?: string;
  displayName?: string;
  email?: string;
  phone?: string;
  role?: 'admin' | 'member' | 'police' | 'parent';
  batteryLevel?: number;
  dangerZoneActive?: boolean;
  lastLatitude?: number;
  lastLongitude?: number;
  lastUpdateTimestamp?: number;
  uniqueId?: string;
  station?: string;
  contacts?: Record<string, Contact>;
  history?: Record<string, HistoryItem>;
  lastLogin?: number;
  timestamp?: number;
}

// Alert interface for top-level alerts (like -OkUnen8V8Cs5qMkkRUk)
export interface TopLevelAlert {
  id?: string;
  confidence?: number;
  emotion: string;
  latitude: number;
  longitude: number;
  locationName?: string;
  phone?: string;
  status?: string;
  threatLevel?: 'High' | 'Medium' | 'Low';
  timestamp: number;
  userName?: string;
}

// Alert interface for nested alerts (under user IDs)
export interface NestedAlert {
  id?: string;
  audioUrl?: string;
  emotion: string;
  isEmergency?: boolean;
  latitude: number;
  longitude: number;
  reason?: string;
  timestamp: number;
  phone?: string;
  userName?: string;
  status?: string;
  confidence?: number;
  threatLevel?: 'High' | 'Medium' | 'Low';
  locationName?: string;
}

// Combined Alert type that can be either top-level or nested
export type Alert = TopLevelAlert | NestedAlert;

// Type guard to check if alert is top-level
export function isTopLevelAlert(alert: Alert): alert is TopLevelAlert {
  return 'confidence' in alert || 'threatLevel' in alert || 'locationName' in alert;
}

// Type guard to check if alert is nested
export function isNestedAlert(alert: Alert): alert is NestedAlert {
  return 'isEmergency' in alert || 'reason' in alert;
}

export interface DashboardData {
  alerts: Record<string, Alert | Record<string, NestedAlert>>;
  users: Record<string, User>;
}