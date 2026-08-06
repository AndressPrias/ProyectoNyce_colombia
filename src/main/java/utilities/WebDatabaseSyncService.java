package utilities;

import db.Database;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WebDatabaseSyncService {

    private static final int MAX_PENDING_PHOTOS_PER_RUN = 10;
    private static final int MAX_PHOTO_BYTES = 8 * 1024 * 1024;
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
            downloadPendingPhotos(settings);
        } catch (Exception e) {
            System.err.println("No fue posible descargar las fotos pendientes: " + e.getMessage());
        }

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

    private static void downloadPendingPhotos(
            AppConfig.WebSyncSettings settings) throws Exception {
        URI endpoint = URI.create(settings.url()).resolve("fotos-pendientes.php");

        for (int index = 0; index < MAX_PENDING_PHOTOS_PER_RUN; index++) {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofMinutes(1))
                    .header("X-Sync-Token", settings.token())
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 204) {
                return;
            }
            if (response.statusCode() != 200) {
                throw new IOException(
                        "El servidor respondió con estado " + response.statusCode() + ".");
            }

            String jobId = requiredHeader(response, "X-Photo-ID");
            String sampleCode = URLDecoder.decode(
                    requiredHeader(response, "X-Sample-Code"), StandardCharsets.UTF_8);
            String extension = requiredHeader(response, "X-Photo-Extension").toLowerCase();
            int sampleId = Integer.parseInt(requiredHeader(response, "X-Sample-ID"));
            byte[] imageBytes = response.body();

            if (!jobId.matches("[a-f0-9]{32}")
                    || !extension.matches("jpg|png|webp")
                    || sampleId < 1
                    || sampleCode.isBlank()
                    || imageBytes.length < 1
                    || imageBytes.length > MAX_PHOTO_BYTES) {
                throw new IOException("La foto pendiente no superó la validación.");
            }

            if (!sampleExists(sampleId, sampleCode)) {
                acknowledgePhoto(endpoint, settings.token(), jobId);
                continue;
            }

            Path temporaryPhoto = Files.createTempFile(
                    AppConfig.getStorageFolder(), "foto-movil-", "." + extension);
            try {
                Files.write(temporaryPhoto, imageBytes);
                String storedPath = ImageStorage.copySamplePhoto(
                        temporaryPhoto.toFile(), sampleCode);
                updateSamplePhoto(sampleId, sampleCode, storedPath);
                acknowledgePhoto(endpoint, settings.token(), jobId);
                System.out.println("Foto móvil sincronizada para la muestra " + sampleCode + ".");
            } finally {
                Files.deleteIfExists(temporaryPhoto);
            }
        }
    }

    private static String requiredHeader(
            HttpResponse<?> response, String headerName) throws IOException {
        return response.headers().firstValue(headerName)
                .orElseThrow(() -> new IOException(
                        "La respuesta no incluyó el encabezado " + headerName + "."));
    }

    private static boolean sampleExists(int sampleId, String sampleCode) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM muestras WHERE id=? AND codigoInterno=? LIMIT 1")) {
            statement.setInt(1, sampleId);
            statement.setString(2, sampleCode);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void updateSamplePhoto(
            int sampleId, String sampleCode, String storedPath) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE muestras SET rutaFoto=? WHERE id=? AND codigoInterno=?")) {
            statement.setString(1, storedPath);
            statement.setInt(2, sampleId);
            statement.setString(3, sampleCode);
            if (statement.executeUpdate() != 1) {
                throw new IOException("La muestra cambió antes de guardar la fotografía.");
            }
        }
    }

    private static void acknowledgePhoto(
            URI endpoint, String token, String jobId) throws Exception {
        URI acknowledgementUri = URI.create(
                endpoint + "?id=" + URLEncoder.encode(jobId, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(acknowledgementUri)
                .timeout(Duration.ofSeconds(30))
                .header("X-Sync-Token", token)
                .DELETE()
                .build();
        HttpResponse<Void> response = HTTP_CLIENT.send(
                request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() != 204) {
            throw new IOException(
                    "No fue posible confirmar la foto sincronizada ("
                            + response.statusCode() + ").");
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
