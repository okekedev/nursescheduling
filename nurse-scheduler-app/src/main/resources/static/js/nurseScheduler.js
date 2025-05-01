function nurseScheduler() {
  return {
    nurses: [],
    selectedNurseId: null,
    patients: [],
    selectedPatientIds: [],
    map: null,
    nurseLayer: null,
    patientLayer: null,

    async init() {
      if (this.map) return;

      this.map = L.map("map").setView([31.0, -99.0], 6);
      L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
        attribution:
          '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      }).addTo(this.map);

      await this.fetchData();
      this.updateMap();
    },

    async fetchData() {
      try {
        const nurseResponse = await fetch("/api/nurses");
        const nurseData = await nurseResponse.json();
        if (nurseData.success) {
          this.nurses = nurseData.nurses || [];
          if (this.nurses.length > 0) {
            this.selectedNurseId = this.nurses[0].workerId;
          }
        } else {
          console.error("Error fetching nurses:", nurseData.error);
        }

        await this.fetchPatients();
      } catch (error) {
        console.error("Error fetching data:", error);
      }
    },

    async fetchPatients() {
      try {
        const patientsResponse = await fetch(
          `/api/patients${
            this.selectedNurseId ? "?workerId=" + this.selectedNurseId : ""
          }`
        );
        const patientsData = await patientsResponse.json();
        if (patientsData.success) {
          this.patients = patientsData.patients || [];
          this.selectedPatientIds = this.patients.map((p) => p.patientId);
        } else {
          console.error("Error fetching patients:", patientsData.error);
        }
        this.updateMap();
      } catch (error) {
        console.error("Error fetching patients:", error);
      }
    },

    updateMap() {
      if (this.nurseLayer) this.map.removeLayer(this.nurseLayer);
      if (this.patientLayer) this.map.removeLayer(this.patientLayer);

      this.nurseLayer = L.layerGroup().addTo(this.map);
      this.patientLayer = L.layerGroup().addTo(this.map);

      const selectedNurse = this.nurses.find(
        (n) => n.workerId === this.selectedNurseId
      );
      if (selectedNurse && selectedNurse.coordinates) {
        L.marker([
          selectedNurse.coordinates.latitude,
          selectedNurse.coordinates.longitude,
        ])
          .addTo(this.nurseLayer)
          .bindPopup(`${selectedNurse.firstName} ${selectedNurse.lastName}`)
          .openPopup();
        this.map.setView(
          [
            selectedNurse.coordinates.latitude,
            selectedNurse.coordinates.longitude,
          ],
          10
        );
      }

      this.patients.forEach((patient) => {
        if (
          patient.latitude &&
          patient.longitude &&
          this.selectedPatientIds.includes(patient.patientId)
        ) {
          L.marker([patient.latitude, patient.longitude])
            .addTo(this.patientLayer)
            .bindPopup(patient.name);
        }
      });

      if (
        this.nurseLayer.getLayers().length ||
        this.patientLayer.getLayers().length
      ) {
        this.map.fitBounds(
          L.featureGroup([
            ...this.nurseLayer.getLayers(),
            ...this.patientLayer.getLayers(),
          ]).getBounds(),
          { padding: [50, 50] }
        );
      }
    },

    async selectNurse(nurseId) {
      this.selectedNurseId = nurseId;
      this.selectedPatientIds = [];
      await this.fetchPatients();
    },

    togglePatient(patientId) {
      if (this.selectedPatientIds.includes(patientId)) {
        this.selectedPatientIds = this.selectedPatientIds.filter(
          (id) => id !== patientId
        );
      } else {
        this.selectedPatientIds.push(patientId);
      }
      this.updateMap();
    },

    getSelectedCoordinates() {
      const coordinates = [];
      const selectedNurse = this.nurses.find(
        (n) => n.workerId === this.selectedNurseId
      );
      if (selectedNurse && selectedNurse.coordinates) {
        coordinates.push([
          selectedNurse.coordinates.latitude,
          selectedNurse.coordinates.longitude,
        ]);
      }
      this.patients.forEach((patient) => {
        if (
          this.selectedPatientIds.includes(patient.patientId) &&
          patient.latitude &&
          patient.longitude
        ) {
          coordinates.push([patient.latitude, patient.longitude]);
        }
      });
      if (selectedNurse && selectedNurse.coordinates) {
        coordinates.push([
          selectedNurse.coordinates.latitude,
          selectedNurse.coordinates.longitude,
        ]);
      }
      return coordinates;
    },
  };
}
