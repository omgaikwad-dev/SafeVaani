
import React, { useState } from 'react';
import { DashboardData, User, HistoryItem, Contact } from '../types';
import { Battery, BatteryLow, BatteryMedium, BatteryFull, MapPin, Phone, ShieldAlert, ChevronRight, PlayCircle, Activity, Users, AlertTriangle } from 'lucide-react';

interface MembersViewProps {
  data: DashboardData;
}

const MembersView: React.FC<MembersViewProps> = ({ data }) => {
  const users = Object.values(data.users || {}) as User[];
  const citizens = users
    .filter(u => u.role === 'member' || u.role === 'admin' || !u.role)
    .sort((a, b) => {
      const timeA = a.lastLogin || a.lastUpdateTimestamp || 0;
      const timeB = b.lastLogin || b.lastUpdateTimestamp || 0;
      return timeB - timeA;
    });
  const [selectedUser, setSelectedUser] = useState<User | null>(null);

  const getBatteryIcon = (level?: number) => {
    if (!level || level === -1) return <Battery className="w-4 h-4 text-slate-300" />;
    if (level < 20) return <BatteryLow className="w-4 h-4 text-rose-500" />;
    if (level < 70) return <BatteryMedium className="w-4 h-4 text-amber-500" />;
    return <BatteryFull className="w-4 h-4 text-emerald-500" />;
  };

  return (
    <div className="max-w-7xl mx-auto space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* Left: Citizens List */}
        <div className="lg:col-span-4 space-y-4">
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-bold text-slate-800">Monitored Citizens ({citizens.length})</h3>
            <div className="flex items-center gap-2 bg-indigo-50 text-indigo-600 px-2 py-1 rounded-lg">
              <div className="w-1.5 h-1.5 bg-indigo-600 rounded-full animate-pulse"></div>
              <span className="text-xs font-bold">TRACKING</span>
            </div>
          </div>
          {citizens.length === 0 ? (
            <div className="bg-slate-50 border-2 border-dashed border-slate-200 rounded-2xl p-8 text-center">
              <Users className="w-12 h-12 text-slate-300 mx-auto mb-3" />
              <p className="text-slate-400 text-sm">No citizens registered in system</p>
            </div>
          ) : (
            citizens.map((user) => (
              <div
                key={user.id || Math.random()}
                onClick={() => setSelectedUser(user)}
                className={`p-4 rounded-2xl cursor-pointer border transition-all ${selectedUser?.id === user.id
                  ? 'bg-white border-indigo-500 shadow-lg ring-4 ring-indigo-500/5'
                  : 'bg-white border-slate-200 hover:border-slate-300 shadow-sm'
                  }`}
              >
                <div className="flex items-center gap-4">
                  <div className="relative">
                    <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center text-white font-bold text-lg">
                      {(user.name || user.email || 'U')[0].toUpperCase()}
                    </div>
                    {user.dangerZoneActive && (
                      <div className="absolute -top-1 -right-1 w-3 h-3 bg-rose-500 border-2 border-white rounded-full animate-pulse"></div>
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between">
                      <h4 className="font-bold text-slate-800 truncate">{user.name || user.email || 'Unknown Citizen'}</h4>
                      {user.role && (
                        <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded-md uppercase ${user.role === 'admin' ? 'bg-indigo-100 text-indigo-600' : 'bg-slate-100 text-slate-600'}`}>
                          {user.role}
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-3 mt-1 text-slate-500">
                      <div className="flex items-center gap-1">
                        {getBatteryIcon(user.batteryLevel)}
                        <span className="text-[10px] font-medium">{user.batteryLevel && user.batteryLevel !== -1 ? `${user.batteryLevel}%` : 'N/A'}</span>
                      </div>
                      <div className="w-1 h-1 bg-slate-300 rounded-full"></div>
                      <div className="flex items-center gap-1">
                        <MapPin className="w-3 h-3" />
                        <span className="text-[10px] font-medium">GPS Active</span>
                      </div>
                    </div>
                  </div>
                  <ChevronRight className="w-5 h-5 text-slate-300" />
                </div>
              </div>
            ))
          )}
        </div>

        {/* Right: User Detail Panel */}
        <div className="lg:col-span-8">
          {selectedUser ? (
            <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden h-full flex flex-col">
              <div className="p-8 border-b border-slate-100 flex items-start justify-between">
                <div className="flex gap-6">
                  <div className="w-20 h-20 rounded-3xl bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center text-white font-bold text-3xl ring-4 ring-slate-50 shadow-md">
                    {(selectedUser.name || selectedUser.email || 'U')[0].toUpperCase()}
                  </div>
                  <div>
                    <h2 className="text-2xl font-bold text-slate-900">{selectedUser.name || 'Unknown Citizen'}</h2>
                    <p className="text-slate-500 font-medium mb-3">{selectedUser.email || 'No email on file'}</p>
                    <div className="flex gap-2">
                      {selectedUser.phone && (
                        <a
                          href={`tel:${selectedUser.phone}`}
                          className="flex items-center gap-2 bg-slate-900 text-white px-4 py-1.5 rounded-lg text-xs font-bold hover:bg-slate-800 transition-colors"
                        >
                          <Phone className="w-3.5 h-3.5" /> Call {selectedUser.phone}
                        </a>
                      )}
                      {selectedUser.dangerZoneActive && (
                        <div className="flex items-center gap-2 bg-rose-100 text-rose-600 px-4 py-1.5 rounded-lg text-xs font-bold">
                          <AlertTriangle className="w-3.5 h-3.5" /> Danger Zone Active
                        </div>
                      )}
                    </div>
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-[10px] text-slate-400 uppercase tracking-widest font-bold mb-1">Unique ID</p>
                  <p className="text-lg font-mono font-bold text-indigo-600 bg-indigo-50 px-3 py-1 rounded-lg">{selectedUser.uniqueId || 'N/A'}</p>
                </div>
              </div>

              <div className="flex-1 grid grid-cols-1 md:grid-cols-2 divide-x divide-slate-100 overflow-hidden">
                {/* Incident History */}
                <div className="p-6 overflow-y-auto max-h-[500px]">
                  <h5 className="font-bold text-slate-800 mb-4 flex items-center justify-between">
                    <span>Incident History</span>
                    <span className="text-[10px] bg-indigo-50 text-indigo-600 px-2 py-0.5 rounded-full">
                      {selectedUser.history ? Object.keys(selectedUser.history).length : 0} Records
                    </span>
                  </h5>
                  <div className="space-y-4">
                    {selectedUser.history ? (
                      Object.entries(selectedUser.history).map(([key, h]) => {
                        const historyItem = h as HistoryItem;
                        return (
                          <div key={key} className="p-4 bg-slate-50 rounded-xl border border-slate-100 group hover:border-indigo-200 transition-all">
                            <div className="flex items-center justify-between mb-2">
                              <span className="text-xs font-bold text-slate-700">{historyItem.emotion}</span>
                              <span className="text-[10px] text-slate-400">{new Date(historyItem.timestamp).toLocaleString()}</span>
                            </div>
                            <p className="text-xs text-slate-500 mb-3">{historyItem.status}</p>
                            <div className="flex items-center gap-3">
                              <button
                                onClick={() => window.open(`https://www.google.com/maps?q=${historyItem.latitude},${historyItem.longitude}`, '_blank')}
                                className="flex items-center gap-1.5 bg-white px-2.5 py-1 rounded-md border border-slate-200 hover:border-indigo-300 transition-colors"
                              >
                                <MapPin className="w-3 h-3 text-indigo-500" />
                                <span className="text-[10px] font-bold text-slate-600">View Location</span>
                              </button>
                              {historyItem.audioUrl && (
                                <a
                                  href={historyItem.audioUrl}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="flex items-center gap-1.5 bg-indigo-600 text-white px-2.5 py-1 rounded-md hover:bg-indigo-700 transition-colors"
                                >
                                  <PlayCircle className="w-3 h-3" />
                                  <span className="text-[10px] font-bold">Play Evidence</span>
                                </a>
                              )}
                            </div>
                          </div>
                        );
                      })
                    ) : (
                      <div className="py-20 text-center">
                        <Activity className="w-12 h-12 text-slate-200 mx-auto mb-3" />
                        <p className="text-slate-400 text-sm">No incidents recorded</p>
                      </div>
                    )}
                  </div>
                </div>

                {/* Contacts & Location */}
                <div className="p-6 bg-slate-50/50">
                  <h5 className="font-bold text-slate-800 mb-4">Emergency Contacts</h5>
                  <div className="space-y-3">
                    {selectedUser.contacts ? (
                      Object.values(selectedUser.contacts).map((c, i) => {
                        const contact = c as Contact;
                        return (
                          <div key={i} className="bg-white p-4 rounded-xl border border-slate-200 flex items-center justify-between">
                            <div>
                              <p className="text-sm font-bold text-slate-800">{contact.name}</p>
                              <p className="text-xs text-slate-500 capitalize">{contact.relationship}</p>
                            </div>
                            <div className="text-right">
                              <a href={`tel:${contact.phone}`} className="text-sm font-bold text-indigo-600 hover:underline">{contact.phone}</a>
                            </div>
                          </div>
                        );
                      })
                    ) : (
                      <p className="text-slate-400 text-sm">No emergency contacts registered</p>
                    )}
                  </div>

                  <div className="mt-8">
                    <h5 className="font-bold text-slate-800 mb-3">Last Known Location</h5>
                    <div className="bg-slate-200 h-40 rounded-xl flex items-center justify-center relative overflow-hidden shadow-inner">
                      <div className="absolute inset-0 opacity-40 bg-[url('https://picsum.photos/400/200?grayscale&blur=5')] bg-cover"></div>
                      <button
                        onClick={() => window.open(`https://www.google.com/maps?q=${selectedUser.lastLatitude || 0},${selectedUser.lastLongitude || 0}`, '_blank')}
                        className="relative z-1 w-10 h-10 bg-indigo-600 text-white rounded-full flex items-center justify-center shadow-2xl hover:scale-110 transition-transform cursor-pointer"
                      >
                        <MapPin className="w-6 h-6" />
                      </button>
                      <div className="absolute bottom-2 left-2 bg-white/90 backdrop-blur-sm px-3 py-1.5 rounded-lg border border-slate-200 shadow-sm">
                        <p className="text-[9px] font-bold text-slate-700">
                          LAT: {selectedUser.lastLatitude?.toFixed(4) || '0.0000'},
                          LNG: {selectedUser.lastLongitude?.toFixed(4) || '0.0000'}
                        </p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <div className="h-[600px] bg-slate-100 border-2 border-dashed border-slate-300 rounded-2xl flex flex-col items-center justify-center text-slate-400 gap-4">
              <Users className="w-16 h-16 opacity-20" />
              <p className="font-medium">Select a citizen to view detailed records</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default MembersView;