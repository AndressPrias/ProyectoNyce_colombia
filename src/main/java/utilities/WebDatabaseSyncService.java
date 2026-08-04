package utilities;

import db.Database;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WebDatabaseSyncService {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static ScheduledExecutorService executor;
    private static volatile String lastSuccessfulSignature = "";

    private WebDatabaseSyncService() {}

    public static void start() {
        AppConfig.WebSyncSettings settings = AppConfig.getWebSyncSettings();
        if (!settings.isConfigured() || !STARTED.compareAndSet(false, true)) {
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "lenc-web-database-sync");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(
                () -> synchronizeSafely(settings),
                15,
                Math.max(60L, settings.intervalMinutes() * 60L),
                TimeUnit.SECONDS
        );
    }

    public static void stop() {
        ScheduledExecutorService current = executor;
        if (current != null) {
            current.shutdownNow();
        }
        executor = null;
        STARTED.set(false);
    }

    private static void synchronizeSafely(AppConfig.WebSyncSettings settings) {
        try {
            Path databasePath = AppConfig.getDatabasePath();
            if (!Files.isRegularFile(databasePath)) {
                return;
            }

            String currentSignature = databaseSignature(databasePath);
            if (currentSignature.equals(lastSuccessfulSignature)) {
                return;
            }

            Path snapshot = createConsistentSnapshot();
            try {
                uploadSnapshot(snapshot, settings);
                lastSuccessfulSignature = currentSignature;
                System.out.println("Copia web de la base de datos sincronizada.");
            } finally {
                Files.deleteIfExists(snapshot);
            }
        } catch (Exception e) {
            System.err.println("No fue posible sincronizar la copia web: " + e.getMessage());
        }
    }

    private static Path createConsistentSnapshot() throws Exception {
        Path snapshot = Files.createTempFile(
                AppConfig.getStorageFolder(), "lenc-web-snapshot-", ".db");
        Files.deleteIfExists(snapshot);

        String sqlitePath = snapshot.toAbsolutePath().normalize().toString().replace("'", "''");
        try (Connection connection = Database.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("VACUUM INTO '" + sqlitePath + "'");
        }
        if (!Files.isRegularFile(snapshot) || Files.size(snapshot) < 100) {
            throw new IOException("No se pudo generar una copia consistente de SQLite.");
        }
        return snapshot;
    }

    private static void uploadSnapshot(
            Path snapshot, AppConfig.WebSyncSettings settings) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(settings.url()))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/octet-stream")
                .header("X-Sync-Token", settings.token())
                .POST(HttpRequest.BodyPublishers.ofFile(snapshot))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(
                request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("El servidor web respondió con estado " + response.statusCode() + ".");
        }
    }

    private static String databaseSignature(Path databasePath) throws IOException {
        long modified = Files.getLastModifiedTime(databasePath).toMillis();
        long size = Files.size(databasePath);
        Path walPath = Path.of(databasePath.toString() + "-wal");
        long walModified = Files.exists(walPath)
                ? Files.getLastModifiedTime(walPath).toMillis() : 0;
        long walSize = Files.exists(walPath) ? Files.size(walPath) : 0;
        return modified + ":" + size + ":" + walModified + ":" + walSize;
    }
}
