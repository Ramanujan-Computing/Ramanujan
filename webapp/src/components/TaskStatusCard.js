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
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  Tab,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';

const TaskStatusCard = ({ task }) => {
  const [tabValue, setTabValue] = React.useState(0);

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

  // Parse result similar to ExecutorImpl.java
  const parseResult = (result) => {
    if (!result) return { variables: {}, arrays: {} };

    const variableStore = {};
    const arrayStore = {};

    try {
      // Extract variables
      if (result.variables && Array.isArray(result.variables)) {
        result.variables.forEach(varObj => {
          if (varObj.variableName && varObj.object !== undefined) {
            variableStore[varObj.variableName] = varObj.object;
          }
        });
      }

      // Extract arrays
      if (result.arrays && Array.isArray(result.arrays)) {
        result.arrays.forEach(arrObj => {
          if (arrObj.arrayId) {
            let name = arrObj.arrayId;
            
            // Skip function-related arrays (same as ExecutorImpl.java)
            if (name.includes('func')) {
              return;
            }
            
            // Extract name after '_name_' prefix (same as ExecutorImpl.java)
            if (name.includes('_name_')) {
              const nameParts = name.split('_name_');
              if (nameParts.length > 1) {
                name = nameParts[1];
              } else {
                // If split doesn't work as expected, skip this array
                return;
              }
            }
            
            const indexStr = arrObj.indexStr || '0';
            const value = arrObj.object;

            if (!arrayStore[name]) {
              arrayStore[name] = {};
            }
            arrayStore[name][indexStr] = value;
          }
        });
      }
    } catch (error) {
      console.error('Error parsing result:', error);
    }

    return { variables: variableStore, arrays: arrayStore };
  };

  const { variables, arrays } = parseResult(task.result);

  const handleTabChange = (event, newValue) => {
    setTabValue(newValue);
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

      {task.result && (Object.keys(variables).length > 0 || Object.keys(arrays).length > 0) && (
        <Accordion>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography>Results - Variables & Arrays</Typography>
          </AccordionSummary>
          <AccordionDetails>
            <Box sx={{ width: '100%' }}>
              <Tabs value={tabValue} onChange={handleTabChange} aria-label="result tabs">
                <Tab label={`Variables (${Object.keys(variables).length})`} />
                <Tab label={`Arrays (${Object.keys(arrays).length})`} />
              </Tabs>
              
              {/* Variables Tab */}
              {tabValue === 0 && (
                <Box sx={{ mt: 2 }}>
                  {Object.keys(variables).length === 0 ? (
                    <Typography color="text.secondary">No variables found</Typography>
                  ) : (
                    <TableContainer component={Paper} variant="outlined">
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell><strong>Variable Name</strong></TableCell>
                            <TableCell><strong>Value</strong></TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {Object.entries(variables).map(([name, value]) => (
                            <TableRow key={name}>
                              <TableCell>{name}</TableCell>
                              <TableCell>
                                <Typography variant="body2" component="span">
                                  {typeof value === 'object' ? JSON.stringify(value) : String(value)}
                                </Typography>
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  )}
                </Box>
              )}

              {/* Arrays Tab */}
              {tabValue === 1 && (
                <Box sx={{ mt: 2 }}>
                  {Object.keys(arrays).length === 0 ? (
                    <Typography color="text.secondary">No arrays found</Typography>
                  ) : (
                    Object.entries(arrays).map(([arrayName, arrayData]) => (
                      <Box key={arrayName} sx={{ mb: 3 }}>
                        <Typography variant="h6" gutterBottom>
                          Array: {arrayName}
                        </Typography>
                        <TableContainer component={Paper} variant="outlined">
                          <Table size="small">
                            <TableHead>
                              <TableRow>
                                <TableCell><strong>Index</strong></TableCell>
                                <TableCell><strong>Value</strong></TableCell>
                              </TableRow>
                            </TableHead>
                            <TableBody>
                              {Object.entries(arrayData).map(([index, value]) => (
                                <TableRow key={index}>
                                  <TableCell>{index}</TableCell>
                                  <TableCell>
                                    <Typography variant="body2" component="span">
                                      {typeof value === 'object' ? JSON.stringify(value) : String(value)}
                                    </Typography>
                                  </TableCell>
                                </TableRow>
                              ))}
                            </TableBody>
                          </Table>
                        </TableContainer>
                      </Box>
                    ))
                  )}
                </Box>
              )}
            </Box>
          </AccordionDetails>
        </Accordion>
      )}

      {task.result && Object.keys(variables).length === 0 && Object.keys(arrays).length === 0 && (
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
