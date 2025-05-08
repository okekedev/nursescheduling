#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Starting all services for Nurse Scheduler application...${NC}"

# Create log directory
mkdir -p logs

# Check if GraphHopper is set up
if [ ! -d "graphhopper-standalone" ] || [ ! -f "graphhopper-standalone/config.yml" ]; then
    echo -e "${RED}GraphHopper is not set up. Please run setup-graphhopper.sh first.${NC}"
    exit 1
fi

# Check if Photon is already running
echo -e "${YELLOW}Checking if Photon is running...${NC}"
if curl -s "http://localhost:2322/api?q=test" > /dev/null 2>&1; then
    echo -e "${GREEN}Photon geocoding service is already running on port 2322${NC}"
else
    # Check if Photon has been initialized
    if [ -f "photon/photon.jar" ]; then
        echo -e "${YELLOW}Starting Photon geocoding service...${NC}"
        # Use correct parameters and add memory settings
        java -Xms1g -Xmx2g -jar photon/photon.jar -data-dir photon/data > logs/photon.log 2>&1 &
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
if curl -s "http://localhost:8989/info" > /dev/null 2>&1; then
    echo -e "${GREEN}GraphHopper is already running on port 8989${NC}"
else
    echo -e "${YELLOW}Starting GraphHopper...${NC}"
    cd graphhopper-standalone
    java -jar graphhopper/web/target/graphhopper-web-*.jar server config.yml > ../logs/graphhopper.log 2>&1 &
    GRAPHHOPPER_PID=$!
    echo -e "${GREEN}GraphHopper started with PID ${GRAPHHOPPER_PID}${NC}"
    cd ..
    
    # Wait for GraphHopper to be ready
    echo -e "${YELLOW}Waiting for GraphHopper to initialize (this may take a while for first run)...${NC}"
    COUNTER=0
    MAX_TRIES=120  # Allow up to 2 minutes for first-time initialization
    while ! curl -s "http://localhost:8989/info" > /dev/null 2>&1; do
        sleep 1
        COUNTER=$((COUNTER+1))
        if [ $((COUNTER % 10)) -eq 0 ]; then
            echo -e "${YELLOW}Still waiting for GraphHopper... (${COUNTER}s)${NC}"
        fi
        if [ $COUNTER -ge $MAX_TRIES ]; then
            echo -e "${YELLOW}GraphHopper is taking longer than expected to start, but continuing...${NC}"
            break
        fi
    done
    
    if [ $COUNTER -lt $MAX_TRIES ]; then
        echo -e "${GREEN}GraphHopper is ready!${NC}"
    fi
fi

# Start Spring Boot application
echo -e "${YELLOW}Starting Spring Boot application...${NC}"
cd nurse-scheduler-app
mvn spring-boot:run > ../logs/app.log 2>&1 &
APP_PID=$!
echo -e "${GREEN}Spring Boot application started with PID ${APP_PID}${NC}"
cd ..

# Save PIDs to a file for stopping later
echo "${PHOTON_PID:-already_running}" > .service_pids
echo "${GRAPHHOPPER_PID:-already_running}" >> .service_pids
echo "${APP_PID}" >> .service_pids

echo -e "${GREEN}All services started successfully!${NC}"
echo -e "${YELLOW}Application should be available at http://localhost:8080${NC}"
echo -e "${YELLOW}GraphHopper maps are available at http://localhost:8080/maps/maps/${NC}"
echo -e "${YELLOW}To stop all services, run ./stop-all-services.sh${NC}"

# Keep this script running to easily kill with Ctrl+C
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"
trap "bash stop-all-services.sh; exit" INT
wait