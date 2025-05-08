#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Setting up GraphHopper from pre-built release...${NC}"

# Create GraphHopper directory and clean any existing installation
GRAPHHOPPER_DIR="graphhopper-standalone"
echo -e "${YELLOW}Creating directory: ${GRAPHHOPPER_DIR}${NC}"
mkdir -p $GRAPHHOPPER_DIR
cd $GRAPHHOPPER_DIR

# Clean existing installation if it exists
if [ -f "graphhopper.jar" ]; then
    echo -e "${YELLOW}Removing existing GraphHopper JAR file...${NC}"
    rm graphhopper.jar
fi

# Download latest GraphHopper release
echo -e "${YELLOW}Downloading GraphHopper 10.2 release...${NC}"
LATEST_VERSION="10.2"
DOWNLOAD_URL="https://github.com/graphhopper/graphhopper/releases/download/${LATEST_VERSION}/graphhopper-web-${LATEST_VERSION}.jar"

echo -e "${YELLOW}Attempting to download from: ${DOWNLOAD_URL}${NC}"

# Try multiple download methods
if command -v wget > /dev/null; then
    echo -e "${YELLOW}Using wget to download...${NC}"
    wget --no-check-certificate $DOWNLOAD_URL -O graphhopper.jar
elif command -v curl > /dev/null; then
    echo -e "${YELLOW}Using curl to download...${NC}"
    curl -k -L $DOWNLOAD_URL -o graphhopper.jar
else
    echo -e "${RED}Neither wget nor curl is available. Please install one of these tools or download manually.${NC}"
    echo -e "${YELLOW}Download URL: ${DOWNLOAD_URL}${NC}"
    echo -e "${YELLOW}Save to: ${PWD}/graphhopper.jar${NC}"
    exit 1
fi

if [ ! -f "graphhopper.jar" ]; then
    echo -e "${RED}Failed to download GraphHopper.${NC}"
    echo -e "${YELLOW}Please download it manually:${NC}"
    echo -e "${YELLOW}1. Go to: https://github.com/graphhopper/graphhopper/releases/tag/${LATEST_VERSION}${NC}"
    echo -e "${YELLOW}2. Download: graphhopper-web-${LATEST_VERSION}.jar${NC}"
    echo -e "${YELLOW}3. Save to: ${PWD}/graphhopper.jar${NC}"
    exit 1
fi

echo -e "${GREEN}GraphHopper downloaded successfully.${NC}"

# Create data directory
echo -e "${YELLOW}Creating data directory...${NC}"
mkdir -p data

# Download OSM file if it doesn't exist
OSM_FILE="data/texas-latest.osm.pbf"
if [ ! -f "$OSM_FILE" ]; then
    echo -e "${YELLOW}Downloading Texas OSM data...${NC}"
    if command -v wget > /dev/null; then
        wget --no-check-certificate https://download.geofabrik.de/north-america/us/texas-latest.osm.pbf -O $OSM_FILE
    else
        curl -k -L https://download.geofabrik.de/north-america/us/texas-latest.osm.pbf -o $OSM_FILE
    fi
    
    if [ ! -f "$OSM_FILE" ]; then
        echo -e "${RED}Failed to download OSM data. Please download it manually.${NC}"
        echo -e "${YELLOW}Download from: https://download.geofabrik.de/north-america/us/texas-latest.osm.pbf${NC}"
        echo -e "${YELLOW}Save to: ${PWD}/$OSM_FILE${NC}"
        exit 1
    fi
fi

# Create GraphHopper config file compatible with version 10.2
echo -e "${YELLOW}Creating GraphHopper configuration...${NC}"
cat > config.yml << EOL
graphhopper:
  datareader.file: $OSM_FILE
  graph.location: data/graph-cache
  
  # Explicitly set encoded values as required by the error message
  graph.encoded_values: road_class
  
  # Simplified profile with custom model
  profiles:
    - name: car
      profile: car
      weighting: custom
      custom_model:
        priority:
          - if: road_class == MOTORWAY
            multiply_by: 0.8
        speed:
          - if: true
            limit_to: 120
  
  # Contraction Hierarchies
  profiles_ch:
    - profile: car
  
  # Import settings
  import.osm.ignored_highways: elevator,escalator,steps

# Server configuration
server:
  application_connectors:
    - type: http
      port: 8989
  admin_connectors:
    - type: http
      port: 8990

logging:
  level: INFO
EOL

echo -e "${GREEN}GraphHopper setup complete!${NC}"
echo -e "${YELLOW}You can start GraphHopper with:${NC}"
echo -e "  cd ${GRAPHHOPPER_DIR} && java -jar graphhopper.jar server config.yml"
echo -e "${YELLOW}The GraphHopper UI will be available at:${NC}"
echo -e "  http://localhost:8989/maps/"
echo -e "${YELLOW}The routing API will be available at:${NC}"
echo -e "  http://localhost:8989/route"
echo -e "${YELLOW}Note: Initial startup may take some time as GraphHopper builds the routing graph.${NC}"

# Go back to original directory
cd ..