# Nurse Scheduler Application

This is a nurse scheduling application that allows a nurse to view their scheduled patient visits on a map and calculate an optimized route using GraphHopper. The frontend is built with Alpine.js and Leaflet, and the backend is a Spring Boot application using an H2 in-memory database.

## Features

- Display a map with markers for the nurse and patients.
- Fetch nurse and patient data from a database.
- Calculate an optimized route for the nurse to visit all patients and return to the starting location.
- Show the total distance of the route in kilometers.

## Prerequisites

Before running the application, ensure you have the following installed:

- **Java 17**: The project requires Java 17. You can download it from [Adoptium](https://adoptium.net/).
  - Verify with: `java -version`
  - Set the `JAVA_HOME` environment variable to point to your Java 17 installation (e.g., `/path/to/jdk-17`).
- **Maven**: Ensure Maven is installed. You can download it from [Apache Maven](https://maven.apache.org/download.cgi).
  - Verify with: `mvn -version`
  - Alternatively, use the included Maven Wrapper (`./mvnw` or `mvnw.cmd`) to avoid installing Maven globally.
- **texas-latest.osm.pbf**: The application uses an OpenStreetMap (OSM) file for Texas to calculate routes. This file is not included in the repository due to its size (approximately 1.5 GB).

## Setup and Running the Application

Follow these steps to set up and run the application on your local machine.

### Step 1: Clone the Repository

Clone the repository from GitHub to your local machine:

```bash
git clone https://github.com/your-username/nurse-scheduler-app.git
cd nurse-scheduler-app
```

Replace `your-username` with your actual GitHub username.

### Step 2: Install Java 17

Ensure Java 17 is installed on your system:

1. Check your Java version:
   ```bash
   java -version
   ```

2. If Java 17 is not installed, download and install it from [Adoptium](https://adoptium.net/).

3. Set the `JAVA_HOME` environment variable:
   - **On Windows**:
     - Open the System Properties window (right-click on "This PC" > Properties > Advanced system settings > Environment Variables).
     - Under "System variables", add a new variable:
       - Name: `JAVA_HOME`
       - Value: `C:\path\to\jdk-17` (replace with your Java 17 installation path).
     - Add `%JAVA_HOME%\bin` to the Path variable.
   - **On macOS/Linux**:
     ```bash
     export JAVA_HOME=/path/to/jdk-17
     export PATH=$JAVA_HOME/bin:$PATH
     ```
     - Add these lines to your shell configuration file (e.g., `~/.bashrc`, `~/.zshrc`) to make them permanent.

### Step 3: Install Maven (Optional)

The project includes the Maven Wrapper (`mvnw` and `mvnw.cmd`), so you can skip this step if you prefer to use the wrapper. If you want to use a global Maven installation:

1. Check if Maven is installed:
   ```bash
   mvn -version
   ```

2. If Maven is not installed, download it from [Apache Maven](https://maven.apache.org/download.cgi) and follow the installation instructions.

3. Add Maven to your PATH:
   - **On Windows**:
     - Add `C:\path\to\apache-maven-3.9.9\bin` to the Path environment variable (similar to the Java setup).
   - **On macOS/Linux**:
     ```bash
     export PATH=/path/to/apache-maven-3.9.9/bin:$PATH
     ```
     - Add this line to your shell configuration file (e.g., `~/.bashrc`, `~/.zshrc`).

### Step 4: Download the OSM File

The application requires the `texas-latest.osm.pbf` file for GraphHopper to calculate routes. This file is not included in the repository due to its size.

1. Download `texas-latest.osm.pbf` from [Geofabrik](https://download.geofabrik.de/north-america/us/texas.html).

2. Place the file in the `src/main/resources/graphhopper/` directory:
   ```bash
   mkdir -p src/main/resources/graphhopper
   mv /path/to/texas-latest.osm.pbf src/main/resources/graphhopper/
   ```

### Step 5: Run the Application

Run the application using Maven or the Maven Wrapper:

- Using Maven:
  ```bash
  mvn spring-boot:run
  ```

- Using the Maven Wrapper:
  ```bash
  ./mvnw spring-boot:run
  ```

The application will start on http://localhost:8080/. The first run may take some time as GraphHopper builds the graph from `texas-latest.osm.pbf`. Subsequent runs will be faster as the graph cache (`src/main/resources/graphhopper/graph-cache/`) is reused.

### Step 6: Access the Application

1. Open your browser and navigate to http://localhost:8080/.
2. The map should load with markers for the nurse (Sarah Johnson) and patients (John Doe, Jane Smith).
3. Click the "Calculate Route" button to display the optimized route and total distance.