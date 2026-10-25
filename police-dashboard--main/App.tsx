
import React, { useState, useEffect, useRef } from 'react';
import { LayoutDashboard, Bell, Users, ShieldAlert, LogOut, Loader2, AlertCircle } from 'lucide-react';
import { onAuthStateChanged, signOut, User as FirebaseUser } from 'firebase/auth';
import { ref, onValue, off } from 'firebase/database';
import { auth, database } from './firebase';
import { ToastContainer, toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import Login from './components/Login';
import Dashboard from './components/Dashboard';
import MembersView from './components/MembersView';
import AlertsView from './components/AlertsView';
import DangerZoneView from './components/DangerZoneView';
import { DashboardData } from './types';

// Emergency alert sound
const ALERT_SOUND_URL = 'https://assets.mixkit.co/active_storage/sfx/2869/2869-preview.mp3';

const App: React.FC = () => {
  const [user, setUser] = useState<FirebaseUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'dashboard' | 'members' | 'alerts' | 'danger'>('dashboard');
  const [dashboardData, setDashboardData] = useState<DashboardData>({ alerts: {}, users: {} });
  const [dataLoading, setDataLoading] = useState(true);
  const previousAlertCount = useRef<number>(0);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {
    // Initialize audio
    audioRef.current = new Audio(ALERT_SOUND_URL);
    const unsubscribe = onAuthStateChanged(auth, (currentUser) => {
      setUser(currentUser);
      setLoading(false);
    });
    return () => unsubscribe();
  }, []);

  // Helper function to get victim name from users or alert
  const getVictimName = (alert: any, users: any): string => {
    // Try to find user by matching alert data
    const userEntries = Object.entries(users || {});

    // If alert has userName field
    if (alert.userName) return alert.userName;

    // Try to match by phone number
    if (alert.phone) {
      const matchedUser = userEntries.find(([_, user]: [string, any]) => user.phone === alert.phone);
      if (matchedUser) return (matchedUser[1] as any).name || 'Unknown Citizen';
    }

    // Try to match by location proximity (if both have coordinates)
    if (alert.latitude && alert.longitude) {
      const matchedUser = userEntries.find(([_, user]: [string, any]) => {
        const u = user as any;
        if (!u.lastLatitude || !u.lastLongitude) return false;
        const distance = Math.sqrt(
          Math.pow(u.lastLatitude - alert.latitude, 2) +
          Math.pow(u.lastLongitude - alert.longitude, 2)
        );
        return distance < 0.01; // Very close proximity
      });
      if (matchedUser) return (matchedUser[1] as any).name || 'Unknown Citizen';
    }

    return 'Citizen in Distress';
  };

  // Real-time Firebase listeners with notification
  useEffect(() => {
    if (!user) return;

    console.log('🔥 Firebase listeners starting...');
    const alertsRef = ref(database, 'alerts');
    const usersRef = ref(database, 'users');

    const handleAlertsUpdate = (snapshot: any) => {
      const alerts = snapshot.val() || {};

      // Count all alerts (including nested)
      let totalAlerts = 0;
      let latestAlert: any = null;
      let latestTimestamp = 0;

      const processAlert = (startAlert: any) => {
        if (startAlert.emotion) {
          totalAlerts++;
          if (startAlert.timestamp > latestTimestamp) {
            latestTimestamp = startAlert.timestamp;
            latestAlert = startAlert;
          }
        }
      };

      Object.values(alerts).forEach((alert: any) => {
        if (alert.emotion) {
          processAlert(alert);
        } else {
          Object.values(alert).forEach((nestedAlert: any) => {
            processAlert(nestedAlert);
          });
        }
      });

      // Show notification and play sound for new alert
      if (previousAlertCount.current > 0 && totalAlerts > previousAlertCount.current) {
        // Play sound
        if (audioRef.current) {
          audioRef.current.play().catch(e => console.error("Error playing sound:", e));
        }

        const victimName = getVictimName(latestAlert, dashboardData.users);
        toast.error(
          <div className="flex items-start gap-3">
            <AlertCircle className="w-6 h-6 text-rose-500 flex-shrink-0 mt-0.5" />
            <div>
              <p className="font-bold text-sm">🚨 NEW EMERGENCY ALERT</p>
              <p className="text-xs mt-1"><strong>Victim:</strong> {victimName}</p>
              <p className="text-xs"><strong>Type:</strong> {latestAlert?.emotion || 'Unknown'}</p>
              <p className="text-xs text-slate-500 mt-1">
                {new Date(latestTimestamp).toLocaleTimeString()}
              </p>
            </div>
          </div>,
          {
            position: "top-right",
            autoClose: 8000,
            hideProgressBar: false,
            closeOnClick: true,
            pauseOnHover: true,
            draggable: true,
            className: 'bg-white border-2 border-rose-500 shadow-2xl',
            progressClassName: 'bg-rose-500',
          }
        );
      }

      previousAlertCount.current = totalAlerts;
      setDashboardData(prev => ({ ...prev, alerts }));
      setDataLoading(false);
    };

    const handleUsersUpdate = (snapshot: any) => {
      const users = snapshot.val() || {};
      setDashboardData(prev => ({ ...prev, users }));
    };

    onValue(alertsRef, handleAlertsUpdate);
    onValue(usersRef, handleUsersUpdate);

    return () => {
      off(alertsRef, 'value', handleAlertsUpdate);
      off(usersRef, 'value', handleUsersUpdate);
    };
  }, [user, dashboardData.users]);


  const handleLogout = async () => {
    try {
      await signOut(auth);
    } catch (error) {
      console.error('Error logging out:', error);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <Loader2 className="w-8 h-8 text-indigo-600 animate-spin" />
      </div>
    );
  }

  if (!user) {
    return <Login />;
  }

  // Calculate active alerts count for badge
  const getActiveAlertCount = () => {
    let count = 0;
    const alerts = Object.values(dashboardData.alerts || {});
    alerts.forEach((alert: any) => {
      // Check if this is a top-level alert
      if (alert.emotion) {
        // Count as active if:
        // 1. Explicitly marked as ACTIVE status, OR
        // 2. Has isEmergency flag set to true, OR
        // 3. No status field (treat as active by default unless resolved)
        const isActive = alert.status === 'ACTIVE' ||
          alert.isEmergency === true ||
          (!alert.status && !alert.hasOwnProperty('isEmergency'));
        if (isActive && alert.status !== 'RESOLVED') count++;
      } else {
        // This is a nested structure (user ID -> alerts)
        Object.values(alert).forEach((nested: any) => {
          if (nested.emotion) {
            const isActive = nested.status === 'ACTIVE' ||
              nested.isEmergency === true ||
              (!nested.status && !nested.hasOwnProperty('isEmergency'));
            if (isActive && nested.status !== 'RESOLVED') count++;
          }
        });
      }
    });
    return count;
  };

  const activeAlertCount = getActiveAlertCount();

  const tabs = [
    { id: 'dashboard', label: 'Command Center', icon: <LayoutDashboard className="w-5 h-5" /> },
    { id: 'alerts', label: 'Active Alerts', icon: <Bell className="w-5 h-5" />, badge: activeAlertCount },
    { id: 'danger', label: 'Danger Zones', icon: <ShieldAlert className="w-5 h-5" /> },
    { id: 'members', label: 'Citizen Records', icon: <Users className="w-5 h-5" /> },
  ];

  return (
    <div className="min-h-screen bg-slate-50 flex">
      {/* Sidebar */}
      <div className="w-64 bg-white border-r border-slate-200 flex flex-col fixed h-full z-20 hidden lg:flex">
        <div className="p-6 border-b border-slate-100 flex items-center gap-3">
          <div className="bg-indigo-600 p-2 rounded-xl text-white shadow-lg shadow-indigo-200">
            <ShieldAlert className="w-6 h-6" />
          </div>
          <div>
            <h1 className="font-bold text-slate-800 text-lg leading-none">Sentinel</h1>
            <span className="text-xs text-slate-500 font-medium">Police Command</span>
          </div>
        </div>

        <nav className="flex-1 px-4 py-4 space-y-1">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
              className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg transition-all ${activeTab === tab.id
                ? 'bg-indigo-50 text-indigo-700 shadow-sm font-semibold'
                : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
                }`}
            >
              <div className="relative">
                {tab.icon}
                {tab.badge !== undefined && tab.badge > 0 && (
                  <span className="absolute -top-2 -right-2 bg-rose-500 text-white text-[10px] font-bold h-4 w-4 flex items-center justify-center rounded-full animate-pulse shadow-sm border border-white">
                    {tab.badge}
                  </span>
                )}
              </div>
              <span className="flex-1 text-left">{tab.label}</span>
            </button>
          ))}
        </nav>

        <div className="p-4 border-t border-slate-100">
          <button
            onClick={handleLogout}
            className="w-full flex items-center gap-3 px-4 py-3 text-rose-600 hover:bg-rose-50 rounded-lg transition-colors font-medium text-sm"
          >
            <LogOut className="w-5 h-5" />
            Sign Out
          </button>
        </div>
      </div>

      {/* Main Content */}
      <main className="flex-1 lg:ml-64 p-8 overflow-y-auto h-screen">
        <div className="max-w-7xl mx-auto pb-20 lg:pb-0">
          {activeTab === 'dashboard' && <Dashboard data={dashboardData} />}
          {activeTab === 'alerts' && <AlertsView data={dashboardData} />}
          {activeTab === 'members' && <MembersView data={dashboardData} />}
          {activeTab === 'danger' && <DangerZoneView data={dashboardData} />}
        </div>
      </main>

      {/* Mobile Bottom Nav (visible only on small screens) */}
      <nav className="fixed bottom-0 left-0 right-0 bg-white border-t border-slate-200 flex justify-around p-2 lg:hidden z-50 safe-area-bottom">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id as any)}
            className={`flex flex-col items-center gap-1 p-2 rounded-xl transition-colors ${activeTab === tab.id
              ? 'text-indigo-600 bg-indigo-50'
              : 'text-slate-400 hover:text-slate-600'
              }`}
          >
            <div className="relative">
              {tab.icon}
              {tab.badge !== undefined && tab.badge > 0 && (
                <span className="absolute -top-1 -right-1 bg-rose-500 text-white text-[8px] font-bold h-3 w-3 flex items-center justify-center rounded-full">
                  {tab.badge}
                </span>
              )}
            </div>
            <span className="text-[10px] font-medium">{tab.label}</span>
          </button>
        ))}
        <button
          onClick={handleLogout}
          className="flex flex-col items-center gap-1 p-2 text-rose-500"
        >
          <LogOut className="w-5 h-5" />
          <span className="text-[10px] font-medium">Exit</span>
        </button>
      </nav>

      {/* Toast Notifications */}
      <ToastContainer aria-label="Notification Container" />
    </div>
  );
};

export default App;
