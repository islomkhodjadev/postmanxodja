import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import type { User } from '../types';
import * as authService from '../services/auth';

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, name: string) => Promise<void>;
  loginWithTokens: (accessToken: string, refreshToken: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const initAuth = async () => {
      const { accessToken } = authService.getStoredTokens();
      if (accessToken) {
        try {
          const currentUser = await authService.getCurrentUser(accessToken);
          setUser(currentUser);
        } catch {
          authService.clearTokens();
        }
      }
      setIsLoading(false);
    };

    initAuth();
  }, []);

  const login = async (email: string, password: string) => {
    const response = await authService.login({ email, password });
    authService.storeTokens(response.access_token, response.refresh_token);
    setUser(response.user);
  };

  const register = async (email: string, password: string, name: string) => {
    const response = await authService.register({ email, password, name });
    authService.storeTokens(response.access_token, response.refresh_token);
    setUser(response.user);
  };

  const loginWithTokens = async (accessToken: string, refreshToken: string) => {
    authService.storeTokens(accessToken, refreshToken);
    const currentUser = await authService.getCurrentUser(accessToken);
    setUser(currentUser);
  };

  const logout = () => {
    const { accessToken } = authService.getStoredTokens();
    if (accessToken) {
      authService.logout(accessToken).catch(() => {});
    }
    authService.clearTokens();
    setUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
        register,
        loginWithTokens,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
