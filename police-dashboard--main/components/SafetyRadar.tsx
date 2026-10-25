
import React, { useMemo, useEffect, useRef } from 'react';
import { Radar, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, ResponsiveContainer, Tooltip, Legend } from 'recharts';
import { DashboardData } from '../types';
import { AlertTriangle, Shield, Activity, TrendingUp, Zap } from 'lucide-react';

interface SafetyRadarProps {
  data: DashboardData;
}

const SafetyRadar: React.FC<SafetyRadarProps> = ({ data }) => {
  const radarData = useMemo(() => {
    const alerts = Object.values(data.alerts || {});

    // Flatten nested alerts structure
    const allAlerts: any[] = [];
    alerts.forEach((alert: any) => {
      if (alert.emotion) {
        allAlerts.push(alert);
      } else {
        Object.values(alert).forEach((nestedAlert: any) => {
          if (nestedAlert.emotion) {
            allAlerts.push(nestedAlert);
          }
        });
      }
    });

    // Count threat types
    const threatCounts = {
      PANIC: 0,
      MEDICAL: 0,
      DOMESTIC: 0,
      HARASSMENT: 0,
      ROBBERY: 0,
      ANGER: 0,
    };

    allAlerts.forEach((alert: any) => {
      const emotion = alert.emotion?.toUpperCase() || '';
      if (emotion.includes('PANIC') || emotion.includes('ASSAULT')) {
        threatCounts.PANIC++;
      } else if (emotion.includes('MEDICAL') || emotion.includes('HEALTH')) {
        threatCounts.MEDICAL++;
      } else if (emotion.includes('DOMESTIC') || emotion.includes('VIOLENCE')) {
        threatCounts.DOMESTIC++;
      } else if (emotion.includes('HARASSMENT') || emotion.includes('ABUSE')) {
        threatCounts.HARASSMENT++;
      } else if (emotion.includes('ROBBERY') || emotion.includes('THEFT')) {
        threatCounts.ROBBERY++;
      } else if (emotion.includes('ANGER') || emotion.includes('ALTERCATION')) {
        threatCounts.ANGER++;
      }
    });

    // Calculate percentages (max 100)
    const maxCount = Math.max(...Object.values(threatCounts), 1);
    const normalizedData = [
      {
        category: 'Physical Assault',
        current: Math.min((threatCounts.PANIC / maxCount) * 100, 100),
        threshold: 80,
        count: threatCounts.PANIC,
        fullMark: 100
      },
      {
        category: 'Medical Emergency',
        current: Math.min((threatCounts.MEDICAL / maxCount) * 100, 100),
        threshold: 70,
        count: threatCounts.MEDICAL,
        fullMark: 100
      },
      {
        category: 'Domestic Violence',
        current: Math.min((threatCounts.DOMESTIC / maxCount) * 100, 100),
        threshold: 75,
        count: threatCounts.DOMESTIC,
        fullMark: 100
      },
      {
        category: 'Harassment',
        current: Math.min((threatCounts.HARASSMENT / maxCount) * 100, 100),
        threshold: 65,
        count: threatCounts.HARASSMENT,
        fullMark: 100
      },
      {
        category: 'Robbery/Theft',
        current: Math.min((threatCounts.ROBBERY / maxCount) * 100, 100),
        threshold: 85,
        count: threatCounts.ROBBERY,
        fullMark: 100
      },
      {
        category: 'Verbal Conflict',
        current: Math.min((threatCounts.ANGER / maxCount) * 100, 100),
        threshold: 60,
        count: threatCounts.ANGER,
        fullMark: 100
      },
    ];

    return { normalizedData, totalAlerts: allAlerts.length, threatCounts };
  }, [data]);

  const getThreatLevel = () => {
    const highThreats = radarData.normalizedData.filter(d => (d.current as number) > 70).length;
    if (highThreats >= 3) return { level: 'CRITICAL', color: 'text-rose-600', bg: 'bg-rose-50', border: 'border-rose-300', icon: '🔴' };
    if (highThreats >= 1) return { level: 'ELEVATED', color: 'text-amber-600', bg: 'bg-amber-50', border: 'border-amber-300', icon: '🟡' };
    return { level: 'NORMAL', color: 'text-emerald-600', bg: 'bg-emerald-50', border: 'border-emerald-300', icon: '🟢' };
  };

  const threat = getThreatLevel();
  const maxThreat = radarData.normalizedData.reduce((max, item) =>
    (item.count as number) > (max.count as number) ? item : max,
    radarData.normalizedData[0]
  );

  // Custom label component for better positioning
  const CustomLabel = ({ x, y, value, index }: any) => {
    const angle = (index * 60 - 90) * (Math.PI / 180);
    const radius = 140;
    const labelX = x + Math.cos(angle) * radius;
    const labelY = y + Math.sin(angle) * radius;

    return (
      <text
        x={labelX}
        y={labelY}
        fill="#1e293b"
        fontSize="13"
        fontWeight="700"
        textAnchor="middle"
        dominantBaseline="middle"
      >
        {value}
      </text>
    );
  };

  return (
    <div className="h-full flex flex-col">
      {/* Enhanced Threat Level Indicator */}
      <div className="mb-4 p-4 rounded-2xl bg-gradient-to-r from-slate-50 to-slate-100 border-2 border-slate-200">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className={`p-3 rounded-xl ${threat.bg} border-2 ${threat.border} shadow-lg`}>
              <Shield className={`w-6 h-6 ${threat.color}`} />
            </div>
            <div>
              <p className="text-xs text-slate-500 font-bold uppercase tracking-wider">System Status</p>
              <p className={`text-xl font-bold ${threat.color} flex items-center gap-2`}>
                {threat.icon} {threat.level}
              </p>
            </div>
          </div>
          <div className="text-right">
            <p className="text-xs text-slate-500 font-bold uppercase tracking-wider">Total Incidents</p>
            <p className="text-3xl font-bold text-slate-900">{radarData.totalAlerts}</p>
          </div>
        </div>

        {/* Top Threat Indicator */}
        {maxThreat.count > 0 && (
          <div className="mt-3 pt-3 border-t border-slate-200 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <TrendingUp className="w-4 h-4 text-rose-500" />
              <span className="text-xs font-bold text-slate-600">Highest Threat:</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-sm font-bold text-slate-900">{maxThreat.category}</span>
              <span className="bg-rose-100 text-rose-700 px-2 py-1 rounded-lg text-xs font-bold">
                {maxThreat.count} incidents
              </span>
            </div>
          </div>
        )}
      </div>

      {/* Enhanced Radar Chart with Fixed Layout */}
      <div className="flex-1 bg-gradient-to-br from-slate-50 to-white rounded-2xl p-6 border border-slate-200 min-h-[400px]">
        <ResponsiveContainer width="100%" height="100%">
          <RadarChart
            cx="50%"
            cy="50%"
            outerRadius="60%"
            data={radarData.normalizedData}
            margin={{ top: 40, right: 40, bottom: 40, left: 40 }}
          >
            <PolarGrid
              stroke="#cbd5e1"
              strokeWidth={2}
              gridType="polygon"
            />
            <PolarAngleAxis
              dataKey="category"
              tick={{
                fill: '#1e293b',
                fontSize: 13,
                fontWeight: 700
              }}
              tickLine={false}
            />
            <PolarRadiusAxis
              angle={90}
              domain={[0, 100]}
              tick={{ fill: '#64748b', fontSize: 11, fontWeight: 600 }}
              tickCount={5}
              axisLine={false}
            />
            <Radar
              name="Current Threat"
              dataKey="current"
              stroke="#ef4444"
              fill="#ef4444"
              fillOpacity={0.6}
              strokeWidth={3}
              dot={{ fill: '#dc2626', r: 6, strokeWidth: 2, stroke: '#fff' }}
            />
            <Radar
              name="Alert Threshold"
              dataKey="threshold"
              stroke="#94a3b8"
              fill="#94a3b8"
              fillOpacity={0.15}
              strokeWidth={2}
              strokeDasharray="5 5"
            />
            <Tooltip
              contentStyle={{
                backgroundColor: '#1e293b',
                borderRadius: '12px',
                border: 'none',
                boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
                color: '#fff',
                fontSize: '13px',
                fontWeight: 700,
                padding: '12px'
              }}
              labelStyle={{ color: '#f1f5f9', fontWeight: 800, marginBottom: '4px' }}
              formatter={(value: any, name: string, props: any) => {
                return [`${value.toFixed(1)}% (${props.payload.count} incidents)`, name];
              }}
            />
            <Legend
              wrapperStyle={{
                fontSize: '12px',
                fontWeight: 700,
                paddingTop: '20px'
              }}
              iconType="circle"
              verticalAlign="bottom"
            />
          </RadarChart>
        </ResponsiveContainer>
      </div>

      {/* Enhanced Threat Breakdown */}
      <div className="grid grid-cols-3 gap-3 mt-4">
        {Object.entries(radarData.threatCounts).map(([key, count]) => {
          const isHighest = (count as number) === (maxThreat.count as number) && (count as number) > 0;
          return (
            <div
              key={key}
              className={`rounded-xl p-3 text-center border-2 transition-all ${isHighest
                ? 'bg-rose-50 border-rose-300 shadow-lg shadow-rose-100'
                : 'bg-slate-50 border-slate-200'
                }`}
            >
              <p className={`text-[10px] font-bold uppercase tracking-wider mb-1 ${isHighest ? 'text-rose-600' : 'text-slate-500'
                }`}>
                {key}
              </p>
              <div className="flex items-center justify-center gap-1">
                <p className={`text-2xl font-bold ${isHighest ? 'text-rose-700' : 'text-slate-900'}`}>
                  {count}
                </p>
                {isHighest && <Zap className="w-4 h-4 text-rose-500" />}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default SafetyRadar;
