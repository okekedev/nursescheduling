#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Starting all services for Nurse Scheduler application...${NC}"

# Set paths for the flattened directory structure
CURRENT_DIR="."
PARENT_DIR="."
LOG_DIR="./logs"
PHOTON_DIR="./photon"
GRAPHHOPPER_DIR="./graphhopper-standalone"

# Create log directories
mkdir -p $LOG_DIR

# Debug info
echo -e "${YELLOW}Using paths:${NC}"
echo -e "  PHOTON_DIR: ${PHOTON_DIR}"
echo -e "  GRAPHHOPPER_DIR: ${GRAPHHOPPER_DIR}"
echo -e "  LOG_DIR: ${LOG_DIR}"

# Check if Photon files exist
if [ ! -f "${PHOTON_DIR}/photon.jar" ]; then
    echo -e "${RED}Error: Could not find Photon JAR at ${PHOTON_DIR}/photon.jar${NC}"
    echo -e "${YELLOW}Current directory: $(pwd)${NC}"
    echo -e "${YELLOW}Files in ./photon (if exists):${NC}"
    if [ -d "./photon" ]; then
        ls -la ./photon
    else
        echo "  Directory ./photon does not exist"
    fi
    
    # Ask if user wants to run setup-photon.sh
    echo -e "${YELLOW}Would you like to run setup-photon.sh to set up Photon? (y/n)${NC}"
    read -r response
    if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
        echo -e "${YELLOW}Running setup-photon.sh...${NC}"
        ./setup-photon.sh
        if [ ! -f "${PHOTON_DIR}/photon.jar" ]; then
            echo -e "${RED}Photon setup failed. Please run setup-photon.sh manually.${NC}"
            exit 1
        fi
    else
        exit 1
    fi
fi

# Check if Photon is already running
echo -e "${YELLOW}Checking if Photon is running...${NC}"
if curl -s "http://localhost:2322/api?q=test" > /dev/null 2>&1; then
    echo -e "${GREEN}Photon geocoding service is already running on port 2322${NC}"
else
    # Check if Photon has been initialized
    if [ -f "${PHOTON_DIR}/photon.jar" ]; then
        echo -e "${YELLOW}Starting Photon geocoding service...${NC}"
        # Use correct parameters and add memory settings
        java -Xms1g -Xmx2g -jar "${PHOTON_DIR}/photon.jar" -data-dir "${PHOTON_DIR}/data" > "${LOG_DIR}/photon.log" 2>&1 &
        PHOTON_PID=$!
        echo -e "${GREEN}Photon started with PID ${PHOTON_PID}${NC}"
        
        # Wait for Photon to be ready (with a timeout)
        echo -e "${YELLOW}Waiting for Photon to be ready (max 30 seconds)...${NC}"
        COUNTER=0
        MAX_TRIES=30
        while ! curl -s "http://localhost:2322/api?q=test" > /dev/null 2>&1; do
            sleep 1
            COUNTER=$((COUNTER+1))
            if [ $COUNTER -ge $MAX_TRIES ]; then
                echo -e "${YELLOW}Photon is taking longer than expected to start, but continuing with other services...${NC}"
                break
            fi
        done
        
        if [ $COUNTER -lt $MAX_TRIES ]; then
            echo -e "${GREEN}Photon is ready!${NC}"
        fi
    else
        echo -e "${RED}Photon is not set up. Please run setup-photon.sh first.${NC}"
        exit 1
    fi
fi

# Check if GraphHopper is already running
echo -e "${YELLOW}Checking if GraphHopper is running...${NC}"
if curl -s "http://localhost:8989/maps/" > /dev/null 2>&1; then
    echo -e "${GREEN}GraphHopper is already running on port 8989${NC}"
else
    # Check if GraphHopper has been set up
    if [ ! -f "${GRAPHHOPPER_DIR}/graphhopper.jar" ] || [ ! -f "${GRAPHHOPPER_DIR}/config.yml" ]; then
        echo -e "${YELLOW}GraphHopper is not set up. Running setup-graphhopper.sh...${NC}"
        ./setup-graphhopper.sh
        if [ ! -f "${GRAPHHOPPER_DIR}/graphhopper.jar" ] || [ ! -f "${GRAPHHOPPER_DIR}/config.yml" ]; then
            echo -e "${RED}GraphHopper setup failed. Please run setup-graphhopper.sh manually.${NC}"
            exit 1
        fi
    fi
    
    echo -e "${YELLOW}Starting GraphHopper...${NC}"
    
    # Create the log file first to avoid "No such file" error
    touch "${LOG_DIR}/graphhopper.log"
    
    # Use a consistent approach to running GraphHopper and logging
    cd "${GRAPHHOPPER_DIR}"
    java -jar graphhopper.jar server config.yml > "../${LOG_DIR}/graphhopper.log" 2>&1 &
    GRAPHHOPPER_PID=$!
    cd ..
    
    echo -e "${GREEN}GraphHopper started with PID ${GRAPHHOPPER_PID}${NC}"
    
    # Wait for GraphHopper to be ready (with a timeout)
    echo -e "${YELLOW}Waiting for GraphHopper to be ready (max 60 seconds)...${NC}"
    COUNTER=0
    MAX_TRIES=60
    while ! curl -s "http://localhost:8989/maps/" > /dev/null 2>&1; do
        sleep 1
        COUNTER=$((COUNTER+1))
        if [ $COUNTER -ge $MAX_TRIES ]; then
            echo -e "${YELLOW}GraphHopper is taking longer than expected to start, but continuing with other services...${NC}"
            break
        fi
        
        # Display a progress indicator every 5 seconds
        if [ $((COUNTER % 5)) -eq 0 ]; then
            echo -n "."
        fi
    done
    echo ""  # Add a newline after the progress indicators
    
    if [ $COUNTER -lt $MAX_TRIES ]; then
        echo -e "${GREEN}GraphHopper is ready!${NC}"
    fi
fi

# Start Spring Boot application
echo -e "${YELLOW}Starting Spring Boot application...${NC}"

# Create log file
touch "${LOG_DIR}/app.log"

# Try different Maven command options
if command -v mvn &> /dev/null; then
    mvn spring-boot:run > "${LOG_DIR}/app.log" 2>&1 &
elif [ -f "./mvnw" ]; then
    ./mvnw spring-boot:run > "${LOG_DIR}/app.log" 2>&1 &
elif [ -f "mvnw" ]; then
    mvnw spring-boot:run > "${LOG_DIR}/app.log" 2>&1 &
else
    echo -e "${RED}Error: Could not find Maven or Maven Wrapper script${NC}"
    exit 1
fi
APP_PID=$!
echo -e "${GREEN}Spring Boot application started with PID ${APP_PID}${NC}"

# Save PIDs to a file for stopping later
PID_FILE="${CURRENT_DIR}/.service_pids"
echo "${PHOTON_PID:-already_running}" > "${PID_FILE}"
echo "${GRAPHHOPPER_PID:-already_running}" >> "${PID_FILE}"
echo "${APP_PID}" >> "${PID_FILE}"

echo -e "${GREEN}All services started successfully!${NC}"
echo -e "${YELLOW}Application should be available at http://localhost:8080${NC}"
echo -e "${YELLOW}To stop all services, run ./stop-all-services.sh${NC}"

# Keep this script running to easily kill with Ctrl+C
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"
trap "bash ${CURRENT_DIR}/stop-all-services.sh; exit" INT
wait