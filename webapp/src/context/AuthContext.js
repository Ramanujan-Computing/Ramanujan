import React, { createContext, useState, useEffect } from 'react';
import jwt_decode from 'jwt-decode';

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Check if token exists in localStorage and is valid
    const checkAuth = () => {
      const token = localStorage.getItem('auth_token');
      if (token) {
        try {
          const decodedToken = jwt_decode(token);
          // Check if token is expired
          if (decodedToken.exp * 1000 < Date.now()) {
            logout();
          } else {
            setUser(decodedToken);
            setIsAuthenticated(true);
          }
        } catch (error) {
          console.error("Invalid token:", error);
          logout();
        }
      }
      setLoading(false);
    };

    checkAuth();
  }, []);

  const login = (response) => {
    const token = response.credential;
    const decodedToken = jwt_decode(token);
    
    localStorage.setItem('auth_token', token);
    setUser(decodedToken);
    setIsAuthenticated(true);
  };

  const logout = () => {
    localStorage.removeItem('auth_token');
    setUser(null);
    setIsAuthenticated(false);
  };

  return (
    <AuthContext.Provider value={{ isAuthenticated, user, login, logout, loading }}>
      {children}
    </AuthContext.Provider>
  );
};
