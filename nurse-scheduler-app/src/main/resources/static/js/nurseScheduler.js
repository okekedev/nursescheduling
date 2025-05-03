function nurseScheduler() {
  return {
    nurses: [],
    selectedNurseId: null,
    patients: [],
    selectedPatientIds: [],
    map: null,
    nurseLayer: null,
    patientLayer: null,
    routeLayer: null,

    async init() {
      if (this.map) return;
      this.map = L.map("map").setView([31.0, -99.0], 6);
      L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
        attribution:
          '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      }).addTo(this.map);
      this.routeLayer = L.layerGroup().addTo(this.map);
      await this.fetchSchedules();
      this.updateMap();
    },

    async fetchSchedules() {
      try {
        const response = await fetch(
          `/api/schedule${
            this.selectedNurseId ? "?workerId=" + this.selectedNurseId : ""
          }`
        );
        const data = await response.json();
        if (data.success) {
          this.nurses = data.schedules.map((s) => s.nurse);
          this.patients = data.schedules.flatMap((s) => s.patients);
          this.selectedPatientIds = this.patients.map((p) => p.patientId);
          if (this.nurses.length > 0 && !this.selectedNurseId) {
            this.selectedNurseId = this.nurses[0].workerId;
          }
          this.updateRoute(data.schedules);
        } else {
          console.error("Error fetching schedules:", data.error);
        }
      } catch (error) {
        console.error("Error fetching schedules:", error);
      }
    },

    updateRoute(schedules) {
      if (this.routeLayer) this.routeLayer.clearLayers();
      const schedule = schedules.find(
        (s) => s.nurse.workerId === this.selectedNurseId
      );
      if (schedule && schedule.route.coordinates.length > 0) {
        const latlngs = schedule.route.coordinates.map((coord) => [
          coord[0],
          coord[1],
        ]);
        L.polyline(latlngs, { color: "blue" }).addTo(this.routeLayer);
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
      await this.fetchSchedules();
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
  };
}
