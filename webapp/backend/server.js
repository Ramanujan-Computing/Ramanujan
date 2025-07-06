const express = require('express');
const cors = require('cors');
const mysql = require('mysql2/promise');
const jwt_decode = require('jwt-decode');
const axios = require('axios');
const path = require('path');

// Load environment variables from the parent directory
const envFile = process.env.NODE_ENV === 'production' ? '.env.production' : '.env.development';
const envPath = path.join(__dirname, '..', envFile);
require('dotenv').config({ path: envPath });

const app = express();
const PORT = process.env.PORT || process.env.BACKEND_PORT || 8080;

// Ramanujan API configuration
const RAMANUJAN_API_URL = process.env.RAMANUJAN_API_URL || 'https://server.ramanujan.dev';

// Create axios instance for Ramanujan API
const ramanujanClient = axios.create({
  baseURL: RAMANUJAN_API_URL,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  },
  timeout: 60000, // 60 seconds timeout
});

// Middleware
app.use(cors());
app.use(express.json());

// Add request logging for debugging
app.use((req, res, next) => {
  console.log(`${new Date().toISOString()} - ${req.method} ${req.url}`);
  next();
});

// Graceful shutdown handling
process.on('SIGTERM', () => {
  console.log('SIGTERM received, shutting down gracefully');
  process.exit(0);
});

process.on('SIGINT', () => {
  console.log('SIGINT received, shutting down gracefully');
  process.exit(0);
});

// Database connection configuration
const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: process.env.DB_PORT || 3306,
  user: process.env.DB_USERNAME,
  password: process.env.DB_PASSWORD,
  database: process.env.DB_NAME || 'ramanujan',
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0
};

// Create connection pool
const pool = mysql.createPool(dbConfig);

// Middleware to extract user ID from JWT token
const authenticateUser = (req, res, next) => {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'No token provided' });
  }

  try {
    const token = authHeader.substring(7);
    const decoded = jwt_decode(token);
    req.userId = decoded.email || decoded.sub; // Use email or sub as userId
    next();
  } catch (error) {
    return res.status(401).json({ error: 'Invalid token' });
  }
};

// API Routes

// Simple health check for container monitoring
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'S_OK', timestamp: new Date().toISOString() });
});

// Record user activity when a job is submitted
app.post('/api/user-activity', authenticateUser, async (req, res) => {
  try {
    const { asyncId } = req.body;
    const userId = req.userId;
    const timeStamp = Date.now();

    if (!asyncId) {
      return res.status(400).json({ error: 'asyncId is required' });
    }
    console.log('Recording user activity - User ID:', userId, 'Async ID:', asyncId, 'Timestamp:', timeStamp);
    const query = 'INSERT INTO userIdActivity (userId, asyncId, timeStamp) VALUES (?, ?, ?)';
    await pool.execute(query, [userId, asyncId, timeStamp]);

    res.json({ 
      success: true, 
      message: 'User activity recorded',
      data: { userId, asyncId, timeStamp }
    });
  } catch (error) {
    console.error('Error recording user activity:', error);
    res.status(500).json({ error: 'Failed to record user activity' });
  }
});

// Get user's job history
app.get('/api/user-history', authenticateUser, async (req, res) => {
  try {
    const userId = req.userId;
    const { limit = 50, offset = 0 } = req.query;

    const query = `
      SELECT asyncId, timeStamp 
      FROM userIdActivity 
      WHERE userId = ? 
      ORDER BY timeStamp DESC 
      LIMIT ? OFFSET ?
    `;
    
    const [rows] = await pool.execute(query, [userId, parseInt(limit), parseInt(offset)]);

    // Transform the data to include formatted timestamps
    const history = rows.map(row => ({
      asyncId: row.asyncId,
      timeStamp: row.timeStamp,
      createdAt: new Date(row.timeStamp).toISOString(),
      taskStatus: 'UNKNOWN' // We'll need to query the main API for status
    }));

    res.json({
      success: true,
      data: history,
      pagination: {
        limit: parseInt(limit),
        offset: parseInt(offset),
        total: history.length
      }
    });
  } catch (error) {
    console.error('Error fetching user history:', error);
    res.status(500).json({ error: 'Failed to fetch user history' });
  }
});

// Get user's recent activities (last 24 hours)
app.get('/api/user-recent', authenticateUser, async (req, res) => {
  try {
    const userId = req.userId;
    const last24Hours = Date.now() - (24 * 60 * 60 * 1000);

    const query = `
      SELECT asyncId, timeStamp 
      FROM userIdActivity 
      WHERE userId = ? AND timeStamp > ?
      ORDER BY timeStamp DESC
    `;
    
    const [rows] = await pool.execute(query, [userId, last24Hours]);

    const recent = rows.map(row => ({
      asyncId: row.asyncId,
      timeStamp: row.timeStamp,
      createdAt: new Date(row.timeStamp).toISOString()
    }));

    res.json({
      success: true,
      data: recent
    });
  } catch (error) {
    console.error('Error fetching recent activities:', error);
    res.status(500).json({ error: 'Failed to fetch recent activities' });
  }
});

// Health check endpoint
app.get('/api/health', async (req, res) => {
  try {
    // Test database connection
    await pool.execute('SELECT 1');
    res.json({ 
      status: 'healthy', 
      database: 'connected',
      timestamp: new Date().toISOString()
    });
  } catch (error) {
    res.status(500).json({ 
      status: 'unhealthy', 
      database: 'disconnected',
      error: error.message,
      timestamp: new Date().toISOString()
    });
  }
});

// Proxy endpoints for Ramanujan API
// Submit job to Ramanujan
app.post('/api/ramanujan/run', authenticateUser, async (req, res) => {
  try {
    console.log('Proxying job submission to Ramanujan API for user:', req.userId);
    
    const response = await ramanujanClient.post('/run?debug=false', req.body);
    
    console.log('Ramanujan API response:', response.status, response.data);
    res.json(response.data);
  } catch (error) {
    console.error('Error proxying to Ramanujan API:', error.response?.data || error.message);
    
    if (error.response) {
      // Forward the error response from Ramanujan API
      res.status(error.response.status).json(error.response.data);
    } else {
      // Network or other error
      res.status(500).json({ 
        error: 'Failed to connect to Ramanujan API',
        message: error.message 
      });
    }
  }
});

// Check task status from Ramanujan
app.get('/api/ramanujan/status', authenticateUser, async (req, res) => {
  try {
    const { uuid } = req.query;
    
    if (!uuid) {
      return res.status(400).json({ error: 'uuid parameter is required' });
    }
    
    console.log('Checking task status for uuid:', uuid, 'user:', req.userId);
    
    const response = await ramanujanClient.get(`/status?uuid=${uuid}`);
    
    res.json(response.data);
  } catch (error) {
    console.error('Error checking task status:', error.response?.data || error.message);
    
    if (error.response) {
      res.status(error.response.status).json(error.response.data);
    } else {
      res.status(500).json({ 
        error: 'Failed to connect to Ramanujan API',
        message: error.message 
      });
    }
  }
});

// Serve static files from React build (only in production)
if (process.env.NODE_ENV === 'production') {
  // Serve static files from the React app build directory
  app.use(express.static(path.join(__dirname, '../build')));
  
  // Handle React routing, return all requests to React app
  app.get('*', (req, res) => {
    res.sendFile(path.join(__dirname, '../build', 'index.html'));
  });
}

// Start server
app.listen(PORT, '0.0.0.0', () => {
  console.log(`Backend API server running on port ${PORT}`);
  console.log(`Database: ${dbConfig.host}:${dbConfig.port}/${dbConfig.database}`);
});

module.exports = app;
