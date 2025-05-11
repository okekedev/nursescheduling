#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Starting Photon Geocoding Service Test...${NC}"

# Check if Photon is running
if ! curl -s "http://localhost:2322/" > /dev/null; then
    echo -e "${RED}Error: Photon geocoding service is not running on port 2322${NC}"
    echo -e "Please start Photon first with: ./photon/photon.jar"
    exit 1
fi

echo -e "${GREEN}Photon service detected at http://localhost:2322${NC}"
echo -e "${YELLOW}Running test with Spring Boot...${NC}"

# Run the Spring Boot application with the photon-test profile
mvn spring-boot:run -Dspring-boot.run.profiles=photon-test

# Check if log files were created
if [ -f "photon-test-log.txt" ]; then
    echo -e "${GREEN}Test completed. Check log file: photon-test-log.txt${NC}"
else
    echo -e "${RED}Test may have failed. No log file was created.${NC}"
fi

if [ -f "photon-geocoding.log" ]; then
    echo -e "${GREEN}Geocoding log file: photon-geocoding.log${NC}"
fi