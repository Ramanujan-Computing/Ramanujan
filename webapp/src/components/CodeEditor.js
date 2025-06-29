import React, { useState } from 'react';
import { Box, TextField, Paper } from '@mui/material';

const CodeEditor = ({ code, onChange, error }) => {
  const [isFocused, setIsFocused] = useState(false);

  return (
    <Paper 
      elevation={isFocused ? 3 : 1}
      sx={{ 
        position: 'relative',
        transition: 'all 0.3s ease-in-out',
        border: error ? '1px solid #f44336' : 'none'
      }}
    >
      <TextField
        fullWidth
        multiline
        variant="outlined"
        value={code}
        onChange={(e) => onChange(e.target.value)}
        onFocus={() => setIsFocused(true)}
        onBlur={() => setIsFocused(false)}
        placeholder="Enter your code here..."
        InputProps={{
          style: {
            fontFamily: '"Fira Code", "Courier New", monospace',
            fontSize: '14px',
            lineHeight: '1.5',
            padding: '16px',
          },
        }}
        sx={{
          '& .MuiOutlinedInput-root': {
            '& fieldset': {
              border: 'none',
            },
          },
        }}
        minRows={15}
        error={!!error}
        helperText={error}
      />
    </Paper>
  );
};

export default CodeEditor;
