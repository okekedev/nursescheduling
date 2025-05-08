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
echo -e "${YELLOW}Downloading latest GraphHopper release...${NC}"
LATEST_VERSION="7.0"
DOWNLOAD_URL="https://github.com/graphhopper/graphhopper/releases/download/${LATEST_VERSION}/graphhopper-web-${LATEST_VERSION}.jar"

echo -e "${YELLOW}Downloading from: ${DOWNLOAD_URL}${NC}"
curl -L $DOWNLOAD_URL -o graphhopper.jar

if [ ! -f "graphhopper.jar" ]; then
    echo -e "${RED}Failed to download GraphHopper. Please check the URL and try again.${NC}"
    exit 1
fi

# Verify the JAR file is valid
if ! java -jar graphhopper.jar --help 2>&1 | grep -q "Usage"; then
    echo -e "${RED}The downloaded JAR file appears to be invalid or corrupted.${NC}"
    echo -e "${YELLOW}Trying alternate download method...${NC}"
    rm graphhopper.jar
    wget $DOWNLOAD_URL -O graphhopper.jar
    
    if [ ! -f "graphhopper.jar" ] || ! java -jar graphhopper.jar --help 2>&1 | grep -q "Usage"; then
        echo -e "${RED}Failed to download a valid GraphHopper JAR file.${NC}"
        exit 1
    fi
fi

echo -e "${GREEN}GraphHopper downloaded successfully.${NC}"

# Create data directory
echo -e "${YELLOW}Creating data directory...${NC}"
mkdir -p data

# Download OSM file if it doesn't exist
OSM_FILE="data/texas-latest.osm.pbf"
if [ ! -f "$OSM_FILE" ]; then
    echo -e "${YELLOW}Downloading Texas OSM data...${NC}"
    curl -L https://download.geofabrik.de/north-america/us/texas-latest.osm.pbf -o $OSM_FILE
    
    if [ ! -f "$OSM_FILE" ]; then
        echo -e "${RED}Failed to download OSM data. Please download it manually.${NC}"
        echo -e "${YELLOW}Download from: https://download.geofabrik.de/north-america/us/texas-latest.osm.pbf${NC}"
        echo -e "${YELLOW}Save to: ${PWD}/$OSM_FILE${NC}"
        exit 1
    fi
fi

# Create GraphHopper config file
echo -e "${YELLOW}Creating GraphHopper configuration...${NC}"
cat > config.yml << EOL
graphhopper:
  datareader.file: $OSM_FILE
  graph.location: data/graph-cache
  profiles:
    - name: car
      vehicle: car
      weighting: fastest
  profiles_ch:
    - profile: car

server:
  application_connectors:
    - type: http
      port: 8989
  admin_connectors:
    - type: http
      port: 8990

# Uncomment these for memory settings if needed
# Xmx: 1g
# Xms: 1g
EOL

echo -e "${GREEN}GraphHopper setup complete!${NC}"
echo -e "${YELLOW}You can start GraphHopper with:${NC}"
echo -e "  cd ${GRAPHHOPPER_DIR} && java -jar graphhopper.jar server config.yml"
echo -e "${YELLOW}The GraphHopper UI will be available at:${NC}"
echo -e "  http://localhost:8989/maps/"

# Go back to original directory
cd ..