import { createContext, useContext, useEffect, useState } from 'react';
import { type User, onAuthStateChanged } from 'firebase/auth';
import { auth } from '../../firebase/config';
import {
  signInAnon as fbSignInAnon,
  signInWithGoogle as fbSignInGoogle,
  signOut as fbSignOut,
  ensureUserProfile,
} from '../../firebase/auth';

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  signInAnon: () => Promise<void>;
  signInWithGoogle: () => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const unsub = onAuthStateChanged(auth, async (u) => {
      setUser(u);
      setLoading(false);
      if (u) await ensureUserProfile(u);
    });
    return unsub;
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        signInAnon: fbSignInAnon,
        signInWithGoogle: fbSignInGoogle,
        signOut: fbSignOut,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be inside AuthProvider');
  return ctx;
}
