#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Create logs directory if it doesn't exist
mkdir -p logs

echo -e "${YELLOW}Starting all services for Nurse Scheduler...${NC}"

# Check if Photon is already running
echo -e "Checking if Photon is running..."
PHOTON_RUNNING=false
if pgrep -f "photon.jar" > /dev/null; then
    echo -e "${GREEN}Photon is already running.${NC}"
    PHOTON_RUNNING=true
else
    echo -e "Starting Photon geocoding service..."
    cd photon
    nohup java -jar photon.jar > ../logs/photon.log 2>&1 &
    PHOTON_PID=$!
    echo -e "Photon started with PID $PHOTON_PID"
    cd ..
    
    # Check if photon started correctly
    echo -e "Waiting for Photon to be ready (max 30 seconds)..."
    COUNTER=0
    while ! curl -s http://localhost:2322/ > /dev/null && [ $COUNTER -lt 30 ]; do
        sleep 1
        COUNTER=$((COUNTER+1))
    done
    
    if curl -s http://localhost:2322/ > /dev/null; then
        echo -e "${GREEN}Photon is ready!${NC}"
    else
        echo -e "${RED}Photon failed to start within 30 seconds!${NC}"
        echo -e "Check logs at logs/photon.log for details."
        exit 1
    fi
fi

# Check if GraphHopper is already running
echo -e "Checking if GraphHopper is running..."
GH_RUNNING=false
if pgrep -f "graphhopper.jar" > /dev/null; then
    echo -e "${GREEN}GraphHopper is already running.${NC}"
    GH_RUNNING=true
else
    echo -e "Starting GraphHopper..."
    cd graphhopper-standalone
    nohup java -jar graphhopper.jar server config.yml > ../logs/graphhopper.log 2>&1 &
    GH_PID=$!
    echo -e "GraphHopper started with PID $GH_PID"
    cd ..
    
    # Check if GraphHopper started correctly (this may take some time)
    echo -e "Waiting for GraphHopper to be ready (max 60 seconds)..."
    COUNTER=0
    while ! curl -s http://localhost:8989/ > /dev/null && [ $COUNTER -lt 60 ]; do
        sleep 1
        COUNTER=$((COUNTER+1))
        # Display a progress indicator
        if [ $((COUNTER % 5)) -eq 0 ]; then
            echo -n "."
        fi
    done
    echo ""
    
    if curl -s http://localhost:8989/ > /dev/null; then
        echo -e "${GREEN}GraphHopper is ready!${NC}"
    else
        echo -e "${YELLOW}GraphHopper may still be initializing. This is normal for the first run.${NC}"
        echo -e "Check logs at logs/graphhopper.log for details."
        echo -e "You can continue with the application startup, but routing may not work immediately."
    fi
fi

# Start Spring Boot application
echo -e "${YELLOW}Starting Nurse Scheduler application...${NC}"
mvn spring-boot:run

# Note: The script will not reach this point until the Spring Boot app is terminated

# Cleanup - only stop services that were started by this script
echo -e "${YELLOW}Cleaning up...${NC}"

if [ "$GH_RUNNING" = false ] && pgrep -f "graphhopper.jar" > /dev/null; then
    echo -e "Stopping GraphHopper..."
    pkill -f "graphhopper.jar"
fi

if [ "$PHOTON_RUNNING" = false ] && pgrep -f "photon.jar" > /dev/null; then
    echo -e "Stopping Photon..."
    pkill -f "photon.jar"
fi

echo -e "${GREEN}All services stopped.${NC}"