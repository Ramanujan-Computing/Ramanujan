import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Typography,
  Button,
  Paper,
  Alert,
  AlertTitle,
  CircularProgress,
  Divider,
  Snackbar,
} from '@mui/material';
import CodeEditor from '../components/CodeEditor';
import FileUpload from '../components/FileUpload';
import apiService from '../services/apiService';

const JobSubmit = () => {
  const navigate = useNavigate();
  const [code, setCode] = useState('');
  const [files, setFiles] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);
  const [taskId, setTaskId] = useState(null);
  const [backendConnected, setBackendConnected] = useState(null);

  // Test backend connection on component mount
  useEffect(() => {
    const testBackendConnection = async () => {
      try {
        await apiService.checkBackendHealth();
        setBackendConnected(true);
        console.log('Backend connection successful');
      } catch (error) {
        setBackendConnected(false);
        console.warn('Backend connection failed - user activity tracking may not work:', error.message);
      }
    };
    
    testBackendConnection();
  }, []);

  const handleSubmit = async () => {
    if (!code.trim()) {
      setError('Please enter some code before submitting');
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const codeRunRequest = {
        code: code,
        csvInformationList: files.map(file => ({
          fileName: file.fileName,
          data: file.data
        }))
      };

      console.log('Submitting job with request:', codeRunRequest);
      const response = await apiService.submitJob(codeRunRequest);
      console.log('Job submission response:', response);
      
      if (response.status === '200 OK' && response.data) {
        setSuccess(true);
        setTaskId(response.data.asyncId);
        console.log('Job submitted successfully, asyncId:', response.data.asyncId);
        
        // Automatically navigate to status page after 2 seconds
        setTimeout(() => {
          navigate('/status', { state: { highlightTaskId: response.data.asyncId } });
        }, 2000);
      } else {
        console.error('Job submission failed with response:', response);
        setError('Job submission failed: ' + (response.message || 'Unknown error'));
      }
    } catch (err) {
      console.error('Error submitting job:', err);
      setError('Failed to submit job: ' + (err.message || 'Unknown error'));
    } finally {
      setLoading(false);
    }
  };

  const handleCloseSnackbar = () => {
    setSuccess(false);
  };

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Submit Job
      </Typography>
      
      {backendConnected === false && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          <AlertTitle>Backend Connection Issue</AlertTitle>
          User activity tracking may not work properly. Job submission will still work, but your job history may not be recorded.
        </Alert>
      )}
      
      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Code
        </Typography>
        <Typography variant="body2" color="textSecondary" sx={{ mb: 2 }}>
          Enter the code you want to execute.
        </Typography>
        <CodeEditor 
          code={code} 
          onChange={setCode} 
          error={error && !code.trim() ? 'Code is required' : null}
        />
      </Paper>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Input Files (Optional)
        </Typography>
        <Typography variant="body2" color="textSecondary" sx={{ mb: 2 }}>
          Upload any CSV files your code needs for execution.
        </Typography>
        <FileUpload files={files} onFilesChange={setFiles} />
      </Paper>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          <AlertTitle>Error</AlertTitle>
          {error}
        </Alert>
      )}

      <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
        <Button
          variant="outlined"
          sx={{ mr: 2 }}
          disabled={loading}
          onClick={() => navigate('/')}
        >
          Cancel
        </Button>
        <Button
          variant="contained"
          color="primary"
          disabled={loading || !code.trim()}
          onClick={handleSubmit}
          startIcon={loading ? <CircularProgress size={20} /> : null}
        >
          {loading ? 'Submitting...' : 'Submit Job'}
        </Button>
      </Box>

      <Snackbar
        open={success}
        autoHideDuration={6000}
        onClose={handleCloseSnackbar}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert onClose={handleCloseSnackbar} severity="success" sx={{ width: '100%' }}>
          Job submitted successfully! Task ID: {taskId}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default JobSubmit;
