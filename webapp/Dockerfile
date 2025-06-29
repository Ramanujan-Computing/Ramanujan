# Multi-stage build for frontend and backend

# Backend build stage
FROM node:16-alpine as backend-build

WORKDIR /app/backend

# Copy backend package.json and install dependencies
COPY backend/package*.json ./
RUN npm ci --only=production

# Copy backend source files
COPY backend/ ./

# Frontend build stage
FROM node:16-alpine as frontend-build

WORKDIR /app

# Copy frontend package.json and install dependencies
COPY package*.json ./
RUN npm ci

# Copy frontend source files
COPY src/ ./src/
COPY public/ ./public/

# Build the React app
RUN npm run build

# Production stage
FROM node:16-alpine

WORKDIR /app

# Install nginx for serving frontend
RUN apk add --no-cache nginx

# Copy backend files
COPY --from=backend-build /app/backend ./backend

# Copy frontend build files
COPY --from=frontend-build /app/build ./frontend/build

# Copy nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Create startup script
RUN echo '#!/bin/sh' > /app/start.sh && \
    echo 'cd /app/backend && npm start &' >> /app/start.sh && \
    echo 'nginx -g "daemon off;"' >> /app/start.sh && \
    chmod +x /app/start.sh

# Expose ports
EXPOSE 80 3001

# Start both services
CMD ["/app/start.sh"]
