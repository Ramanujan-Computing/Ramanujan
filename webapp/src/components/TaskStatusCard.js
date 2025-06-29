import React from 'react';
import {
  Paper,
  Typography,
  Box,
  Chip,
  CircularProgress,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';

const TaskStatusCard = ({ task }) => {
  const getStatusColor = (status) => {
    switch (status) {
      case 'SUCCESS':
        return 'success';
      case 'FAILED':
        return 'error';
      case 'PENDING':
      case 'IN_PROGRESS':
        return 'warning';
      default:
        return 'default';
    }
  };

  return (
    <Paper elevation={3} sx={{ p: 2, mb: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h6" component="div">
          Task ID: {task.asyncId || task.id}
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          {(task.taskStatus === 'PENDING' || task.taskStatus === 'IN_PROGRESS') && (
            <CircularProgress size={20} sx={{ mr: 1 }} />
          )}
          <Chip 
            label={task.taskStatus} 
            color={getStatusColor(task.taskStatus)} 
            size="small" 
          />
        </Box>
      </Box>
      
      <Box sx={{ mb: 2 }}>
        <Typography variant="body2" color="text.secondary">
          Submitted: {new Date(task.createdAt || Date.now()).toLocaleString()}
        </Typography>
      </Box>

      {task.result && (
        <Accordion>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography>Results</Typography>
          </AccordionSummary>
          <AccordionDetails>
            <Box sx={{ maxHeight: '300px', overflow: 'auto' }}>
              <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
                {JSON.stringify(task.result, null, 2)}
              </pre>
            </Box>
          </AccordionDetails>
        </Accordion>
      )}

      {task.error && (
        <Box sx={{ mt: 2 }}>
          <Typography variant="subtitle2" color="error">Error:</Typography>
          <Paper variant="outlined" sx={{ p: 1, bgcolor: '#fff4f4' }}>
            <Typography variant="body2" component="pre" sx={{ m: 0, whiteSpace: 'pre-wrap' }}>
              {task.error}
            </Typography>
          </Paper>
        </Box>
      )}
    </Paper>
  );
};

export default TaskStatusCard;
