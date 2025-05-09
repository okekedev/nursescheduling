package nursescheduler.utility;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility for pre-downloading map tiles for offline use
 * Downloads tiles from a tile server and caches them locally
 */
public class TileDownloader {

    private final String tileServerUrl;
    private final String outputDirectory;
    private final int threadCount;
    private final AtomicInteger downloadedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    
    /**
     * Constructor
     * 
     * @param tileServerUrl The URL template for the tile server, with {z}, {x}, {y} placeholders
     * @param outputDirectory The directory to save tiles to
     * @param threadCount Number of concurrent download threads
     */
    public TileDownloader(String tileServerUrl, String outputDirectory, int threadCount) {
        this.tileServerUrl = tileServerUrl;
        this.outputDirectory = outputDirectory;
        this.threadCount = threadCount;
        
        // Create output directory if it doesn't exist
        File outputDir = new File(outputDirectory);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
    }
    
    /**
     * Download tiles for a specified area and zoom levels
     * 
     * @param minLat Minimum latitude (southern boundary)
     * @param maxLat Maximum latitude (northern boundary)
     * @param minLon Minimum longitude (western boundary)
     * @param maxLon Maximum longitude (eastern boundary)
     * @param minZoom Minimum zoom level
     * @param maxZoom Maximum zoom level
     */
    public void downloadTiles(
            double minLat, double maxLat, 
            double minLon, double maxLon, 
            int minZoom, int maxZoom) {
        
        System.out.println("Starting tile download...");
        System.out.println("Area: " + minLat + "," + minLon + " to " + maxLat + "," + maxLon);
        System.out.println("Zoom levels: " + minZoom + " to " + maxZoom);
        
        // Create thread pool
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // For each zoom level
        for (int z = minZoom; z <= maxZoom; z++) {
            final int zoom = z;
            
            // Calculate tile ranges for this zoom level
            int x1 = lonToTileX(minLon, zoom);
            int x2 = lonToTileX(maxLon, zoom);
            int y1 = latToTileY(maxLat, zoom); // Note: y is inverted in tile coordinates
            int y2 = latToTileY(minLat, zoom);
            
            int tilesAtZoom = (x2 - x1 + 1) * (y2 - y1 + 1);
            System.out.println("Zoom level " + zoom + ": " + tilesAtZoom + " tiles to download");
            
            // Download each tile
            for (int x = x1; x <= x2; x++) {
                for (int y = y1; y <= y2; y++) {
                    final int tileX = x;
                    final int tileY = y;
                    
                    executor.submit(() -> downloadTile(zoom, tileX, tileY));
                }
            }
        }
        
        // Shutdown executor and wait for completion
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            System.err.println("Download interrupted: " + e.getMessage());
        }
        
        System.out.println("Download complete!");
        System.out.println("Downloaded: " + downloadedCount.get() + " tiles");
        System.out.println("Failed: " + failedCount.get() + " tiles");
    }
    
    /**
     * Download a single tile
     */
    private void downloadTile(int z, int x, int y) {
        try {
            // Create directory structure
            String tilePath = String.format("%s/%d/%d", outputDirectory, z, x);
            Path directory = Paths.get(tilePath);
            Files.createDirectories(directory);
            
            // Check if tile already exists
            Path tileFile = Paths.get(tilePath + "/" + y + ".png");
            if (Files.exists(tileFile)) {
                return; // Skip if already downloaded
            }
            
            // Construct URL
            String url = tileServerUrl
                    .replace("{z}", String.valueOf(z))
                    .replace("{x}", String.valueOf(x))
                    .replace("{y}", String.valueOf(y));
            
            // Download tile
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Save tile
                Files.write(tileFile, response.getBody());
                downloadedCount.incrementAndGet();
                
                // Print progress occasionally
                int count = downloadedCount.get();
                if (count % 100 == 0) {
                    System.out.println("Downloaded " + count + " tiles...");
                }
            } else {
                failedCount.incrementAndGet();
            }
            
            // Be nice to the server - add a small delay
            Thread.sleep(50);
            
        } catch (IOException | InterruptedException e) {
            failedCount.incrementAndGet();
            System.err.println("Error downloading tile " + z + "/" + x + "/" + y + ": " + e.getMessage());
        }
    }
    
    /**
     * Convert longitude to tile X coordinate
     */
    private static int lonToTileX(double lon, int zoom) {
        return (int) Math.floor((lon + 180) / 360 * (1 << zoom));
    }
    
    /**
     * Convert latitude to tile Y coordinate
     */
    private static int latToTileY(double lat, int zoom) {
        double latRad = Math.toRadians(lat);
        return (int) Math.floor((1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2 * (1 << zoom));
    }
    
    /**
     * Main method for testing and CLI usage
     */
    public static void main(String[] args) {
        // Default values for Austin, TX area
        double minLat = 30.15;
        double maxLat = 30.35;
        double minLon = -97.85;
        double maxLon = -97.65;
        int minZoom = 10;
        int maxZoom = 16;
        String tileUrl = "http://localhost:8081/data/texas/{z}/{x}/{y}.png";
        String outputDir = "./tile-cache";
        int threads = 4;
        
        // Parse command line arguments if provided
        if (args.length >= 8) {
            try {
                minLat = Double.parseDouble(args[0]);
                maxLat = Double.parseDouble(args[1]);
                minLon = Double.parseDouble(args[2]);
                maxLon = Double.parseDouble(args[3]);
                minZoom = Integer.parseInt(args[4]);
                maxZoom = Integer.parseInt(args[5]);
                tileUrl = args[6];
                outputDir = args[7];
                
                if (args.length >= 9) {
                    threads = Integer.parseInt(args[8]);
                }
            } catch (NumberFormatException e) {
                System.err.println("Error parsing arguments: " + e.getMessage());
                printUsage();
                return;
            }
        }
        
        // Create downloader and start download
        TileDownloader downloader = new TileDownloader(tileUrl, outputDir, threads);
        downloader.downloadTiles(minLat, maxLat, minLon, maxLon, minZoom, maxZoom);
    }
    
    /**
     * Print usage instructions
     */
    private static void printUsage() {
        System.out.println("Usage: TileDownloader [minLat maxLat minLon maxLon minZoom maxZoom tileUrl outputDir threads]");
        System.out.println("  If no arguments are provided, defaults to Austin, TX area.");
    }
}