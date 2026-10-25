
import React, { useMemo } from 'react';
import { DashboardData, Alert, User } from '../types';
import { AlertCircle, MapPin, ShieldCheck, Activity, Users as UsersIcon, Clock, Phone, Navigation, PlayCircle, ExternalLink } from 'lucide-react';
import SafetyRadar from './SafetyRadar';
import RecentActivity from './RecentActivity';

interface DashboardProps {
  data: DashboardData;
}

const Dashboard: React.FC<DashboardProps> = ({ data }) => {
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

    return allAlerts;
  }, [data.alerts]);

  // Get latest critical alert with improved logic
  const latestCriticalAlert = useMemo(() => {
    const criticalAlerts = processedAlerts
      .filter(a => {
        const isActive = a.status === 'ACTIVE' ||
          a.isEmergency === true ||
          (!a.status && !a.hasOwnProperty('isEmergency'));
        return isActive && a.status !== 'RESOLVED';
      })
      .sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));

    return criticalAlerts[0] || null;
  }, [processedAlerts]);

  const users = Object.values(data.users || {}) as User[];
  const activeAlerts = processedAlerts.filter(a => {
    const isActive = a.status === 'ACTIVE' ||
      a.isEmergency === true ||
      (!a.status && !a.hasOwnProperty('isEmergency'));
    return isActive && a.status !== 'RESOLVED';
  });
  const citizensMonitored = users.filter(u => u.role === 'member' || u.role === 'admin').length;
  const dangerZones = users.filter(u => u.dangerZoneActive).length;
  const totalIncidents = processedAlerts.length;

  const stats = [
    {
      label: 'Active Emergencies',
      value: activeAlerts.length,
      icon: <AlertCircle className="w-5 h-5" />,
      color: 'bg-rose-50 text-rose-600',
      trend: activeAlerts.length > 0 ? 'CRITICAL' : 'All Clear',
      trendColor: activeAlerts.length > 0 ? 'text-rose-500' : 'text-emerald-500'
    },
    {
      label: 'Citizens Monitored',
      value: citizensMonitored,
      icon: <UsersIcon className="w-5 h-5" />,
      color: 'bg-indigo-50 text-indigo-600',
      trend: 'Protected',
      trendColor: 'text-indigo-500'
    },
    {
      label: 'Danger Zones Active',
      value: dangerZones,
      icon: <MapPin className="w-5 h-5" />,
      color: 'bg-amber-50 text-amber-600',
      trend: dangerZones > 0 ? 'Monitoring' : 'None',
      trendColor: dangerZones > 0 ? 'text-amber-500' : 'text-slate-400'
    },
    {
      label: 'Total Incidents (24h)',
      value: totalIncidents,
      icon: <Clock className="w-5 h-5" />,
      color: 'bg-slate-50 text-slate-600',
      trend: 'Logged',
      trendColor: 'text-slate-400'
    },
  ];

  return (
    <div className="space-y-8 max-w-7xl mx-auto">
      {/* Latest Critical Alert with OpenStreetMap */}
      {latestCriticalAlert && latestCriticalAlert.latitude !== 0 && latestCriticalAlert.longitude !== 0 && (
        <div className="bg-gradient-to-r from-rose-600 to-rose-700 rounded-3xl overflow-hidden shadow-2xl shadow-rose-200/50">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-0">
            {/* Alert Info */}
            <div className="p-8 text-white flex flex-col justify-between">
              <div>
                <div className="flex items-center gap-3 mb-6">
                  <div className="w-14 h-14 bg-white/20 rounded-2xl flex items-center justify-center backdrop-blur-md border border-white/30 animate-pulse">
                    <AlertCircle className="w-8 h-8" />
                  </div>
                  <div>
                    <h3 className="text-2xl font-bold">🚨 LATEST CRITICAL ALERT</h3>
                    <p className="text-rose-100 text-sm font-medium">Real-time Emergency Response Required</p>
                  </div>
                </div>

                <div className="space-y-4 bg-white/10 backdrop-blur-sm rounded-2xl p-6 border border-white/20">
                  <div>
                    <p className="text-rose-100 text-xs font-bold uppercase tracking-wider mb-1">Victim</p>
                    <p className="text-2xl font-bold">{getVictimName(latestCriticalAlert)}</p>
                  </div>

                  <div>
                    <p className="text-rose-100 text-xs font-bold uppercase tracking-wider mb-1">Threat Type</p>
                    <p className="text-lg font-bold">{latestCriticalAlert.emotion}</p>
                  </div>

                  {latestCriticalAlert.reason && (
                    <div>
                      <p className="text-rose-100 text-xs font-bold uppercase tracking-wider mb-1">Detection Reason</p>
                      <p className="text-sm font-medium">{latestCriticalAlert.reason}</p>
                    </div>
                  )}

                  {latestCriticalAlert.locationName && (
                    <div>
                      <p className="text-rose-100 text-xs font-bold uppercase tracking-wider mb-1">Location</p>
                      <p className="text-sm font-medium">{latestCriticalAlert.locationName}</p>
                    </div>
                  )}

                  <div className="grid grid-cols-2 gap-4 pt-2">
                    {latestCriticalAlert.confidence && (
                      <div>
                        <p className="text-rose-100 text-xs font-bold uppercase tracking-wider mb-1">AI Confidence</p>
                        <p className="text-sm font-medium">{latestCriticalAlert.confidence.toFixed(1)}%</p>
                      </div>
                    )}
                    {latestCriticalAlert.threatLevel && (
                      <div>
                        <p className="text-rose-100 text-xs font-bold uppercase tracking-wider mb-1">Threat Level</p>
                        <p className="text-sm font-medium">{latestCriticalAlert.threatLevel}</p>
                      </div>
                    )}
                  </div>

                  <div className="grid grid-cols-2 gap-4 pt-2 border-t border-white/20">
                    <div>
                      <p className="text-rose-100 text-xs font-bold uppercase tracking-wider mb-1">GPS Coordinates</p>
                      <p className="text-sm font-medium">
                        {latestCriticalAlert.latitude?.toFixed(4)}, {latestCriticalAlert.longitude?.toFixed(4)}
                      </p>
                    </div>
                    <div>
                      <p className="text-rose-100 text-xs font-bold uppercase tracking-wider mb-1">Time</p>
                      <p className="text-sm font-medium">
                        {new Date(latestCriticalAlert.timestamp).toLocaleTimeString()}
                      </p>
                    </div>
                  </div>
                </div>
              </div>

              <div className="flex gap-3 mt-6">
                <button
                  onClick={() => window.open(`https://www.google.com/maps/dir/?api=1&destination=${latestCriticalAlert.latitude},${latestCriticalAlert.longitude}`, '_blank')}
                  className="flex-1 bg-white text-rose-600 py-3 px-4 rounded-xl font-bold flex items-center justify-center gap-2 hover:bg-rose-50 transition-all shadow-lg active:scale-95"
                >
                  <Navigation className="w-4 h-4" /> Navigate Now
                </button>
                {latestCriticalAlert.audioUrl && (
                  <a
                    href={latestCriticalAlert.audioUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex-1 bg-white/20 backdrop-blur-md text-white py-3 px-4 rounded-xl font-bold flex items-center justify-center gap-2 hover:bg-white/30 transition-all border border-white/30"
                  >
                    <PlayCircle className="w-4 h-4" /> Evidence
                  </a>
                )}
              </div>
            </div>

            {/* OpenStreetMap */}
            <div className="relative h-[400px] lg:h-auto bg-slate-200">
              <iframe
                width="100%"
                height="100%"
                frameBorder="0"
                scrolling="no"
                src={`https://www.openstreetmap.org/export/embed.html?bbox=${latestCriticalAlert.longitude - 0.01},${latestCriticalAlert.latitude - 0.01},${latestCriticalAlert.longitude + 0.01},${latestCriticalAlert.latitude + 0.01}&layer=mapnik&marker=${latestCriticalAlert.latitude},${latestCriticalAlert.longitude}`}
                className="absolute inset-0"
              />
              <div className="absolute top-4 right-4 bg-white/95 backdrop-blur-sm px-4 py-2 rounded-xl shadow-lg border border-slate-200">
                <p className="text-xs font-bold text-rose-600 uppercase tracking-wider">LIVE LOCATION</p>
              </div>
              <button
                onClick={() => window.open(`https://www.openstreetmap.org/?mlat=${latestCriticalAlert.latitude}&mlon=${latestCriticalAlert.longitude}#map=16/${latestCriticalAlert.latitude}/${latestCriticalAlert.longitude}`, '_blank')}
                className="absolute bottom-4 left-4 bg-white/95 backdrop-blur-sm px-4 py-2 rounded-xl shadow-lg border border-slate-200 hover:bg-white transition-colors flex items-center gap-2 text-sm font-bold text-slate-900"
              >
                <ExternalLink className="w-4 h-4 text-rose-600" />
                Open in Maps
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {stats.map((stat, i) => (
          <div key={i} className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm hover:shadow-md transition-shadow">
            <div className="flex items-center justify-between mb-4">
              <div className={`p-2 rounded-xl ${stat.color}`}>
                {stat.icon}
              </div>
              <Activity className="w-4 h-4 text-slate-300" />
            </div>
            <h4 className="text-slate-500 text-sm font-medium mb-1">{stat.label}</h4>
            <div className="flex items-baseline gap-2">
              <span className="text-2xl font-bold text-slate-900">{stat.value}</span>
              <span className={`text-[10px] font-bold uppercase tracking-wider ${stat.trendColor}`}>
                {stat.trend}
              </span>
            </div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Threat Assessment Radar */}
        <div className="lg:col-span-2 bg-white rounded-2xl border border-slate-200 p-6 shadow-sm overflow-hidden">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h3 className="text-lg font-bold text-slate-800">Threat Assessment Radar</h3>
              <p className="text-sm text-slate-500">Real-time threat categorization and analysis</p>
            </div>
            <div className="flex items-center gap-2 bg-indigo-50 text-indigo-600 px-3 py-1.5 rounded-lg">
              <ShieldCheck className="w-4 h-4" />
              <span className="text-xs font-bold">LIVE ANALYSIS</span>
            </div>
          </div>
          <div className="h-[500px] w-full">
            <SafetyRadar data={data} />
          </div>
        </div>

        {/* Recent Activity Feed */}
        <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm overflow-hidden">
          <div className="flex items-center justify-between mb-6">
            <h3 className="text-lg font-bold text-slate-800">Recent Events</h3>
            <button className="text-indigo-600 text-xs font-bold hover:underline">View All</button>
          </div>
          <div className="space-y-1 max-h-[500px] overflow-y-auto">
            <RecentActivity data={data} />
          </div>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;