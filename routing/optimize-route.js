#!/usr/bin/env node

// Route optimization script using local Nominatim for geocoding and GraphHopper for routing
const http = require('http');
const querystring = require('querystring');

// Sample addresses from command line or use defaults
const addresses = process.argv.slice(2).length > 0 ? 
  process.argv.slice(2) : 
  [
    '1100 Congress Ave Austin TX 78701', // Texas State Capitol
    '2201 Barton Springs Rd Austin TX 78746', // Zilker Park
    '2100 Barton Springs Rd Austin TX 78704', // Barton Springs Pool
    '1800 Congress Ave Austin TX 78701', // Bob Bullock Texas History Museum
    '2300 Leo St Austin TX 78704' // S Congress Ave area
  ];

console.log('Optimizing route for the following addresses:');
addresses.forEach((addr, i) => console.log(`${i+1}. ${addr}`));
console.log('\nGeocoding addresses...');

// Function to geocode an address using local Nominatim
function geocodeAddress(address) {
  return new Promise((resolve, reject) => {
    const params = querystring.stringify({
      q: address,
      format: 'json',
      limit: 1
    });

    const options = {
      hostname: 'localhost',
      port: 8080,
      path: `/search?${params}`,
      method: 'GET',
      headers: {
        'User-Agent': 'NurseScheduling/1.0'
      }
    };

    const req = http.request(options, (res) => {
      let data = '';

      res.on('data', (chunk) => {
        data += chunk;
      });

      res.on('end', () => {
        try {
          const parsed = JSON.parse(data);
          if (parsed.length > 0) {
            const location = parsed[0];
            resolve({
              address: address,
              coordinates: {
                lat: parseFloat(location.lat),
                lon: parseFloat(location.lon)
              },
              display_name: location.display_name
            });
          } else {
            reject(new Error(`No results found for address: ${address}`));
          }
        } catch (e) {
          reject(new Error(`Error parsing response for ${address}: ${e.message}`));
        }
      });
    });

    req.on('error', (e) => {
      reject(new Error(`Problem with request for ${address}: ${e.message}`));
    });

    req.end();
  });
}

// Function to get route using GraphHopper
function getRoute(points, profile = 'car') {
  return new Promise((resolve, reject) => {
    // Construct the URL with all points
    let url = `http://localhost:8989/route?profile=${profile}`;
    
    // Add each point to the URL
    points.forEach(point => {
      url += `&point=${point.lat},${point.lon}`;
    });

    // Optional parameters
    url += '&instructions=true&calc_points=true';

    const req = http.request(url, (res) => {
      let data = '';

      res.on('data', (chunk) => {
        data += chunk;
      });

      res.on('end', () => {
        try {
          const result = JSON.parse(data);
          resolve(result);
        } catch (e) {
          reject(new Error(`Error parsing route response: ${e.message}`));
        }
      });
    });

    req.on('error', (e) => {
      reject(new Error(`Problem with route request: ${e.message}`));
    });

    req.end();
  });
}

// Main execution flow
async function main() {
  try {
    // Geocode all addresses
    const geocodedLocations = await Promise.all(
      addresses.map(address => geocodeAddress(address))
    );
    
    console.log('\nAll addresses geocoded successfully!');
    
    // Extract coordinates for GraphHopper
    const points = geocodedLocations.map(location => location.coordinates);
    
    console.log('\nCalculating optimal route...');
    
    // Get route from GraphHopper
    const route = await getRoute(points);
    
    // Display results
    if (route.paths && route.paths.length > 0) {
      const path = route.paths[0];
      
      console.log('\n--- ROUTE DETAILS ---');
      console.log(`Total distance: ${(path.distance/1000).toFixed(2)} km`);
      console.log(`Estimated time: ${Math.round(path.time/60000)} minutes`);
      
      console.log('\n--- TURN-BY-TURN INSTRUCTIONS ---');
      if (path.instructions) {
        path.instructions.forEach((instruction, i) => {
          console.log(`${i+1}. ${instruction.text} - ${(instruction.distance).toFixed(0)}m`);
        });
      }
    } else {
      console.log('No route found or error in route calculation');
      console.log(JSON.stringify(route, null, 2));
    }
  } catch (error) {
    console.error('Error:', error.message);
  }
}

main(); 