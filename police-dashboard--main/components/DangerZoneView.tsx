
import React, { useMemo, useEffect, useRef } from 'react';
import { MapContainer, TileLayer, Circle, Marker, Popup, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { DashboardData } from '../types';
import { AlertCircle, ShieldAlert, Navigation, Phone, Clock, MapPin } from 'lucide-react';

// Fix Leaflet marker icons
const markerIcon = 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png';
const markerShadow = 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png';

let DefaultIcon = L.icon({
    iconUrl: markerIcon,
    shadowUrl: markerShadow,
    iconSize: [25, 41],
    iconAnchor: [12, 41]
});

L.Marker.prototype.options.icon = DefaultIcon;

interface DangerZoneViewProps {
    data: DashboardData;
}

// Component to dynamically fit map bounds and fix visibility
const MapBoundsCheck = ({ alerts }: { alerts: any[] }) => {
    const map = useMap();
    const lastAlertCount = useRef(0);

    useEffect(() => {
        // Force invalidation on mount and whenever container changes
        const resizeObserver = new ResizeObserver(() => {
            map.invalidateSize();
        });

        const container = map.getContainer();
        if (container) {
            resizeObserver.observe(container);
        }

        // Only auto-fit if the number of alerts has changed
        // This prevents "auto-zoomout" when the user is looking at something
        if (alerts.length > 0 && alerts.length !== lastAlertCount.current) {
            const coords = alerts.map(a => [a.latitude, a.longitude] as [number, number]);
            const bounds = L.latLngBounds(coords);
            map.fitBounds(bounds, { padding: [50, 50], maxZoom: 15 });
            lastAlertCount.current = alerts.length;
        }

        return () => {
            resizeObserver.disconnect();
        };
    }, [alerts, map]);

    return null;
};

const DangerZoneView: React.FC<DangerZoneViewProps> = ({ data }) => {
    // Extract all alerts with valid coordinates
    const activeZones = useMemo(() => {
        const alerts = Object.values(data.alerts || {});
        const allAlerts: any[] = [];

        alerts.forEach((alert: any) => {
            if (alert.emotion) {
                allAlerts.push({ ...alert, isTopLevel: true });
            } else {
                Object.entries(alert).forEach(([key, nestedAlert]: [string, any]) => {
                    if (nestedAlert.emotion) {
                        allAlerts.push({ ...nestedAlert, id: key });
                    }
                });
            }
        });

        // Filter for valid coordinates and sort by threat level/timestamp
        return allAlerts
            .filter(a => a.latitude && a.longitude && a.latitude !== 0 && a.longitude !== 0)
            .sort((a, b) => b.timestamp - a.timestamp);
    }, [data.alerts]);

    const getZoneColor = (emotion: string) => {
        const e = emotion?.toUpperCase() || '';
        if (e.includes('PANIC') || e.includes('ASSAULT')) return '#e11d48'; // Rose
        if (e.includes('MEDICAL')) return '#dc2626'; // Red
        if (e.includes('DOMESTIC')) return '#ea580c'; // Orange
        if (e.includes('HARASSMENT')) return '#d97706'; // Amber
        return '#475569'; // Slate
    };

    const getZoneRadius = (emotion: string) => {
        const e = emotion?.toUpperCase() || '';
        if (e.includes('PANIC') || e.includes('ASSAULT')) return 500; // Larger radius for high threat
        return 300;
    };

    // Default center (Pune/Mumbai region based on data)
    const defaultCenter: [number, number] = [18.5204, 73.8567];

    return (
        <div className="h-[calc(100vh-100px)] flex flex-col gap-6">
            <div className="flex items-center justify-between bg-white p-6 rounded-2xl border border-rose-100 shadow-sm">
                <div>
                    <h2 className="text-2xl font-bold text-slate-900 flex items-center gap-3">
                        <ShieldAlert className="w-8 h-8 text-rose-600" />
                        Active Danger Zones
                    </h2>
                    <p className="text-slate-500">Real-time heatmap of reported incidents and high-risk areas</p>
                </div>
                <div className="flex gap-4">
                    <div className="bg-rose-50 px-4 py-2 rounded-xl border border-rose-100">
                        <p className="text-xs font-bold text-rose-600 uppercase">Active Zones</p>
                        <p className="text-2xl font-bold text-rose-700">{activeZones.length}</p>
                    </div>
                </div>
            </div>

            <div className="flex-1 grid grid-cols-1 lg:grid-cols-4 gap-6 min-h-0">
                {/* Map Section */}
                <div className="lg:col-span-3 bg-slate-100 rounded-3xl overflow-hidden border-2 border-slate-200 shadow-inner relative">
                    <MapContainer
                        center={activeZones[0] ? [activeZones[0].latitude, activeZones[0].longitude] : defaultCenter}
                        zoom={13}
                        style={{ height: '100%', width: '100%' }}
                    >
                        <TileLayer
                            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                        />

                        <MapBoundsCheck alerts={activeZones} />

                        {activeZones.map((zone, idx) => (
                            <React.Fragment key={zone.id || idx}>
                                {/* Danger Zone Circle */}
                                <Circle
                                    center={[zone.latitude, zone.longitude]}
                                    pathOptions={{
                                        fillColor: getZoneColor(zone.emotion),
                                        color: getZoneColor(zone.emotion),
                                        fillOpacity: 0.2,
                                        weight: 1
                                    }}
                                    radius={getZoneRadius(zone.emotion)}
                                />

                                {/* Pulse Animation Effect (simulated with smaller circle) */}
                                <Circle
                                    center={[zone.latitude, zone.longitude]}
                                    pathOptions={{
                                        fillColor: getZoneColor(zone.emotion),
                                        color: 'white',
                                        weight: 2,
                                        fillOpacity: 0.8
                                    }}
                                    radius={30}
                                >
                                    <Popup className="custom-popup">
                                        <div className="p-2 min-w-[200px]">
                                            <h3 className="font-bold text-sm mb-1">{zone.emotion}</h3>
                                            <p className="text-xs text-slate-500 mb-2">
                                                {new Date(zone.timestamp).toLocaleString()}
                                            </p>
                                            {zone.userName && (
                                                <p className="text-xs font-semibold mb-2">Victim: {zone.userName}</p>
                                            )}
                                            <button
                                                onClick={() => window.open(`https://www.google.com/maps/dir/?api=1&destination=${zone.latitude},${zone.longitude}`, '_blank')}
                                                className="w-full bg-slate-900 text-white py-1.5 rounded-lg text-xs font-bold flex items-center justify-center gap-1"
                                            >
                                                <Navigation className="w-3 h-3" /> Navigate
                                            </button>
                                        </div>
                                    </Popup>
                                </Circle>
                            </React.Fragment>
                        ))}
                    </MapContainer>

                    <div className="absolute top-4 right-4 bg-white/90 backdrop-blur-md p-4 rounded-xl shadow-lg z-[1000] border border-slate-200 max-w-xs">
                        <h4 className="text-xs font-bold uppercase text-slate-500 mb-2">Zone Legend</h4>
                        <div className="space-y-2">
                            <div className="flex items-center gap-2">
                                <div className="w-3 h-3 rounded-full bg-rose-500"></div>
                                <span className="text-xs font-medium">High Threat (Panic/Assault)</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <div className="w-3 h-3 rounded-full bg-red-600"></div>
                                <span className="text-xs font-medium">Medical Emergency</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <div className="w-3 h-3 rounded-full bg-orange-600"></div>
                                <span className="text-xs font-medium">Domestic Violence</span>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Zone List Sidebar */}
                <div className="bg-white rounded-3xl border border-slate-200 shadow-sm flex flex-col overflow-hidden max-h-[calc(100vh-250px)]">
                    <div className="p-4 border-b border-slate-100 bg-slate-50">
                        <h3 className="font-bold text-slate-800">Reported Incidents</h3>
                    </div>
                    <div className="overflow-y-auto flex-1 p-2 space-y-2">
                        {activeZones.map((zone, idx) => (
                            <div
                                key={zone.id || idx}
                                className="p-3 rounded-xl border border-slate-100 hover:border-slate-300 hover:bg-slate-50 transition-all cursor-pointer group"
                                onClick={() => window.open(`https://www.google.com/maps?q=${zone.latitude},${zone.longitude}`, '_blank')}
                            >
                                <div className="flex items-start gap-3">
                                    <div className="mt-1">
                                        <AlertCircle className="w-5 h-5" style={{ color: getZoneColor(zone.emotion) }} />
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <p className="text-sm font-bold text-slate-900 truncate">{zone.emotion}</p>
                                        <div className="flex items-center gap-1 text-xs text-slate-500 mt-1">
                                            <Clock className="w-3 h-3" />
                                            <span>{new Date(zone.timestamp).toLocaleTimeString()}</span>
                                        </div>
                                        {zone.locationName && (
                                            <div className="flex items-center gap-1 text-xs text-slate-500 mt-1 truncate">
                                                <MapPin className="w-3 h-3" />
                                                <span>{zone.locationName}</span>
                                            </div>
                                        )}
                                    </div>
                                </div>
                            </div>
                        ))}
                        {activeZones.length === 0 && (
                            <div className="p-8 text-center text-slate-400">
                                <p>No active danger zones reported.</p>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default DangerZoneView;
