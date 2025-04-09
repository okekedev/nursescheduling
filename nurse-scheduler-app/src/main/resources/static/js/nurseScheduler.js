function nurseScheduler() {
    return {
        nurse: null,
        patients: [],
        map: null,
        routeLayer: null,
        totalDistance: 0,

        async init() {
            // Prevent reinitialization of the map
            if (this.map) {
                return;
            }

            // Initialize the Leaflet map
            this.map = L.map('map').setView([31.0, -99.0], 6); // Default to central Texas if nurse data isn't available
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            }).addTo(this.map);

            // Fetch nurse and patients data
            await this.fetchData();

            // Set map view to nurse's location if available
            if (this.nurse && this.nurse.latitude && this.nurse.longitude) {
                this.map.setView([this.nurse.latitude, this.nurse.longitude], 10);
                L.marker([this.nurse.latitude, this.nurse.longitude])
                    .addTo(this.map)
                    .bindPopup(this.nurse.name || 'Nurse')
                    .openPopup();
            }

            // Add patient markers if available
            if (this.patients && Array.isArray(this.patients)) {
                this.patients.forEach(patient => {
                    if (patient.latitude && patient.longitude) {
                        L.marker([patient.latitude, patient.longitude])
                            .addTo(this.map)
                            .bindPopup(patient.name || 'Patient');
                    }
                });
            }
        },

        async fetchData() {
            try {
                // Fetch nurse data
                const nurseResponse = await fetch('/api/nurse');
                const nurseData = await nurseResponse.json();
                if (nurseData.success) {
                    this.nurse = nurseData.nurse;
                } else {
                    console.error('Error fetching nurse:', nurseData.error);
                }

                // Fetch patients data
                const patientsResponse = await fetch('/api/patients');
                const patientsData = await patientsResponse.json();
                if (patientsData.success) {
                    this.patients = patientsData.patients || [];
                } else {
                    console.error('Error fetching patients:', patientsData.error);
                }
            } catch (error) {
                console.error('Error fetching data:', error);
            }
        },

        async calculateRoute() {
            if (!this.nurse || !this.patients || !Array.isArray(this.patients)) {
                console.error('Cannot calculate route: Nurse or patients data is missing');
                return;
            }

            if (this.routeLayer) {
                this.map.removeLayer(this.routeLayer);
            }

            const points = [
                [this.nurse.latitude, this.nurse.longitude],
                ...this.patients.map(p => [p.latitude, p.longitude])
            ];

            try {
                const response = await fetch('/api/route', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(points)
                });
                const data = await response.json();

                if (data.success) {
                    this.routeLayer = L.polyline(data.coordinates, {
                        color: 'blue',
                        weight: 4,
                        opacity: 0.7
                    }).addTo(this.map);

                    this.totalDistance = data.distance; // In meters
                    this.map.fitBounds(this.routeLayer.getBounds(), { padding: [50, 50] });
                } else {
                    console.error('Error calculating route:', data.error);
                }
            } catch (error) {
                console.error('Error fetching route from backend:', error);
            }
        }
    };
}