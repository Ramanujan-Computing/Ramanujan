#!/bin/bash

# Setup script for Ramanujan Webapp
# This script helps configure the necessary credentials

# Exit on error
set -e

# Define colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}===============================================${NC}"
echo -e "${GREEN}     Ramanujan Webapp Setup Script            ${NC}"
echo -e "${GREEN}===============================================${NC}"

# Function to validate Google Client ID format
validate_client_id() {
    local client_id=$1
    # Simple pattern match for Google Client ID format
    if [[ $client_id =~ ^[0-9]+-[a-z0-9A-Z_]+\.apps\.googleusercontent\.com$ ]]; then
        return 0
    else
        return 1
    fi
}

# Create .env from template if it doesn't exist
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}Creating .env file from template...${NC}"
    cp .env.template .env
    echo -e "${GREEN}.env file created successfully!${NC}"
fi

echo -e "\n${BLUE}=== Google OAuth Setup ===${NC}"
echo "To use this application, you need a Google OAuth Client ID."
echo ""
echo -e "${YELLOW}Steps to get your Google OAuth Client ID:${NC}"
echo "1. Go to: https://console.cloud.google.com/"
echo "2. Create a new project or select existing one"
echo "3. Go to 'APIs & Services' > 'Credentials'"
echo "4. Click 'Create Credentials' > 'OAuth client ID'"
echo "5. Choose 'Web application'"
echo "6. Add authorized JavaScript origins:"
echo "   - http://localhost:3000 (for development)"
echo "   - Your production domain (if deploying)"
echo "7. Copy the Client ID"
echo ""

# Check current Client ID
current_client_id=$(grep REACT_APP_GOOGLE_CLIENT_ID .env | cut -d '=' -f2)
if [[ -n "$current_client_id" && "$current_client_id" != "YOUR_GOOGLE_CLIENT_ID" && "$current_client_id" != "" ]]; then
    echo -e "Current Google Client ID: ${GREEN}$current_client_id${NC}"
    read -p "Would you like to update this Client ID? (y/n): " update_client_id
    if [[ "$update_client_id" != "y" && "$update_client_id" != "Y" ]]; then
        echo -e "${GREEN}Using existing Client ID.${NC}"
        exit 0
    fi
fi

# Prompt for new Client ID
while true; do
    echo ""
    read -p "Enter your Google OAuth Client ID: " client_id
    
    if [[ -z "$client_id" ]]; then
        echo -e "${RED}Client ID cannot be empty.${NC}"
        continue
    fi
    
    if validate_client_id "$client_id"; then
        # Update .env file
        if [[ "$OSTYPE" == "darwin"* ]]; then
            # macOS
            sed -i '' "s|REACT_APP_GOOGLE_CLIENT_ID=.*|REACT_APP_GOOGLE_CLIENT_ID=$client_id|g" .env
        else
            # Linux
            sed -i "s|REACT_APP_GOOGLE_CLIENT_ID=.*|REACT_APP_GOOGLE_CLIENT_ID=$client_id|g" .env
        fi
        
        echo -e "${GREEN}✓ Google Client ID updated successfully!${NC}"
        break
    else
        echo -e "${RED}Invalid Client ID format.${NC}"
        echo -e "${YELLOW}Expected format: 123456789-abcdefghijklmnop.apps.googleusercontent.com${NC}"
    fi
done

echo -e "\n${BLUE}=== API Configuration ===${NC}"
current_api_url=$(grep REACT_APP_API_URL .env | cut -d '=' -f2)
echo -e "Current API URL: ${GREEN}$current_api_url${NC}"
read -p "Would you like to change the API URL? (y/n): " change_api_url

if [[ "$change_api_url" == "y" || "$change_api_url" == "Y" ]]; then
    read -p "Enter the API URL [https://server.ramanujan.dev]: " api_url
    api_url=${api_url:-https://server.ramanujan.dev}
    
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        sed -i '' "s|REACT_APP_API_URL=.*|REACT_APP_API_URL=$api_url|g" .env
    else
        # Linux
        sed -i "s|REACT_APP_API_URL=.*|REACT_APP_API_URL=$api_url|g" .env
    fi
    
    echo -e "${GREEN}✓ API URL updated successfully!${NC}"
fi

echo -e "\n${GREEN}===============================================${NC}"
echo -e "${GREEN}     Setup completed successfully!            ${NC}"
echo -e "${GREEN}===============================================${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Run 'npm install' to install dependencies"
echo "2. Run 'npm start' for development mode"
echo "3. Run 'npm run build' for production build"
echo ""
echo -e "${BLUE}Note: Your .env file contains sensitive information and is excluded from version control.${NC}"
