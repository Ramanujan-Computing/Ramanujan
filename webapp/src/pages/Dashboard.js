import React, { useContext, useEffect, useState } from 'react';
import { 
  Box, 
  Typography, 
  Grid, 
  Paper, 
  Button,
  CircularProgress
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import apiService from '../services/apiService';
import TaskStatusCard from '../components/TaskStatusCard';

const Dashboard = () => {
  const { user } = useContext(AuthContext);
  const navigate = useNavigate();
  const [recentJobs, setRecentJobs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    // Fetch recent jobs when component mounts
    const fetchRecentJobs = async () => {
      try {
        setLoading(true);
        // Get user's job history from database
        const response = await apiService.getJobHistory(10, 0); // Get last 10 jobs
        setRecentJobs(response.data || []);
      } catch (err) {
        console.error('Error fetching recent jobs:', err);
        setError('Failed to load recent jobs. Please try again later.');
        // Mock data for demonstration when backend is not available
        setRecentJobs([
          {
            asyncId: 'mock-task-1',
            taskStatus: 'SUCCESS',
            createdAt: new Date().toISOString(),
            result: { status: 'Completed successfully' }
          },
          {
            asyncId: 'mock-task-2',
            taskStatus: 'FAILED',
            createdAt: new Date(Date.now() - 3600000).toISOString(),
            error: 'Execution failed due to syntax error'
          }
        ]);
      } finally {
        setLoading(false);
      }
    };

    fetchRecentJobs();
  }, []);

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" gutterBottom>
          Welcome, {user?.name || 'User'}
        </Typography>
        <Typography variant="body1" color="textSecondary">
          This is your Ramanujan Platform dashboard. Here you can submit code for execution and monitor the status of your jobs.
        </Typography>
      </Box>

      <Grid container spacing={3}>
        <Grid item xs={12}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <Typography variant="h6" gutterBottom>
              Quick Actions
            </Typography>
            <Box sx={{ mt: 2 }}>
              <Button 
                variant="contained" 
                color="primary" 
                fullWidth 
                sx={{ mb: 2 }}
                onClick={() => navigate('/submit')}
              >
                Submit New Job
              </Button>
              <Button 
                variant="outlined" 
                color="primary" 
                fullWidth
                onClick={() => navigate('/status')}
              >
                View All Jobs
              </Button>
            </Box>
          </Paper>
        </Grid>
      </Grid>

      <Box sx={{ mt: 4 }}>
        <Typography variant="h5" gutterBottom>
          Recent Jobs
        </Typography>
        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', my: 4 }}>
            <CircularProgress />
          </Box>
        ) : error ? (
          <Paper sx={{ p: 3, bgcolor: '#fff4f4' }}>
            <Typography color="error">{error}</Typography>
          </Paper>
        ) : recentJobs.length === 0 ? (
          <Paper sx={{ p: 3 }}>
            <Typography>No recent jobs found. Submit your first job!</Typography>
          </Paper>
        ) : (
          recentJobs.map((job) => (
            <TaskStatusCard key={job.asyncId || job.id} task={job} />
          ))
        )}
      </Box>
    </Box>
  );
};

export default Dashboard;
