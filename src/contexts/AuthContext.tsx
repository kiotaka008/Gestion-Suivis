import { useCallback, useEffect, useState, type ReactNode } from 'react';
import type { User } from '../types/models';
import { authService, type LoginPayload } from '../services/authService';
import { AuthContext } from './authContextValue';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      const me = await authService.me();
      setUser(me);
    } catch {
      setUser(null);
    }
  }, []);

  useEffect(() => {
    void (async () => {
      await refresh();
      setIsLoading(false);
    })();
  }, [refresh]);

  // Écoute l'événement global déclenché par l'intercepteur Axios sur 401
  useEffect(() => {
    const handler = () => setUser(null);
    window.addEventListener('auth:unauthorized', handler);
    return () => window.removeEventListener('auth:unauthorized', handler);
  }, []);

  const login = useCallback(async (payload: LoginPayload) => {
    const res = await authService.login(payload);
    setUser(res.user);
  }, []);

  const loginWithGoogle = useCallback(async (credential: string, organization?: string) => {
    const res = await authService.loginWithGoogle(credential, organization);
    setUser(res.user);
  }, []);

  const logout = useCallback(async () => {
    try {
      await authService.logout();
    } finally {
      setUser(null);
    }
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        isAuthenticated: !!user,
        login,
        loginWithGoogle,
        setUser,
        logout,
        refresh,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}