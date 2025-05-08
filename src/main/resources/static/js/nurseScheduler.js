function nurseScheduler() {
    return {
      nurses: [],
      fieldNurses: [],
      selectedNurseId: "",
      nurse: null,
      patients: [],
      map: null,
      nurseMarker: null,
      patientMarkers: [],
      routeLayer: null,
      totalDistance: 0,
      loading: false,
      error: null,
      mapInitialized: false,
      tileRetryAttempted: false,
  
      async init() {
        console.log("Initializing nurse scheduler...");
        this.loading = true;
  
        try {
          // Initialize the map once
          if (!this.mapInitialized) {
            this.initializeMap();
          }
  
          // Load nurses
          await this.fetchNurses();
  
          // Select a nurse if available
          if (this.fieldNurses.length > 0) {
            this.selectedNurseId = this.fieldNurses[0].id;
            await this.selectNurse();
          } else if (this.nurses.length > 0) {
            this.selectedNurseId = this.nurses[0].id;
            await this.selectNurse();
          } else {
            // Create test data if no nurses found
            const createResponse = await fetch("/api/test/create-nurse", { method: "POST" });
            if (createResponse.ok) {
              await this.fetchNurses();
              if (this.nurses.length > 0) {
                this.selectedNurseId = this.nurses[0].id;
                await this.selectNurse();
              }
            }
          }
        } catch (err) {
          console.error("Error initializing:", err);
          this.error = "Error initializing: " + err.message;
        } finally {
          this.loading = false;
        }
      },
  
      initializeMap() {
        try {
          const mapElement = document.getElementById("map");
          if (!mapElement) {
            console.error("Map element not found!");
            return;
          }
          
          // Ensure map container has proper dimensions
          if (mapElement.offsetWidth === 0 || mapElement.offsetHeight === 0) {
            mapElement.style.height = '500px';
            mapElement.style.width = '100%';
          }
          
          // Initialize map with better default options for smooth animations
          this.map = L.map("map", {
            center: [33.9383, -98.5329], // Wichita Falls area
            zoom: 10,
            minZoom: 3,  // Allow zooming out to see wider area
            maxZoom: 18, // Maximum zoom for street-level detail
            zoomControl: true,
            scrollWheelZoom: true,
            doubleClickZoom: true,
            zoomAnimation: true,
            fadeAnimation: true,
            markerZoomAnimation: true
          });
          
          // Try GraphHopper tile server first
          var self = this;
          var tileLayer = L.tileLayer("http://localhost:8989/maps/tile/{z}/{x}/{y}.png", {
            attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
            maxZoom: 18,
            tileSize: 256
          });
          
          // Handle tile errors - fallback to OSM if GraphHopper tiles fail
          tileLayer.on('tileerror', function() {
            if (!self.tileRetryAttempted) {
              self.tileRetryAttempted = true;
              self.map.removeLayer(tileLayer);
              
              // Fallback to OpenStreetMap tiles
              L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
                attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
                maxZoom: 18
              }).addTo(self.map);
            }
          });
          
          tileLayer.addTo(this.map);
          this.mapInitialized = true;
  
          // Force a map refresh when container size changes
          window.addEventListener('resize', function() {
            if (self.map) {
              self.map.invalidateSize();
            }
          });
        } catch (error) {
          console.error("Error initializing map:", error);
          this.error = "Failed to initialize map: " + error.message;
        }
      },
  
      async fetchNurses() {
        try {
          const response = await fetch("/api/nurses");
          const data = await response.json();
  
          if (data.success) {
            this.nurses = (data.nurses || []).sort(function(a, b) {
              return a.name.localeCompare(b.name);
            });
            
            // Filter for nurses with coordinates
            this.fieldNurses = this.nurses.filter(function(nurse) {
              return nurse.latitude != null && 
                     nurse.longitude != null && 
                     (nurse.fieldStaff === undefined || nurse.fieldStaff === true);
            });
            
            console.log("Loaded", this.nurses.length, "nurses,", this.fieldNurses.length, "with coordinates");
          } else {
            console.error("Error fetching nurses:", data.error);
            this.error = "Error loading nurses: " + data.error;
          }
        } catch (error) {
          console.error("Error fetching nurse list:", error);
          this.error = "Error loading nurses: " + error.message;
        }
      },
  
      async selectNurse() {
        if (!this.selectedNurseId) {
          this.nurse = null;
          this.patients = [];
          this.clearMapMarkers();
          return;
        }
  
        this.loading = true;
        this.error = null;
  
        try {
          // Fetch nurse data
          const nurseResponse = await fetch("/api/nurse?id=" + this.selectedNurseId);
          const nurseData = await nurseResponse.json();
  
          if (nurseData.success) {
            this.nurse = nurseData.nurse;
            
            // First ensure the map is visible and properly sized
            this.ensureMapIsVisible();
            
            // Update map
            this.clearMapMarkers(); // Clear previous markers first
            this.updateNurseMarker();
            await this.fetchPatients();
            
            // Reset route calculation
            if (this.routeLayer) {
              this.map.removeLayer(this.routeLayer);
              this.routeLayer = null;
            }
            this.totalDistance = 0;
            
            // Get or create schedule
            await this.fetchOrCreateSchedule();
          } else {
            this.error = "Error loading nurse data: " + nurseData.error;
          }
        } catch (error) {
          this.error = "Error selecting nurse: " + error.message;
        } finally {
          this.loading = false;
        }
      },
  
      // Make sure the map container is visible and properly sized
      ensureMapIsVisible() {
        // Force a map redraw to fix disappearing issues
        if (this.map) {
          setTimeout(() => {
            this.map.invalidateSize();
            
            // Reset the view if needed
            if (!this.nurse || !this.nurse.latitude) {
              this.map.setView([33.9383, -98.5329], 10);
            }
          }, 100);
        } else {
          // If map somehow got destroyed, reinitialize it
          this.initializeMap();
        }
      },
  
      async fetchOrCreateSchedule() {
        try {
          const today = new Date().toISOString().split("T")[0];
          const response = await fetch("/api/schedule?nurseId=" + this.selectedNurseId + "&date=" + today);
          const data = await response.json();
  
          if (data.success) {
            this.totalDistance = data.schedule.totalDistance || 0;
  
            // Display route if available
            if (data.schedule.routeCoordinates && data.schedule.routeCoordinates !== "[]") {
              try {
                const coordinates = JSON.parse(data.schedule.routeCoordinates);
                this.displayRoute(coordinates);
              } catch (e) {
                console.error("Error parsing route coordinates:", e);
              }
            }
          } else {
            // Generate new schedule if not found
            await this.generateSchedule();
          }
        } catch (error) {
          this.error = "Error loading schedule: " + error.message;
        }
      },
  
      async generateSchedule() {
        try {
          const today = new Date().toISOString().split("T")[0];
          const response = await fetch(
            "/api/schedule/generate?nurseId=" + this.selectedNurseId + "&date=" + today,
            { method: "POST" }
          );
          
          if (response.ok) {
            await this.fetchOrCreateSchedule();
          } else {
            const data = await response.json();
            this.error = "Error generating schedule: " + (data.error || "Unknown error");
          }
        } catch (error) {
          this.error = "Error generating schedule: " + error.message;
        }
      },
  
      updateNurseMarker() {
        // Clear existing marker
        if (this.nurseMarker) {
          this.map.removeLayer(this.nurseMarker);
          this.nurseMarker = null;
        }
  
        // Add nurse marker if coordinates exist
        if (this.nurse && this.nurse.latitude != null && this.nurse.longitude != null) {
          const nurseIcon = L.divIcon({
            className: "nurse-marker",
            html: '<div style="background-color: #4c86f9; border-radius: 50%; width: 14px; height: 14px; border: 2px solid white;"></div>',
            iconSize: [18, 18],
            iconAnchor: [9, 9],
          });
  
          this.nurseMarker = L.marker(
            [this.nurse.latitude, this.nurse.longitude],
            { icon: nurseIcon }
          )
            .addTo(this.map)
            .bindPopup("<strong>" + this.nurse.name + "</strong><br>Starting Location");
  
          // Center map on nurse position temporarily
          this.map.setView([this.nurse.latitude, this.nurse.longitude], 10);
        } else {
          // No coordinates for this nurse
          console.log("Nurse has no coordinates, skipping marker");
        }
      },
  
      async fetchPatients() {
        this.clearPatientMarkers();
  
        try {
          const patientsResponse = await fetch("/api/patients?nurseId=" + this.selectedNurseId + "&limit=30");
          const patientsData = await patientsResponse.json();
  
          if (patientsData.success) {
            this.patients = patientsData.patients || [];
            this.addPatientMarkers();
          } else {
            this.error = "Error loading patients: " + patientsData.error;
          }
        } catch (error) {
          this.error = "Error loading patients: " + error.message;
        }
      },
  
      clearMapMarkers() {
        // Clear all markers
        this.clearPatientMarkers();
        
        if (this.nurseMarker) {
          this.map.removeLayer(this.nurseMarker);
          this.nurseMarker = null;
        }
        
        if (this.routeLayer) {
          this.map.removeLayer(this.routeLayer);
          this.routeLayer = null;
        }
      },
  
      clearPatientMarkers() {
        // Remove patient markers
        var self = this;
        if (this.patientMarkers && this.patientMarkers.length) {
          this.patientMarkers.forEach(function(marker) {
            self.map.removeLayer(marker);
          });
        }
        this.patientMarkers = [];
      },
  
      addPatientMarkers() {
        // Create patient icon
        const patientIcon = L.divIcon({
          className: "patient-marker",
          html: '<div style="background-color: #f94c4c; border-radius: 50%; width: 10px; height: 10px; border: 2px solid white;"></div>',
          iconSize: [14, 14],
          iconAnchor: [7, 7],
        });
  
        // Filter patients with valid coordinates
        const validPatients = this.patients.filter(function(patient) { 
          return patient.latitude != null && patient.longitude != null;
        });
  
        // Add patient markers
        var self = this;
        validPatients.forEach(function(patient) {
          const marker = L.marker([patient.latitude, patient.longitude], {
            icon: patientIcon,
          }).addTo(self.map).bindPopup(
            "<strong>" + patient.name + "</strong><br>" +
            patient.address + "<br>" +
            "Visit Time: " + patient.time + "<br>" +
            "Duration: " + patient.duration + " min"
          );
  
          self.patientMarkers.push(marker);
        });
  
        // Fit map to show all markers
        this.fitMapToAllMarkers();
  
        // Warning for patients without coordinates
        const missingCoordinates = this.patients.length - validPatients.length;
        if (missingCoordinates > 0) {
          this.error = "Warning: " + missingCoordinates + " patient(s) cannot be shown on the map due to missing coordinates.";
        }
      },
  
      fitMapToAllMarkers() {
        var markers = [];
        
        // Add all markers to the array
        if (this.nurseMarker) {
          markers.push(this.nurseMarker);
        }
        
        if (this.patientMarkers.length) {
          markers = markers.concat(this.patientMarkers);
        }
        
        // If we have markers, fit the map to show them all
        if (markers.length > 0) {
          var group = new L.featureGroup(markers);
          var bounds = group.getBounds();
          
          // Add padding around the bounds to ensure markers aren't at the edge
          this.map.flyToBounds(bounds, {
            padding: [50, 50],
            maxZoom: 14, // Limit zoom level for better overview
            duration: 0.5, // Faster animation for better UX
            easeLinearity: 0.5 // Smoother animation curve
          });
        } else if (this.nurse && this.nurse.latitude && this.nurse.longitude) {
          // If no markers but we have nurse coordinates, center on nurse
          this.map.flyTo([this.nurse.latitude, this.nurse.longitude], 10, {
            duration: 0.5,
            easeLinearity: 0.5
          });
        }
      },
  
      async calculateRoute() {
        // Validate data
        if (!this.nurse || this.nurse.latitude == null || this.nurse.longitude == null) {
          this.error = "Cannot calculate route: Nurse has no coordinates";
          return;
        }
  
        // Filter patients with coordinates
        const validPatients = this.patients.filter(function(p) { 
          return p.latitude != null && p.longitude != null; 
        });
  
        if (validPatients.length === 0) {
          this.error = "Cannot calculate route: No patients have coordinates";
          return;
        }
  
        this.loading = true;
        this.error = null;
  
        try {
          // Clear existing route
          if (this.routeLayer) {
            this.map.removeLayer(this.routeLayer);
          }
  
          // Prepare points data
          const points = [
            [this.nurse.latitude, this.nurse.longitude]
          ];
          
          validPatients.forEach(function(p) {
            points.push([p.latitude, p.longitude]);
          });
  
          // Call API to calculate route
          const response = await fetch("/api/route", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(points),
          });
          
          const data = await response.json();
  
          if (data.success) {
            this.displayRoute(data.coordinates);
            this.totalDistance = data.distance;
            
            // Warning for excluded patients
            const missingCoordinates = this.patients.length - validPatients.length;
            if (missingCoordinates > 0) {
              this.error = "Route calculated, but " + missingCoordinates + " patient(s) were excluded due to missing coordinates.";
            }
          } else {
            this.error = "Error calculating route: " + data.error;
          }
        } catch (error) {
          this.error = "Error calculating route: " + error.message;
        } finally {
          this.loading = false;
        }
      },
  
      displayRoute(coordinates) {
        // Remove old route
        if (this.routeLayer) {
          this.map.removeLayer(this.routeLayer);
        }
  
        // Make sure the map is visible first
        this.ensureMapIsVisible();
  
        // Add new route polyline
        this.routeLayer = L.polyline(coordinates, {
          color: "#4c86f9",
          weight: 4,
          opacity: 0.7,
          smoothFactor: 1, // Simplify line for better performance
        }).addTo(this.map);
  
        // Fit map to show entire route with smooth animation
        this.map.flyToBounds(this.routeLayer.getBounds(), {
          padding: [50, 50],
          maxZoom: 14, // Prevent excessive zooming
          duration: 0.7, // Animation duration in seconds
          easeLinearity: 0.5 // Smooth easing
        });
      },
  
      // Calculate estimated drive time based on distance
      calculateDriveTime(distance) {
        const hours = distance / 1000 / 40; // Assume 40 km/h average speed
        const minutes = Math.round(hours * 60);
  
        if (minutes < 60) {
          return minutes + " min";
        } else {
          const hrs = Math.floor(minutes / 60);
          const mins = minutes % 60;
          return hrs + " hr " + mins + " min";
        }
      },
  
      // Calculate total visit time from all patients
      calculateTotalVisitTime() {
        if (!this.patients || !this.patients.length) {
          return 0;
        }
  
        return this.patients.reduce(function(total, patient) {
          return total + (patient.duration || 0);
        }, 0);
      },
  
      // Calculate total work time (driving + visits)
      calculateTotalWorkTime() {
        const driveTimeMinutes = (this.totalDistance / 1000 / 40) * 60;
        const visitTimeMinutes = this.calculateTotalVisitTime();
        const totalMinutes = Math.round(driveTimeMinutes + visitTimeMinutes);
  
        if (totalMinutes < 60) {
          return totalMinutes + " min";
        } else {
          const hrs = Math.floor(totalMinutes / 60);
          const mins = totalMinutes % 60;
          return hrs + " hr " + mins + " min";
        }
      }
    };
  }