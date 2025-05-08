#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Stopping all services for Nurse Scheduler application...${NC}"

# Check if we're in the nurse-scheduler-app directory
if [ "$(basename "$(pwd)")" = "nurse-scheduler-app" ]; then
    # We're in the nurse-scheduler-app directory
    PID_FILE="./.service_pids"
    if [ ! -f "$PID_FILE" ]; then
        PID_FILE="../.service_pids"
    fi
else
    # We're in the parent directory
    PID_FILE="./.service_pids"
fi

# Check if PID file exists
if [ ! -f "$PID_FILE" ]; then
    echo -e "${YELLOW}No service PIDs file found. Will try to find and kill processes by port.${NC}"
    
    # Try to find Spring Boot application by port
    SPRING_PID=$(lsof -i:8080 -t 2>/dev/null)
    if [ -n "$SPRING_PID" ]; then
        echo -e "${YELLOW}Stopping Spring Boot application (PID: $SPRING_PID)...${NC}"
        kill -15 $SPRING_PID 2>/dev/null || kill -9 $SPRING_PID 2>/dev/null
        echo -e "${GREEN}Spring Boot application stopped.${NC}"
    else
        echo -e "${YELLOW}No Spring Boot application found running on port 8080.${NC}"
    fi
    
    # Try to find GraphHopper by port
    GRAPHHOPPER_PID=$(lsof -i:8989 -t 2>/dev/null)
    if [ -n "$GRAPHHOPPER_PID" ]; then
        echo -e "${YELLOW}Stopping GraphHopper (PID: $GRAPHHOPPER_PID)...${NC}"
        kill -15 $GRAPHHOPPER_PID 2>/dev/null || kill -9 $GRAPHHOPPER_PID 2>/dev/null
        echo -e "${GREEN}GraphHopper stopped.${NC}"
    else
        echo -e "${YELLOW}No GraphHopper instance found running on port 8989.${NC}"
    fi
    
    # Try to find Photon by port
    PHOTON_PID=$(lsof -i:2322 -t 2>/dev/null)
    if [ -n "$PHOTON_PID" ]; then
        echo -e "${YELLOW}Stopping Photon (PID: $PHOTON_PID)...${NC}"
        kill -15 $PHOTON_PID 2>/dev/null || kill -9 $PHOTON_PID 2>/dev/null
        echo -e "${GREEN}Photon stopped.${NC}"
    else
        echo -e "${YELLOW}No Photon instance found running on port 2322.${NC}"
    fi
else
    # Read PIDs from file
    echo -e "${YELLOW}Reading service PIDs from $PID_FILE...${NC}"
    IFS=$'\r\n' PIDS=($(cat "$PID_FILE"))
    
    # Stop Photon
    if [ "${PIDS[0]}" != "already_running" ]; then
        echo -e "${YELLOW}Stopping Photon (PID: ${PIDS[0]})...${NC}"
        kill -15 ${PIDS[0]} 2>/dev/null || kill -9 ${PIDS[0]} 2>/dev/null
        echo -e "${GREEN}Photon stopped.${NC}"
    else
        echo -e "${YELLOW}Photon was already running before script started.${NC}"
        PHOTON_PID=$(lsof -i:2322 -t 2>/dev/null)
        if [ -n "$PHOTON_PID" ]; then
            echo -e "${YELLOW}Stopping Photon (PID: $PHOTON_PID)...${NC}"
            kill -15 $PHOTON_PID 2>/dev/null || kill -9 $PHOTON_PID 2>/dev/null
            echo -e "${GREEN}Photon stopped.${NC}"
        fi
    fi
    
    # Stop GraphHopper
    if [ "${PIDS[1]}" != "already_running" ]; then
        echo -e "${YELLOW}Stopping GraphHopper (PID: ${PIDS[1]})...${NC}"
        kill -15 ${PIDS[1]} 2>/dev/null || kill -9 ${PIDS[1]} 2>/dev/null
        echo -e "${GREEN}GraphHopper stopped.${NC}"
    else
        echo -e "${YELLOW}GraphHopper was already running before script started.${NC}"
        GRAPHHOPPER_PID=$(lsof -i:8989 -t 2>/dev/null)
        if [ -n "$GRAPHHOPPER_PID" ]; then
            echo -e "${YELLOW}Stopping GraphHopper (PID: $GRAPHHOPPER_PID)...${NC}"
            kill -15 $GRAPHHOPPER_PID 2>/dev/null || kill -9 $GRAPHHOPPER_PID 2>/dev/null
            echo -e "${GREEN}GraphHopper stopped.${NC}"
        fi
    fi
    
    # Stop Spring Boot application
    if [ -n "${PIDS[2]}" ]; then
        echo -e "${YELLOW}Stopping Spring Boot application (PID: ${PIDS[2]})...${NC}"
        kill -15 ${PIDS[2]} 2>/dev/null || kill -9 ${PIDS[2]} 2>/dev/null
        echo -e "${GREEN}Spring Boot application stopped.${NC}"
    else
        echo -e "${YELLOW}No Spring Boot application PID found in PID file.${NC}"
        SPRING_PID=$(lsof -i:8080 -t 2>/dev/null)
        if [ -n "$SPRING_PID" ]; then
            echo -e "${YELLOW}Stopping Spring Boot application (PID: $SPRING_PID)...${NC}"
            kill -15 $SPRING_PID 2>/dev/null || kill -9 $SPRING_PID 2>/dev/null
            echo -e "${GREEN}Spring Boot application stopped.${NC}"
        fi
    fi
    
    # Remove PID file
    rm -f "$PID_FILE"
fi

# Make double-sure all Java processes for these services are stopped
echo -e "${YELLOW}Checking for any remaining Java processes...${NC}"

# Get all Java processes
JAVA_PROCESSES=$(ps -ef | grep java | grep -v grep)

# Check for GraphHopper
if echo "$JAVA_PROCESSES" | grep -q "graphhopper.jar"; then
    GH_PID=$(echo "$JAVA_PROCESSES" | grep "graphhopper.jar" | awk '{print $2}')
    echo -e "${YELLOW}Found GraphHopper still running (PID: $GH_PID). Stopping...${NC}"
    kill -15 $GH_PID 2>/dev/null || kill -9 $GH_PID 2>/dev/null
fi

# Check for Photon
if echo "$JAVA_PROCESSES" | grep -q "photon.jar"; then
    PHOTON_PID=$(echo "$JAVA_PROCESSES" | grep "photon.jar" | awk '{print $2}')
    echo -e "${YELLOW}Found Photon still running (PID: $PHOTON_PID). Stopping...${NC}"
    kill -15 $PHOTON_PID 2>/dev/null || kill -9 $PHOTON_PID 2>/dev/null
fi

# Check for Spring Boot
if echo "$JAVA_PROCESSES" | grep -q "spring-boot:run"; then
    SPRING_PID=$(echo "$JAVA_PROCESSES" | grep "spring-boot:run" | awk '{print $2}')
    echo -e "${YELLOW}Found Spring Boot still running (PID: $SPRING_PID). Stopping...${NC}"
    kill -15 $SPRING_PID 2>/dev/null || kill -9 $SPRING_PID 2>/dev/null
fi

echo -e "${GREEN}All services stopped successfully!${NC}"