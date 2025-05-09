#!/bin/bash

# Create directories
mkdir -p photon/data

# Download Photon release
wget https://github.com/komoot/photon/releases/download/0.3.5/photon-0.3.5.jar -O photon/photon.jar

# Download Texas OSM data extract
wget https://download.geofabrik.de/north-america/us/texas-latest.osm.pbf -O photon/data/texas-latest.osm.pbf

# Start Photon with data import (will take time)
# To this line:
java -jar photon/photon.jar -nominatim-import -host localhost -port 9200 -data-dir photon/data