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
  Tabs,
  Tab,
  FormControlLabel,
  Switch,
} from '@mui/material';
import CodeEditor from '../components/CodeEditor';
import FileUpload from '../components/FileUpload';
import LanguageConstructs from '../components/LanguageConstructs';
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
  const [tabValue, setTabValue] = useState(0);
  const [useFileAsCode, setUseFileAsCode] = useState(false);

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
    let codeToSubmit = code;
    
    // If using file as code and there's a file uploaded, use the first file's content
    if (useFileAsCode && files.length > 0) {
      codeToSubmit = files[0].data;
    }
    
    if (!codeToSubmit.trim()) {
      setError('Please enter some code or upload a code file before submitting');
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const codeRunRequest = {
        code: codeToSubmit,
        csvInformationList: useFileAsCode ? [] : files.map(file => ({
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

  const handleTabChange = (event, newValue) => {
    setTabValue(newValue);
  };

  const handleFilesChange = (newFiles) => {
    setFiles(newFiles);
    // If using file as code and a file is uploaded, populate the editor with the file content
    if (useFileAsCode && newFiles.length > 0) {
      setCode(newFiles[0].data);
    } else if (useFileAsCode && newFiles.length === 0) {
      setCode('');
    }
  }

  const handleUseFileAsCodeChange = (event) => {
    setUseFileAsCode(event.target.checked);
    if (!event.target.checked) {
      // If disabling file as code, clear any file content from the editor
      setCode('');
    } else if (files.length > 0) {
      // If enabling and there are files, use the first file's content
      setCode(files[0].data);
    }
  };

  const isSubmitDisabled = () => {
    if (useFileAsCode) {
      // Enable submit if a file is attached and its content is non-empty
      return loading || files.length === 0 || !(files[0]?.data && files[0].data.trim());
    }
    return loading || !code.trim();
  }

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

      <Paper sx={{ mb: 3 }}>
        <Tabs value={tabValue} onChange={handleTabChange} sx={{ borderBottom: 1, borderColor: 'divider' }}>
          <Tab label="Code Submission" />
          <Tab label="Language Guide" />
        </Tabs>
        
        {tabValue === 0 && (
          <Box sx={{ p: 3 }}>
            <Paper sx={{ p: 3, mb: 3 }}>
              <Typography variant="h6" gutterBottom>
                Code Input
              </Typography>
              <Typography variant="body2" color="textSecondary" sx={{ mb: 2 }}>
                Write your Ramanujan code directly in the editor below, or upload a code file.
              </Typography>
              
              <FormControlLabel
                control={
                  <Switch
                    checked={useFileAsCode}
                    onChange={handleUseFileAsCodeChange}
                  />
                }
                label="Use uploaded file as code (instead of manual input)"
                sx={{ mb: 2 }}
              />
              
              <CodeEditor 
                code={code} 
                onChange={setCode} 
                error={error && !code.trim() && !useFileAsCode ? 'Code is required' : null}
              />
              
              {useFileAsCode && (
                <Alert severity="info" sx={{ mt: 2 }}>
                  When enabled, the first uploaded file will be used as your code. The editor above shows the file content for preview.
                </Alert>
              )}
            </Paper>

            <Paper sx={{ p: 3, mb: 3 }}>
              <Typography variant="h6" gutterBottom>
                {useFileAsCode ? 'Code Files' : 'Input Files (Optional)'}
              </Typography>
              <Typography variant="body2" color="textSecondary" sx={{ mb: 2 }}>
                {useFileAsCode 
                  ? 'Upload your Ramanujan code files. The first file will be executed.' 
                  : 'Upload any additional files your code needs for execution.'}
              </Typography>
              <FileUpload files={files} onFilesChange={handleFilesChange} />
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
                disabled={isSubmitDisabled()}
                onClick={handleSubmit}
                startIcon={loading ? <CircularProgress size={20} /> : null}
              >
                {loading ? 'Submitting...' : 'Submit Job'}
              </Button>
            </Box>
          </Box>
        )}
        
        {tabValue === 1 && (
          <Box sx={{ p: 3 }}>
            <LanguageConstructs />
          </Box>
        )}
      </Paper>

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
