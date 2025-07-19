import React, { useContext, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Typography, Paper, Container } from '@mui/material';
import { GoogleLogin } from '@react-oauth/google';
import { AuthContext } from '../context/AuthContext';

const LoginPage = () => {
  const { isAuthenticated, login } = useContext(AuthContext);
  const navigate = useNavigate();

  useEffect(() => {
    // Redirect to dashboard if already authenticated
    if (isAuthenticated) {
      navigate('/dashboard');
    }
  }, [isAuthenticated, navigate]);

  const handleLoginSuccess = (credentialResponse) => {
    login(credentialResponse);
    navigate('/dashboard');
  };

  const handleLoginError = () => {
    console.log('Login Failed');
  };

  return (
    <Container maxWidth="xs">
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '100vh',
        }}
      >
        <Paper elevation={3} sx={{ p: 4, width: '100%', textAlign: 'center' }}>
          <Typography variant="h4" component="h1" gutterBottom>
            Ramanujan Platform
          </Typography>
          <Typography variant="body1" color="textSecondary" sx={{ mb: 3 }}>
            Sign in to access the dashboard
          </Typography>
          <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
            <GoogleLogin
              onSuccess={handleLoginSuccess}
              onError={handleLoginError}
              useOneTap
            />
          </Box>
        </Paper>
      </Box>
    </Container>
  );
};

export default LoginPage;
