import axios from 'axios';

// For production (when REACT_APP_BACKEND_URL is empty), use same origin (no baseURL)
// For development, use localhost:3001
const BACKEND_API_URL = process.env.REACT_APP_BACKEND_URL || 
  (process.env.NODE_ENV === 'production' ? '' : 'http://localhost:3001');

// Create axios instance for backend API (all requests go through backend now)
const backendClient = axios.create({
  baseURL: BACKEND_API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Interceptor to add auth token to requests
const addAuthToken = (config) => {
  const token = localStorage.getItem('auth_token');
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`;
  }
  return config;
};

backendClient.interceptors.request.use(addAuthToken, (error) => Promise.reject(error));

// API service functions
const apiService = {
  // Submit code for execution (now goes through backend)
  submitJob: async (codeRunRequest) => {
    try {
      const response = await backendClient.post('/api/ramanujan/run', codeRunRequest);
      
      // If job submission is successful, record user activity BEFORE returning
      if (response.data && response.data.data && response.data.status === '200 OK' && response.data.data.asyncId) {
        try {
          await apiService.recordUserActivity(response.data.data.asyncId);
        } catch (activityError) {
          console.error('Failed to record user activity:', activityError);
          // Don't fail the main request if activity recording fails, but log it prominently
          console.warn('WARNING: Job was submitted but user activity was not recorded. Status checking may not work.');
        }
      } else {
        console.warn('Job submission response format unexpected:', response.data);
      }
      
      return response.data;
    } catch (error) {
      console.error('Job submission failed:', error);
      throw error;
    }
  },

  // Check task status (now goes through backend)
  checkTaskStatus: async (taskId) => {
    try {
      const response = await backendClient.get(`/api/ramanujan/status?uuid=${taskId}`);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  // Record user activity in database
  recordUserActivity: async (asyncId) => {
    try {
      const response = await backendClient.post('/api/user-activity', { asyncId });
      return response.data;
    } catch (error) {
      console.error('Failed to record user activity:', error.response?.data || error.message);
      throw error;
    }
  },

  // Get user's job history from database
  getJobHistory: async (limit = 50, offset = 0) => {
    try {
      const response = await backendClient.get(`/api/user-history?limit=${limit}&offset=${offset}`);
      
      // For each job in history, try to get current status
      const history = response.data.data || [];
      const enrichedHistory = await Promise.allSettled(
        history.map(async (job) => {
          try {
            const statusResponse = await apiService.checkTaskStatus(job.asyncId);
            return {
              ...job,
              taskStatus: statusResponse.data?.taskStatus || 'UNKNOWN',
              result: statusResponse.data?.result || null,
              error: statusResponse.data?.error || null
            };
          } catch {
            return {
              ...job,
              taskStatus: 'UNKNOWN',
              result: null,
              error: 'Status unavailable'
            };
          }
        })
      );

      return {
        ...response.data,
        data: enrichedHistory.map(result => 
          result.status === 'fulfilled' ? result.value : result.reason
        )
      };
    } catch (error) {
      throw error;
    }
  },

  // Get user's recent activities (last 24 hours)
  getRecentActivities: async () => {
    try {
      const response = await backendClient.get('/api/user-recent');
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  // Check backend health
  checkBackendHealth: async () => {
    try {
      const response = await backendClient.get('/api/health');
      return response.data;
    } catch (error) {
      throw error;
    }
  }
};

export default apiService;
