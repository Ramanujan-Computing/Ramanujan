#!/bin/bash

# Build script for Ramanujan Webapp

# Exit on error
set -e

# Define colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Display banner
echo -e "${GREEN}===============================================${NC}"
echo -e "${GREEN}       Ramanujan Webapp Build Script          ${NC}"
echo -e "${GREEN}===============================================${NC}"

# Check if running in the correct directory
if [ ! -f "package.json" ]; then
    echo -e "${RED}Error: package.json not found.${NC}"
    echo -e "${YELLOW}Please run this script from the webapp directory.${NC}"
    exit 1
fi

# Function to validate Google Client ID format
validate_client_id() {
    local client_id=$1
    # Simple pattern match for Google Client ID format
    if [[ $client_id =~ ^[0-9]+-[a-z0-9]+\.apps\.googleusercontent\.com$ ]]; then
        return 0
    else
        return 1
    fi
}

# Function to update .env files
update_env_files() {
    local client_id=$1
    local api_url=$2

    # Update .env
    sed -i '' "s|REACT_APP_GOOGLE_CLIENT_ID=.*|REACT_APP_GOOGLE_CLIENT_ID=$client_id|g" .env
    sed -i '' "s|REACT_APP_API_URL=.*|REACT_APP_API_URL=$api_url|g" .env

    # Update .env.development
    sed -i '' "s|REACT_APP_GOOGLE_CLIENT_ID=.*|REACT_APP_GOOGLE_CLIENT_ID=$client_id|g" .env.development
    sed -i '' "s|REACT_APP_API_URL=.*|REACT_APP_API_URL=$api_url|g" .env.development

    # Update .env.production
    sed -i '' "s|REACT_APP_GOOGLE_CLIENT_ID=.*|REACT_APP_GOOGLE_CLIENT_ID=$client_id|g" .env.production
    sed -i '' "s|REACT_APP_API_URL=.*|REACT_APP_API_URL=$api_url|g" .env.production
}

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check for Node.js
if ! command_exists node; then
    echo -e "${RED}Error: Node.js is not installed.${NC}"
    echo -e "${YELLOW}Please install Node.js before proceeding.${NC}"
    exit 1
fi

# Check Node.js version
NODE_VERSION=$(node -v | cut -d 'v' -f2)
NODE_MAJOR_VERSION=$(echo $NODE_VERSION | cut -d '.' -f1)
if [ "$NODE_MAJOR_VERSION" -lt 14 ]; then
    echo -e "${RED}Error: Node.js version is too old.${NC}"
    echo -e "${YELLOW}Please upgrade to Node.js v14 or higher.${NC}"
    exit 1
fi

# Check for npm
if ! command_exists npm; then
    echo -e "${RED}Error: npm is not installed.${NC}"
    echo -e "${YELLOW}Please install npm before proceeding.${NC}"
    exit 1
fi

echo -e "${GREEN}Starting build process...${NC}"

# Create .env from template if it doesn't exist
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}Creating .env file from template...${NC}"
    cp .env.template .env
fi

# Check for and configure Google OAuth credentials
echo -e "${YELLOW}Checking Google OAuth Credentials...${NC}"
echo "A Google OAuth Client ID is required for authentication."

# Check if credentials are already set
current_client_id=$(grep REACT_APP_GOOGLE_CLIENT_ID .env | cut -d '=' -f2)
if [[ -n "$current_client_id" && "$current_client_id" != "YOUR_GOOGLE_CLIENT_ID" && "$current_client_id" != "" ]]; then
    echo -e "Current Google Client ID: ${GREEN}$current_client_id${NC}"
    read -p "Would you like to use this Client ID? (y/n): " use_current
    if [[ "$use_current" == "y" || "$use_current" == "Y" ]]; then
        client_id=$current_client_id
    else
        client_id=""
    fi
else
    echo -e "${YELLOW}No valid Google Client ID found.${NC}"
    client_id=""
fi

# Prompt for Google Client ID if needed
while [[ -z "$client_id" ]]; do
    echo "You can create one at: https://console.cloud.google.com/apis/credentials"
    read -p "Enter your Google OAuth Client ID: " input_client_id
    if validate_client_id "$input_client_id"; then
        client_id=$input_client_id
    else
        echo -e "${RED}Invalid Client ID format. It should look like: 123456789-abcdef.apps.googleusercontent.com${NC}"
    fi
done

# Prompt for API URL
current_api_url=$(grep REACT_APP_API_URL .env | cut -d '=' -f2)
echo -e "\n${YELLOW}API Configuration${NC}"
echo -e "Current API URL: ${GREEN}$current_api_url${NC}"
read -p "Would you like to use this API URL? (y/n): " use_current_url
if [[ "$use_current_url" == "y" || "$use_current_url" == "Y" ]]; then
    api_url=$current_api_url
else
    read -p "Enter the API URL: " input_api_url
    api_url=${input_api_url:-$current_api_url}
fi

# Update environment files
echo -e "\n${YELLOW}Updating environment files...${NC}"
update_env_files "$client_id" "$api_url"
echo -e "${GREEN}Environment files updated successfully!${NC}"

# Install dependencies
echo -e "${YELLOW}Installing dependencies...${NC}"
npm install

# Create production build
echo -e "${YELLOW}Creating production build...${NC}"
npm run build

# Check if build was successful
if [ $? -eq 0 ] && [ -d "build" ]; then
    echo -e "${GREEN}Build completed successfully!${NC}"
    echo -e "${YELLOW}The build files are available in the 'build' directory.${NC}"
else
    echo -e "${RED}Build failed.${NC}"
    exit 1
fi

echo -e "${GREEN}===============================================${NC}"
echo -e "${GREEN}       Build Process Completed                 ${NC}"
echo -e "${GREEN}===============================================${NC}"

exit 0
