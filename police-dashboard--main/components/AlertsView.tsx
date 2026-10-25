
import React, { useMemo } from 'react';
import { DashboardData } from '../types';
import { AlertTriangle, MapPin, Phone, ShieldCheck, Clock, Navigation, ExternalLink, PlayCircle } from 'lucide-react';

interface AlertsViewProps {
  data: DashboardData;
}

const AlertsView: React.FC<AlertsViewProps> = ({ data }) => {
  // Helper function to get victim name
  const getVictimName = (alert: any): string => {
    if (alert.userName) return alert.userName;

    // Try to match by phone or other data
    const users = Object.values(data.users || {});
    if (alert.phone) {
      const matchedUser = users.find((u: any) => u.phone === alert.phone);
      if (matchedUser && (matchedUser as any).name) return (matchedUser as any).name;
    }

    return 'Citizen in Distress';
  };

  // Process alerts - handle both flat and nested structure
  const processedAlerts = useMemo(() => {
    const alerts = Object.values(data.alerts || {});
    const allAlerts: any[] = [];

    alerts.forEach((alert: any) => {
      if (alert.emotion) {
        allAlerts.push({ ...alert, isTopLevel: true });
      } else {
        Object.entries(alert).forEach(([key, nestedAlert]: [string, any]) => {
          if (nestedAlert.emotion) {
            allAlerts.push({
              ...nestedAlert,
              id: key,
              isNested: true
            });
          }
        });
      }
    });

    // Sort by timestamp (newest first)
    return allAlerts.sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));
  }, [data.alerts]);

  // Filter active alerts with improved logic
  const activeAlerts = processedAlerts.filter(a => {
    const isActive = a.status === 'ACTIVE' ||
      a.isEmergency === true ||
      (!a.status && !a.hasOwnProperty('isEmergency'));
    return isActive && a.status !== 'RESOLVED';
  });

  const getThreatColor = (emotion: string) => {
    const emotionUpper = emotion?.toUpperCase() || '';
    if (emotionUpper.includes('PANIC') || emotionUpper.includes('ASSAULT') || emotionUpper.includes('ROBBERY')) {
      return 'rose';
    } else if (emotionUpper.includes('MEDICAL') || emotionUpper.includes('HEALTH')) {
      return 'red';
    } else if (emotionUpper.includes('DOMESTIC') || emotionUpper.includes('VIOLENCE')) {
      return 'orange';
    } else if (emotionUpper.includes('HARASSMENT')) {
      return 'amber';
    }
    return 'slate';
  };

  return (
    <div className="max-w-6xl mx-auto space-y-8">
      <div className="flex items-center justify-between bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
        <div>
          <h2 className="text-2xl font-bold text-slate-900">Emergency Operations Center</h2>
          <p className="text-slate-500">Real-time threat monitoring and rapid response command</p>
        </div>
        <div className={`px-4 py-2 rounded-xl flex items-center gap-2 font-bold text-sm ${activeAlerts.length > 0
          ? 'bg-rose-100 text-rose-700 animate-pulse'
          : 'bg-emerald-100 text-emerald-700'
          }`}>
          <ShieldCheck className="w-4 h-4" />
          {activeAlerts.length > 0 ? `${activeAlerts.length} Active Emergency` : 'All Clear'}
        </div>
      </div>

      {processedAlerts.length > 0 ? (
        <div className="space-y-6">
          {processedAlerts.map((alert, index) => {
            const isActive = alert.isEmergency || alert.status === 'ACTIVE';
            const hasLocation = alert.latitude && alert.longitude && alert.latitude !== 0 && alert.longitude !== 0;
            const threatColor = getThreatColor(alert.emotion);
            const victimName = getVictimName(alert);

            return (
              <div
                key={alert.id || `alert-${index}`}
                className={`bg-white rounded-3xl overflow-hidden border-2 shadow-xl transition-all ${isActive
                  ? `border-${threatColor}-500 shadow-${threatColor}-200/50`
                  : 'border-slate-200 opacity-75'
                  }`}
              >
                <div className={`p-6 flex items-center justify-between text-white ${isActive ? `bg-${threatColor}-500` : 'bg-slate-400'
                  }`}>
                  <div className="flex items-center gap-4">
                    <div className="w-12 h-12 bg-white/20 rounded-2xl flex items-center justify-center backdrop-blur-md border border-white/30">
                      <AlertTriangle className="w-8 h-8" />
                    </div>
                    <div>
                      <h4 className="text-xl font-bold leading-none">
                        {isActive ? '🚨 EMERGENCY ALERT' : '✓ RESOLVED'}
                      </h4>
                      <p className="text-sm text-white/90 font-semibold mt-1">
                        Victim: {victimName}
                      </p>
                      <p className="text-xs text-white/80 font-medium uppercase tracking-widest">
                        {alert.emotion}
                      </p>
                    </div>
                  </div>
                  <div className="bg-white/10 px-4 py-2 rounded-xl backdrop-blur-md border border-white/20 text-center">
                    <p className="text-[10px] font-bold uppercase">Status</p>
                    <p className="text-sm font-bold">{isActive ? 'ACTIVE' : 'RESOLVED'}</p>
                  </div>
                </div>

                <div className={`p-8 grid grid-cols-1 ${hasLocation ? 'lg:grid-cols-2' : ''} gap-8`}>
                  <div className="space-y-6">
                    {alert.reason && (
                      <div className="p-4 bg-slate-50 rounded-2xl border border-slate-100">
                        <p className="text-[10px] font-bold text-slate-400 uppercase mb-1">Detection Reason</p>
                        <p className="text-sm font-bold text-slate-700">{alert.reason}</p>
                      </div>
                    )}

                    {alert.locationName && (
                      <div className="p-4 bg-indigo-50 rounded-2xl border border-indigo-100">
                        <p className="text-[10px] font-bold text-indigo-400 uppercase mb-1">Location Name</p>
                        <p className="text-sm font-bold text-indigo-700">{alert.locationName}</p>
                      </div>
                    )}

                    <div className="grid grid-cols-2 gap-4">
                      {alert.confidence && (
                        <div className="p-3 bg-emerald-50 rounded-xl border border-emerald-100">
                          <p className="text-[10px] font-bold text-emerald-400 uppercase mb-1">AI Confidence</p>
                          <p className="text-lg font-bold text-emerald-700">{alert.confidence.toFixed(1)}%</p>
                        </div>
                      )}
                      {alert.threatLevel && (
                        <div className={`p-3 rounded-xl border ${alert.threatLevel === 'High' ? 'bg-rose-50 border-rose-100' :
                          alert.threatLevel === 'Medium' ? 'bg-amber-50 border-amber-100' :
                            'bg-slate-50 border-slate-100'
                          }`}>
                          <p className={`text-[10px] font-bold uppercase mb-1 ${alert.threatLevel === 'High' ? 'text-rose-400' :
                            alert.threatLevel === 'Medium' ? 'text-amber-400' :
                              'text-slate-400'
                            }`}>Threat Level</p>
                          <p className={`text-lg font-bold ${alert.threatLevel === 'High' ? 'text-rose-700' :
                            alert.threatLevel === 'Medium' ? 'text-amber-700' :
                              'text-slate-700'
                            }`}>{alert.threatLevel}</p>
                        </div>
                      )}
                    </div>

                    <div className="space-y-4">
                      {hasLocation && (
                        <div className="flex items-start gap-3">
                          <MapPin className="w-5 h-5 text-slate-400 mt-1" />
                          <div>
                            <p className="text-sm font-bold text-slate-800">
                              GPS: {alert.latitude?.toFixed(4) || '0.0000'}, {alert.longitude?.toFixed(4) || '0.0000'}
                            </p>
                            <p className="text-xs text-slate-500">Click map to view full location</p>
                          </div>
                        </div>
                      )}

                      {alert.phone && (
                        <div className="flex items-center gap-3">
                          <Phone className="w-5 h-5 text-slate-400" />
                          <a href={`tel:${alert.phone}`} className="text-sm font-bold text-indigo-600 hover:underline">
                            {alert.phone}
                          </a>
                        </div>
                      )}

                      <div className="flex items-center gap-3">
                        <Clock className="w-5 h-5 text-slate-400" />
                        <p className="text-sm font-bold text-slate-800">
                          {new Date(alert.timestamp).toLocaleString()}
                        </p>
                      </div>
                    </div>

                    {hasLocation && (
                      <div className="flex gap-4 pt-4">
                        <button
                          onClick={() => window.open(`https://www.google.com/maps/dir/?api=1&destination=${alert.latitude},${alert.longitude}`, '_blank')}
                          className="flex-1 bg-slate-900 text-white py-3 rounded-2xl font-bold flex items-center justify-center gap-2 hover:bg-slate-800 shadow-lg transition-all active:scale-95"
                        >
                          <Navigation className="w-4 h-4" /> Get Directions
                        </button>
                        <button
                          onClick={() => window.open(`https://www.openstreetmap.org/?mlat=${alert.latitude}&mlon=${alert.longitude}#map=16/${alert.latitude}/${alert.longitude}`, '_blank')}
                          className={`flex-1 py-3 rounded-2xl font-bold flex items-center justify-center gap-2 shadow-lg transition-all active:scale-95 ${isActive
                            ? `bg-${threatColor}-600 text-white hover:bg-${threatColor}-700`
                            : 'bg-slate-200 text-slate-600 hover:bg-slate-300'
                            }`}
                        >
                          <MapPin className="w-4 h-4" /> View on Map
                        </button>
                      </div>
                    )}

                    {alert.audioUrl && (
                      <a
                        href={alert.audioUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center justify-center gap-2 bg-indigo-600 text-white py-3 rounded-2xl font-bold hover:bg-indigo-700 transition-all shadow-lg"
                      >
                        <PlayCircle className="w-4 h-4" /> Play Audio Evidence
                      </a>
                    )}
                  </div>

                  {hasLocation && (
                    <div className="relative group min-h-[300px]">
                      <div className="absolute inset-0 rounded-3xl overflow-hidden border border-slate-200 shadow-inner bg-slate-100">
                        <iframe
                          width="100%"
                          height="100%"
                          frameBorder="0"
                          scrolling="no"
                          src={`https://www.openstreetmap.org/export/embed.html?bbox=${alert.longitude - 0.01},${alert.latitude - 0.01},${alert.longitude + 0.01},${alert.latitude + 0.01}&layer=mapnik&marker=${alert.latitude},${alert.longitude}`}
                        />
                      </div>
                      <div className="absolute top-4 left-4 bg-white/95 backdrop-blur-sm px-4 py-2 rounded-xl shadow-lg border border-slate-200">
                        <p className="text-xs font-bold uppercase tracking-widest text-rose-600">LIVE LOCATION</p>
                        <p className="text-[10px] text-slate-600 font-semibold mt-0.5">{victimName}</p>
                      </div>
                      <button
                        onClick={() => window.open(`https://www.openstreetmap.org/?mlat=${alert.latitude}&mlon=${alert.longitude}#map=16/${alert.latitude}/${alert.longitude}`, '_blank')}
                        className="absolute top-4 right-4 p-3 bg-white/95 backdrop-blur-md rounded-xl shadow-lg text-slate-800 hover:bg-white transition-colors"
                      >
                        <ExternalLink className="w-5 h-5" />
                      </button>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="bg-white rounded-3xl border border-slate-200 p-20 flex flex-col items-center justify-center text-center">
          <div className="w-24 h-24 bg-emerald-50 text-emerald-500 rounded-full flex items-center justify-center mb-6">
            <ShieldCheck className="w-12 h-12" />
          </div>
          <h3 className="text-2xl font-bold text-slate-800">No Emergency Alerts</h3>
          <p className="text-slate-500 mt-2 max-w-sm">
            All monitored citizens are safe. The system is actively monitoring for any emergency signals.
          </p>
        </div>
      )}

      {/* Emergency Services Info */}
      <div className="bg-slate-900 text-white p-8 rounded-3xl shadow-xl overflow-hidden relative">
        <div className="relative z-10">
          <h4 className="text-xl font-bold mb-6 flex items-center gap-2">
            <ShieldCheck className="w-6 h-6 text-indigo-400" />
            Emergency Response Network
          </h4>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="bg-white/10 backdrop-blur-md border border-white/10 p-5 rounded-2xl">
              <p className="text-indigo-400 text-xs font-bold uppercase tracking-widest mb-1">Police Command</p>
              <p className="text-lg font-bold">Sentinel Central Command</p>
              <p className="text-sm text-white/60 mb-4 italic">24/7 Emergency Response</p>
              <button className="w-full bg-white text-slate-900 py-2.5 rounded-xl text-sm font-bold hover:bg-slate-100 transition-colors">
                Direct Line: 100
              </button>
            </div>
            <div className="bg-white/10 backdrop-blur-md border border-white/10 p-5 rounded-2xl">
              <p className="text-rose-400 text-xs font-bold uppercase tracking-widest mb-1">Medical Response</p>
              <p className="text-lg font-bold">Emergency Medical Services</p>
              <p className="text-sm text-white/60 mb-4 italic">Ambulance Dispatch Ready</p>
              <button className="w-full bg-white text-slate-900 py-2.5 rounded-xl text-sm font-bold hover:bg-slate-100 transition-colors">
                Direct Line: 108
              </button>
            </div>
          </div>
        </div>
        <div className="absolute top-0 right-0 w-64 h-64 bg-indigo-500/20 rounded-full blur-3xl -mr-20 -mt-20"></div>
      </div>
    </div>
  );
};

export default AlertsView;