import React from 'react';
import { Typography, Paper, Link } from '@mui/material';

const LanguageConstructs = () => (
  <Paper sx={{ p: 3 }}>
    <Typography variant="h6" gutterBottom>
      Ramanujan Language Constructs
    </Typography>
    <Typography variant="body2" color="textSecondary" paragraph>
      For the full list of language constructs and syntax, see the official documentation:
      <br />
      <Link href="https://github.com/Ramanujan-Computing/Ramanujan#ramanujan-language" target="_blank" rel="noopener">
        https://github.com/Ramanujan-Computing/Ramanujan#ramanujan-language
      </Link>
    </Typography>
  </Paper>
);

export default LanguageConstructs;
