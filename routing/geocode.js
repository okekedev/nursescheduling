#!/usr/bin/env node

// Simple geocoding script using local Nominatim
const http = require('http');
const querystring = require('querystring');

// Get the address from command line arguments
const address = process.argv.slice(2).join(' ');

if (!address) {
  console.error('Please provide an address to geocode');
  console.error('Usage: node geocode.js "402 Morningside Dr Wichita Falls tx 76301"');
  process.exit(1);
}

// Build the query parameters
const params = querystring.stringify({
  q: address,
  format: 'json',
  limit: 1
});

// Configure the request options - using local Nominatim server
const options = {
  hostname: 'localhost',
  port: 8080,
  path: `/search?${params}`,
  method: 'GET',
  headers: {
    'User-Agent': 'NurseScheduling/1.0'
  }
};

// Make the request
const req = http.request(options, (res) => {
  let data = '';

  // A chunk of data has been received
  res.on('data', (chunk) => {
    data += chunk;
  });

  // The whole response has been received
  res.on('end', () => {
    try {
      const parsed = JSON.parse(data);
      if (parsed.length > 0) {
        const location = parsed[0];
        console.log(JSON.stringify({
          address: address,
          coordinates: {
            lat: parseFloat(location.lat),
            lon: parseFloat(location.lon)
          },
          display_name: location.display_name
        }, null, 2));
      } else {
        console.error('No results found for the given address');
      }
    } catch (e) {
      console.error('Error parsing response:', e.message);
    }
  });
});

req.on('error', (e) => {
  console.error(`Problem with request: ${e.message}`);
});

req.end(); 