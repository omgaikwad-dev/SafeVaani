
import React, { useState, useEffect } from 'react';
import { ShieldCheck, Lock, Mail, Loader2, AlertTriangle } from 'lucide-react';
import { signInWithEmailAndPassword, createUserWithEmailAndPassword, fetchSignInMethodsForEmail } from 'firebase/auth';
import { auth } from '../firebase';

const Login = () => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Dummy credentials for "pushing" to Firebase as requested
    // These are not shown in the UI but are used to seed the account if it doesn't exist
    const DUMMY_EMAIL = "admin@police.sentinel.gov";
    const DUMMY_PASSWORD = "SecureLogin!2024";

    useEffect(() => {
        // Auto-seed the dummy user if they attempt to login with it and it doesn't exist?
        // Or just provide a way to trigger it.
        // The user asked to "push the dummy email and password in firebase"
        // We'll do this by attempting to create the user on component mount (silently failing if exists)
        // This ensures the credentials exist in Firebase Auth.

        const seedUser = async () => {
            try {
                // We can't check if user exists without admin SDK easily, 
                // but we can try to create it. If it fails with 'email-already-in-use', that's good.
                await createUserWithEmailAndPassword(auth, DUMMY_EMAIL, DUMMY_PASSWORD);
                console.log("Dummy user created in Firebase");
            } catch (err: any) {
                if (err.code === 'auth/email-already-in-use') {
                    console.log("Dummy user already exists in Firebase");
                } else {
                    console.error("Error seeding dummy user:", err);
                }
            }
        };

        seedUser();
    }, []);

    const handleLogin = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError(null);
        try {
            await signInWithEmailAndPassword(auth, email, password);
            // Success is handled by the auth state listener in App.tsx
        } catch (err: any) {
            console.error(err);
            setError("Invalid credentials. Please contact IT support.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center p-4">
            <div className="w-full max-w-md bg-white rounded-2xl shadow-xl overflow-hidden border border-slate-700/50">
                <div className="bg-indigo-600 p-8 text-center relative overflow-hidden">
                    <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-transparent via-white/30 to-transparent animate-pulse"></div>
                    <div className="w-16 h-16 bg-white/10 rounded-2xl mx-auto flex items-center justify-center backdrop-blur-sm mb-4 border border-white/20 shadow-inner">
                        <ShieldCheck className="w-10 h-10 text-white" />
                    </div>
                    <h1 className="text-2xl font-bold text-white tracking-tight">Sentinel SafeParent</h1>
                    <p className="text-indigo-200 text-sm font-medium mt-1 uppercase tracking-wider">Official Police Access</p>
                </div>

                <div className="p-8 space-y-6">
                    <div className="bg-amber-50 border border-amber-200 text-amber-800 px-4 py-3 rounded-lg text-xs flex items-start gap-2">
                        <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                        <p>Authorized personnel only. All access attempts are monitored and logged.</p>
                    </div>

                    <form onSubmit={handleLogin} className="space-y-4">
                        <div className="space-y-2">
                            <label className="text-sm font-semibold text-slate-700 ml-1">Official ID</label>
                            <div className="relative group">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400 group-focus-within:text-indigo-600 transition-colors">
                                    <Mail className="w-5 h-5" />
                                </div>
                                <input
                                    type="email"
                                    required
                                    value={email}
                                    onChange={(e) => setEmail(e.target.value)}
                                    className="block w-full pl-10 pr-3 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-slate-800 placeholder-slate-400 text-sm focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-all"
                                    placeholder="officer@sentinelArgs.com"
                                />
                            </div>
                        </div>

                        <div className="space-y-2">
                            <label className="text-sm font-semibold text-slate-700 ml-1">Secure Stamp</label>
                            <div className="relative group">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400 group-focus-within:text-indigo-600 transition-colors">
                                    <Lock className="w-5 h-5" />
                                </div>
                                <input
                                    type="password"
                                    required
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    className="block w-full pl-10 pr-3 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-slate-800 placeholder-slate-400 text-sm focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-all phone-pad"
                                    placeholder="••••••••••••"
                                />
                            </div>
                        </div>

                        {error && (
                            <div className="text-rose-500 text-xs font-medium bg-rose-50 px-3 py-2 rounded-lg border border-rose-100 flex items-center gap-2">
                                <AlertTriangle className="w-3 h-3" />
                                {error}
                            </div>
                        )}

                        <button
                            type="submit"
                            disabled={loading}
                            className="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-3 rounded-lg shadow-lg hover:shadow-indigo-500/25 transition-all text-sm flex items-center justify-center gap-2 disabled:opacity-70 disabled:cursor-not-allowed"
                        >
                            {loading ? (
                                <>
                                    <Loader2 className="w-4 h-4 animate-spin" />
                                    Verifying Credentials...
                                </>
                            ) : (
                                "Access Dashboard"
                            )}
                        </button>
                    </form>

                    <div className="pt-4 border-t border-slate-100 text-center">
                        <p className="text-xs text-slate-400">
                            By accessing this system, you agree to the <span className="text-indigo-600 font-medium cursor-pointer hover:underline">Terms of Service</span> and <span className="text-indigo-600 font-medium cursor-pointer hover:underline">Privacy Policy</span>.
                        </p>
                    </div>
                </div>
                <div className="bg-slate-50 py-3 px-8 text-center border-t border-slate-200">
                    <p className="text-[10px] text-slate-400 font-mono tracking-wider">v2.4.0 • SYSTEM SECURE • ENCRYPTED</p>
                </div>
            </div>
        </div>
    );
};

export default Login;
