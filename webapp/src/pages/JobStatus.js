import React, { useState, useEffect, useCallback } from 'react';
import { useLocation } from 'react-router-dom';
import {
  Box,
  Typography,
  Paper,
  TextField,
  Button,
  CircularProgress,
  Divider,
  Alert,
  AlertTitle,
  InputAdornment,
  IconButton,
} from '@mui/material';
import { Search as SearchIcon, Refresh as RefreshIcon } from '@mui/icons-material';
import TaskStatusCard from '../components/TaskStatusCard';
import apiService from '../services/apiService';

const JobStatus = () => {
  const location = useLocation();
  const [searchTaskId, setSearchTaskId] = useState('');
  const [taskStatus, setTaskStatus] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [activeTasks, setActiveTasks] = useState([]);
  const [loadingActiveTasks, setLoadingActiveTasks] = useState(true);
  const [highlightedTaskId, setHighlightedTaskId] = useState(null);

  // Check if there's a highlighted task from navigation
  useEffect(() => {
    if (location.state?.highlightTaskId) {
      setHighlightedTaskId(location.state.highlightTaskId);
      setSearchTaskId(location.state.highlightTaskId);
      handleSearch(location.state.highlightTaskId);

      // Clear the highlight after 5 seconds
      const timer = setTimeout(() => {
        setHighlightedTaskId(null);
      }, 5000);

      return () => clearTimeout(timer);
    }
  }, [location.state]);

  const fetchActiveTasks = useCallback(async () => {
    try {
      setLoadingActiveTasks(true);
      // Get user's active/pending jobs from database
      const response = await apiService.getJobHistory(50, 0); // Get recent jobs
      const activeTasks = response.data?.filter(job => 
        job.taskStatus === 'PENDING' || job.taskStatus === 'IN_PROGRESS'
      ) || [];
      
      setActiveTasks(activeTasks);
    } catch (err) {
      console.error('Error fetching active tasks:', err);
      setActiveTasks([]); // Set empty array on error instead of mock data
    } finally {
      setLoadingActiveTasks(false);
    }
  }, []);

  useEffect(() => {
    fetchActiveTasks();
  }, [fetchActiveTasks]);

  const handleSearch = async (id = searchTaskId) => {
    if (!id.trim()) {
      setError('Please enter a task ID');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      setTaskStatus(null);

      // First check if this task belongs to the user by querying database
      const historyResponse = await apiService.getJobHistory(1000, 0); // Get all user jobs
      const userJob = historyResponse.data?.find(job => job.asyncId === id.trim());
      
      if (!userJob) {
        setError('Task ID not found in your job history. You can only check status of your own tasks.');
        return;
      }

      // If task belongs to user, get current status from server
      const response = await apiService.checkTaskStatus(id);
      
      if (response.status === '200 OK' && response.data) {
        setTaskStatus({
          ...userJob, // Include database info (created time, etc.)
          ...response.data, // Overlay with current status
        });
      } else {
        setError('Failed to retrieve task status: ' + (response.message || 'Unknown error'));
      }
    } catch (err) {
      console.error('Error checking task status:', err);
      setError('Failed to check task status: ' + (err.message || 'Unknown error'));
    } finally {
      setLoading(false);
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const handleRefresh = () => {
    if (taskStatus) {
      handleSearch(taskStatus.asyncId || taskStatus.id);
    }
    fetchActiveTasks();
  };

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Job Status
      </Typography>

      <Paper sx={{ p: 3, mb: 4 }}>
        <Typography variant="h6" gutterBottom>
          Check Task Status
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <TextField
            fullWidth
            label="Task ID"
            variant="outlined"
            value={searchTaskId}
            onChange={(e) => setSearchTaskId(e.target.value)}
            onKeyPress={handleKeyPress}
            placeholder="Enter task ID to check status"
            InputProps={{
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton 
                    onClick={() => handleSearch()} 
                    edge="end"
                    disabled={loading || !searchTaskId.trim()}
                  >
                    <SearchIcon />
                  </IconButton>
                </InputAdornment>
              ),
            }}
            sx={{ mr: 2 }}
            disabled={loading}
          />
          <Button
            variant="contained"
            onClick={() => handleSearch()}
            disabled={loading || !searchTaskId.trim()}
            startIcon={loading ? <CircularProgress size={20} /> : null}
          >
            {loading ? 'Checking...' : 'Check Status'}
          </Button>
        </Box>

        {error && (
          <Alert severity="error" sx={{ mt: 2 }}>
            <AlertTitle>Error</AlertTitle>
            {error}
          </Alert>
        )}

        {taskStatus && (
          <Box sx={{ mt: 3 }}>
            <Divider sx={{ mb: 2 }} />
            <Typography variant="h6" gutterBottom>
              Task Status
            </Typography>
            <Box sx={{ 
              border: highlightedTaskId === taskStatus.asyncId ? '2px solid #4caf50' : 'none',
              borderRadius: '4px',
              transition: 'border 0.3s ease-in-out'
            }}>
              <TaskStatusCard task={taskStatus} />
            </Box>
            {(taskStatus.taskStatus === 'PENDING' || taskStatus.taskStatus === 'IN_PROGRESS') && (
              <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
                <Button
                  variant="outlined"
                  startIcon={<RefreshIcon />}
                  onClick={handleRefresh}
                >
                  Refresh Status
                </Button>
              </Box>
            )}
          </Box>
        )}
      </Paper>

      <Box>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="h5">
            Active Tasks
          </Typography>
          <Button 
            startIcon={<RefreshIcon />} 
            onClick={fetchActiveTasks}
            disabled={loadingActiveTasks}
          >
            Refresh
          </Button>
        </Box>

        {loadingActiveTasks ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', my: 4 }}>
            <CircularProgress />
          </Box>
        ) : activeTasks.length === 0 ? (
          <Paper sx={{ p: 3, textAlign: 'center' }}>
            <Typography>No active tasks found.</Typography>
          </Paper>
        ) : (
          activeTasks.map((task) => (
            <Box 
              key={task.asyncId || task.id} 
              sx={{ 
                border: highlightedTaskId === task.asyncId ? '2px solid #4caf50' : 'none',
                borderRadius: '4px',
                transition: 'border 0.3s ease-in-out'
              }}
            >
              <TaskStatusCard task={task} />
            </Box>
          ))
        )}
      </Box>
    </Box>
  );
};

export default JobStatus;
