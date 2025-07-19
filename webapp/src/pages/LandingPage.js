import React, { useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Typography,
  Container,
  Button,
  Paper,
  Grid,
  Card,
  CardContent,
  Chip,
  Stack,
  Divider
} from '@mui/material';
import {
  Computer,
  Speed,
  Language,
  NetworkCheck,
  Code,
  Security,
  GitHub
} from '@mui/icons-material';
import { AuthContext } from '../context/AuthContext';

const LandingPage = () => {
  const navigate = useNavigate();
  const { isAuthenticated } = useContext(AuthContext);

  const handleLoginClick = () => {
    navigate('/login');
  };

  const handleDashboardClick = () => {
    navigate('/dashboard');
  };

  const handleGitHubClick = () => {
    window.open('https://github.com/Ramanujan-Computing/Ramanujan', '_blank');
  };

  const handleGetStartedClick = () => {
    window.open('https://github.com/Ramanujan-Computing/Ramanujan#ramanujan-language', '_blank');
  };

  const features = [
    {
      icon: <Computer />,
      title: 'Multi-Device Support',
      description: 'Devices that can run computation: Android, Linux, Windows, and macOS devices'
    },
    {
      icon: <Speed />,
      title: 'High Performance',
      description: 'Faster than Python3 with optimized parallel execution'
    },
    {
      icon: <Language />,
      title: 'Ramanujan Language',
      description: 'Custom language designed for distributed computing with Python-like syntax'
    },
    {
      icon: <NetworkCheck />,
      title: 'Network Computing',
      description: 'Harness idle computation power across connected devices'
    },
    {
      icon: <Code />,
      title: 'Easy Development',
      description: 'No need for device-specific clients, just write once and run anywhere'
    },
    {
      icon: <Security />,
      title: 'Secure Execution',
      description: 'Safe and controlled execution environment for distributed tasks'
    }
  ];

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      {/* Header */}
      <Container maxWidth="lg">
        <Box sx={{ py: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold', color: 'primary.main' }}>
            Ramanujan Platform
          </Typography>
          <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
            <Button
              variant="outlined"
              size="large"
              onClick={handleGitHubClick}
              startIcon={<GitHub />}
              sx={{ px: 3 }}
            >
              GitHub
            </Button>
            <Button
              variant="contained"
              size="large"
              onClick={isAuthenticated ? handleDashboardClick : handleLoginClick}
              sx={{ px: 4 }}
            >
              {isAuthenticated ? 'Dashboard' : 'Login'}
            </Button>
          </Box>
        </Box>
      </Container>

      {/* Hero Section */}
      <Container maxWidth="lg">
        <Paper elevation={2} sx={{ p: 6, mb: 6, textAlign: 'center', bgcolor: 'primary.main', color: 'white' }}>
          <Typography variant="h2" component="h1" gutterBottom sx={{ fontWeight: 'bold', mb: 3 }}>
            Unleash the Power of Distributed Computing
          </Typography>
          <Typography variant="h5" sx={{ mb: 4, opacity: 0.9 }}>
            Utilize the untapped computation power of digital devices across the globe
          </Typography>
          <Typography variant="body1" sx={{ mb: 4, opacity: 0.8, fontSize: '1.1rem' }}>
            Open Source • MIT Licensed • High Performance Computing
          </Typography>
          <Stack direction="row" spacing={2} justifyContent="center" sx={{ mb: 4 }}>
            <Chip label="Android" variant="outlined" sx={{ color: 'white', borderColor: 'white' }} />
            <Chip label="Linux" variant="outlined" sx={{ color: 'white', borderColor: 'white' }} />
            <Chip label="Windows" variant="outlined" sx={{ color: 'white', borderColor: 'white' }} />
            <Chip label="macOS" variant="outlined" sx={{ color: 'white', borderColor: 'white' }} />
          </Stack>
          <Button
            variant="outlined"
            size="large"
            onClick={isAuthenticated ? handleDashboardClick : handleGetStartedClick}
            sx={{
              px: 6,
              py: 2,
              fontSize: '1.1rem',
              color: 'white',
              borderColor: 'white',
              '&:hover': {
                borderColor: 'white',
                bgcolor: 'rgba(255,255,255,0.1)'
              }
            }}
          >
            {isAuthenticated ? 'Go to Dashboard' : 'Get Started'}
          </Button>
        </Paper>
      </Container>

      {/* Inspiration Section */}
      <Container maxWidth="lg" sx={{ mb: 6 }}>
        <Paper elevation={1} sx={{ p: 4 }}>
          <Typography variant="h4" gutterBottom sx={{ textAlign: 'center', mb: 3 }}>
            The Inspiration
          </Typography>
          <Typography variant="body1" sx={{ fontSize: '1.1rem', lineHeight: 1.8, textAlign: 'center', maxWidth: '800px', mx: 'auto' }}>
            The Apollo guidance computer had a CPU only as powerful as a modern scientific calculator. 
            Today's smartphones and smart devices are <strong>at least million times more powerful</strong> than 
            the Apollo guidance computer, yet most of the time they remain idle. This project aims to 
            harness that immense, untapped computational potential.
          </Typography>
        </Paper>
      </Container>

      {/* Features Grid */}
      <Container maxWidth="lg" sx={{ mb: 6 }}>
        <Typography variant="h4" gutterBottom sx={{ textAlign: 'center', mb: 4 }}>
          Key Features
        </Typography>
        <Grid container spacing={3}>
          {features.map((feature, index) => (
            <Grid item xs={12} md={4} key={index}>
              <Card sx={{ height: '100%', transition: 'transform 0.2s', '&:hover': { transform: 'translateY(-4px)' } }}>
                <CardContent sx={{ textAlign: 'center', p: 3 }}>
                  <Box sx={{ color: 'primary.main', mb: 2 }}>
                    {React.cloneElement(feature.icon, { sx: { fontSize: 48 } })}
                  </Box>
                  <Typography variant="h6" gutterBottom>
                    {feature.title}
                  </Typography>
                  <Typography variant="body2" color="textSecondary">
                    {feature.description}
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      </Container>

      {/* How It Works */}
      <Container maxWidth="lg" sx={{ mb: 6 }}>
        <Paper elevation={1} sx={{ p: 4 }}>
          <Typography variant="h4" gutterBottom sx={{ textAlign: 'center', mb: 4 }}>
            How It's Different from BOINC
          </Typography>
          <Grid container spacing={4}>
            <Grid item xs={12} md={6}>
              <Typography variant="h6" gutterBottom color="primary">
                Traditional Approach (BOINC)
              </Typography>
              <Typography variant="body1" sx={{ mb: 2 }}>
                • Project owners must write new clients for each computation type
              </Typography>
              <Typography variant="body1" sx={{ mb: 2 }}>
                • Devices need to download and install new clients for every project
              </Typography>
              <Typography variant="body1">
                • Complex setup and maintenance for multiple projects
              </Typography>
            </Grid>
            <Grid item xs={12} md={6}>
              <Typography variant="h6" gutterBottom color="primary">
                Ramanujan Platform
              </Typography>
              <Typography variant="body1" sx={{ mb: 2 }}>
                • Project owners write code in the Ramanujan language
              </Typography>
              <Typography variant="body1" sx={{ mb: 2 }}>
                • Platform converts code to intermediate format automatically
              </Typography>
              <Typography variant="body1">
                • Devices install the Ramanujan client only once
              </Typography>
            </Grid>
          </Grid>
        </Paper>
      </Container>

      {/* Performance */}
      <Container maxWidth="lg" sx={{ mb: 6 }}>
        <Paper elevation={1} sx={{ p: 4, bgcolor: 'grey.50' }}>
          <Typography variant="h4" gutterBottom sx={{ textAlign: 'center', mb: 3 }}>
            Performance Benchmarks
          </Typography>
          <Grid container spacing={4}>
            <Grid item xs={12} md={6}>
              <Box sx={{ textAlign: 'center' }}>
                <Typography variant="h2" sx={{ color: 'success.main', fontWeight: 'bold', mb: 1 }}>
                  ~17% Faster
                </Typography>
                <Typography variant="h6" color="textSecondary" sx={{ mb: 2 }}>
                  than Python3
                </Typography>
              </Box>
            </Grid>
            <Grid item xs={12} md={6}>
              <Box sx={{ textAlign: 'center' }}>
                <Typography variant="h2" sx={{ color: 'success.main', fontWeight: 'bold', mb: 1 }}>
                  20X+ Faster
                </Typography>
                <Typography variant="h6" color="textSecondary" sx={{ mb: 2 }}>
                  than MATLAB
                </Typography>
              </Box>
            </Grid>
          </Grid>
          <Typography variant="body2" sx={{ textAlign: 'center', mt: 3, fontStyle: 'italic', color: 'text.secondary' }}>
            Tested on MacBook Air M3 with 8GB RAM
          </Typography>
        </Paper>
      </Container>

      {/* Future Vision */}
      <Container maxWidth="lg" sx={{ mb: 6 }}>
        <Typography variant="h4" gutterBottom sx={{ textAlign: 'center', mb: 4 }}>
          Future Vision
        </Typography>
        <Grid container spacing={3}>
          <Grid item xs={12} md={4}>
            <Card sx={{ height: '100%' }}>
              <CardContent>
                <Typography variant="h6" gutterBottom color="primary">
                  Language Evolution
                </Typography>
                <Typography variant="body2">
                  Adopting Python syntax and grammar with full Object-Oriented Programming support
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} md={4}>
            <Card sx={{ height: '100%' }}>
              <CardContent>
                <Typography variant="h6" gutterBottom color="primary">
                  Platform Expansion
                </Typography>
                <Typography variant="body2">
                  Support for iOS, smart watches, IoT devices, smart fridges, smart washing machine and other smart devices
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} md={4}>
            <Card sx={{ height: '100%' }}>
              <CardContent>
                <Typography variant="h6" gutterBottom color="primary">
                  ML Libraries
                </Typography>
                <Typography variant="body2">
                  Integration with major Python ML libraries like TensorFlow and PyTorch
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </Container>

      {/* CTA Section */}
      <Container maxWidth="lg" sx={{ mb: 6 }}>
        <Paper elevation={2} sx={{ p: 6, textAlign: 'center', bgcolor: 'secondary.main', color: 'white' }}>
          <Typography variant="h4" gutterBottom>
            Ready to Get Started?
          </Typography>
          <Typography variant="body1" sx={{ mb: 4, opacity: 0.9 }}>
            Join the distributed computing revolution and harness the power of connected devices
          </Typography>
          <Button
            variant="contained"
            size="large"
            onClick={isAuthenticated ? handleDashboardClick : handleLoginClick}
            sx={{
              px: 6,
              py: 2,
              fontSize: '1.1rem',
              bgcolor: 'white',
              color: 'secondary.main',
              '&:hover': {
                bgcolor: 'grey.100'
              }
            }}
          >
            {isAuthenticated ? 'Go to Dashboard' : 'Login to Dashboard'}
          </Button>
        </Paper>
      </Container>

      {/* Footer */}
      <Box sx={{ bgcolor: 'grey.900', color: 'white', py: 4 }}>
        <Container maxWidth="lg">
          <Grid container spacing={2} alignItems="center">
            <Grid item xs={12} md={6}>
              <Typography variant="body2">
                © 2025 Ramanujan Platform. Empowering distributed computing across all devices.
              </Typography>
            </Grid>
            <Grid item xs={12} md={6} sx={{ textAlign: { xs: 'left', md: 'right' } }}>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: { xs: 'flex-start', md: 'flex-end' }, gap: 2 }}>
                <Typography variant="body2">
                  MIT Licensed
                </Typography>
                <Button
                  variant="outlined"
                  size="small"
                  onClick={handleGitHubClick}
                  startIcon={<GitHub />}
                  sx={{ 
                    color: 'white', 
                    borderColor: 'white',
                    '&:hover': {
                      borderColor: 'white',
                      bgcolor: 'rgba(255,255,255,0.1)'
                    }
                  }}
                >
                  View on GitHub
                </Button>
              </Box>
            </Grid>
          </Grid>
        </Container>
      </Box>
    </Box>
  );
};

export default LandingPage;
