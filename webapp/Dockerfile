# Multi-stage build for frontend and backend

# Frontend build stage
FROM node:16-alpine as frontend-build

WORKDIR /app

# Copy frontend package.json and install dependencies
COPY package*.json ./
RUN npm ci

# Copy frontend source files
COPY src/ ./src/
COPY public/ ./public/

# Copy environment file for frontend build
COPY .env.production ./

# Build the React app with environment variables
RUN npm run build

# Backend build stage
FROM node:16-alpine as backend-build

WORKDIR /app/backend

# Copy backend package.json and install dependencies
COPY backend/package*.json ./
RUN npm ci --only=production

# Copy backend source files
COPY backend/ ./

# Production stage - Single Node.js server for Cloud Run
FROM node:16-alpine

WORKDIR /app

# Copy backend files
COPY --from=backend-build /app/backend ./backend

# Copy frontend build files to the location expected by server.js
COPY --from=frontend-build /app/build ./build

# Copy environment files
COPY .env.production ./.env.production
COPY .env.template ./.env.template

# Set production environment
ENV NODE_ENV=production

# Cloud Run provides PORT environment variable, default to 8080
EXPOSE 8080

# Change to backend directory
WORKDIR /app/backend

# Health check using the Node.js server
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD node -e "const http = require('http'); const options = { host: '0.0.0.0', port: process.env.PORT || 8080, path: '/health', timeout: 2000 }; const req = http.request(options, (res) => { process.exit(res.statusCode === 200 ? 0 : 1); }); req.on('error', () => process.exit(1)); req.end();"

# Start the Node.js server (it serves both API and static files in production)
CMD ["npm", "start"]
