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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WebDatabaseSyncService {

    private static final int MAX_PENDING_PHOTOS_PER_RUN = 10;
    private static final int MAX_PENDING_EDITS_PER_RUN = 20;
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
            downloadPendingEdits(settings);
        } catch (Exception e) {
            System.err.println("No fue posible aplicar los cambios web pendientes: " + e.getMessage());
        }

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

    private static void downloadPendingEdits(AppConfig.WebSyncSettings settings) throws Exception {
        URI endpoint = URI.create(settings.url()).resolve("cambios-pendientes.php");
        for (int index = 0; index < MAX_PENDING_EDITS_PER_RUN; index++) {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(30))
                    .header("X-Sync-Token", settings.token())
                    .GET().build();
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 204) return;
            if (response.statusCode() != 200) {
                throw new IOException("El servidor de cambios respondió con estado "
                        + response.statusCode() + ".");
            }

            Map<String, String> change = parseForm(response.body());
            String jobId = required(change, "id");
            if (!jobId.matches("[a-f0-9]{32}")) {
                throw new IOException("El cambio web no tiene un identificador válido.");
            }
            try {
                applyPendingEdit(change);
                System.out.println("Cambio web aplicado para la muestra "
                        + required(change, "sample_code") + ".");
            } catch (IllegalArgumentException rejected) {
                System.err.println("Cambio web rechazado: " + rejected.getMessage());
            }
            acknowledgeJob(endpoint, settings.token(), jobId, "cambio");
        }
    }

    private static void applyPendingEdit(Map<String, String> change) throws Exception {
        int sampleId = positiveInteger(required(change, "sample_id"), "muestra");
        int userId = positiveInteger(required(change, "user_id"), "usuario");
        String sampleCode = limited(required(change, "sample_code"), 500, true, "código");
        String userName = limited(required(change, "user_name"), 500, true, "usuario");
        int quantity = positiveInteger(required(change, "cantidad"), "cantidad");
        if (quantity > 1_000_000) throw new IllegalArgumentException("cantidad fuera de rango");
        String state = limited(required(change, "estado"), 50, true, "estado");
        Set<String> validStates = Set.of("EN_CUSTODIA", "ALMACENADO", "EN_CURSO",
                "LISTA_PARA_ALMACENAR", "LABORATORIO_EXTERNO",
                "REALIZAR_DISPOSICION_FINAL", "ENVIADO", "DESTRUCCION");
        if (!validStates.contains(state)) throw new IllegalArgumentException("estado no válido");
        String receptionDate = limited(required(change, "fechaRecepcion"), 10, true, "fecha");
        try { LocalDate.parse(receptionDate); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("fecha no válida"); }

        List<DocumentRef> reports = parseDocuments(change.getOrDefault("informes", ""), false);
        List<DocumentRef> quotations = parseDocuments(change.getOrDefault("cotizaciones", ""), true);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String priorState;
                String priorLocation;
                try (PreparedStatement lookup = connection.prepareStatement(
                        "SELECT estado, ubicacion FROM muestras WHERE id=? AND codigoInterno=? LIMIT 1")) {
                    lookup.setInt(1, sampleId);
                    lookup.setString(2, sampleCode);
                    try (ResultSet result = lookup.executeQuery()) {
                        if (!result.next()) throw new IllegalArgumentException("la muestra ya no existe");
                        priorState = result.getString("estado");
                        priorLocation = result.getString("ubicacion");
                    }
                }
                try (PreparedStatement userLookup = connection.prepareStatement(
                        "SELECT nombre, controlMuestras FROM usuarios WHERE id=? LIMIT 1")) {
                    userLookup.setInt(1, userId);
                    try (ResultSet result = userLookup.executeQuery()) {
                        if (!result.next()
                                || !result.getString("nombre").equals(userName)
                                || !result.getBoolean("controlMuestras")) {
                            throw new IllegalArgumentException("el usuario local no está autorizado");
                        }
                    }
                }
                String location = limited(change.getOrDefault("ubicacion", ""), 500, false, "ubicación");
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE muestras SET rotuloCliente=?, nombreCliente=?, descripcion=?, cantidad=?, "
                                + "marca=?, referencia=?, fechaRecepcion=?, estado=?, ubicacion=?, "
                                + "observacionAlmacenamiento=? WHERE id=? AND codigoInterno=?")) {
                    update.setString(1, limited(change.getOrDefault("rotuloCliente", ""), 500, false, "referencia externa"));
                    update.setString(2, limited(change.getOrDefault("nombreCliente", ""), 500, false, "cliente"));
                    update.setString(3, limited(change.getOrDefault("descripcion", ""), 2000, false, "descripción"));
                    update.setInt(4, quantity);
                    update.setString(5, limited(change.getOrDefault("marca", ""), 500, false, "marca"));
                    update.setString(6, limited(change.getOrDefault("referencia", ""), 500, false, "referencia"));
                    update.setString(7, receptionDate);
                    update.setString(8, state);
                    update.setString(9, location);
                    update.setString(10, limited(change.getOrDefault("observacionAlmacenamiento", ""), 4000, false, "observación"));
                    update.setInt(11, sampleId);
                    update.setString(12, sampleCode);
                    if (update.executeUpdate() != 1) throw new IOException("La muestra cambió durante la actualización.");
                }
                replaceDocuments(connection, "muestra_informes", sampleId, reports);
                replaceDocuments(connection, "muestra_cotizaciones", sampleId, quotations);
                try (PreparedStatement movement = connection.prepareStatement(
                        "INSERT INTO movimientos(muestraId, usuarioId, estadoAnterior, estadoNuevo, "
                                + "ubicacionAnterior, ubicacionNueva, fechaHora, observacion) "
                                + "VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP,?)")) {
                    movement.setInt(1, sampleId);
                    movement.setInt(2, userId);
                    movement.setString(3, priorState);
                    movement.setString(4, state);
                    movement.setString(5, priorLocation);
                    movement.setString(6, location);
                    movement.setString(7, "Actualización realizada desde el portal web");
                    movement.executeUpdate();
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static void replaceDocuments(Connection connection, String table, int sampleId,
                                         List<DocumentRef> documents) throws Exception {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE muestraId=?")) {
            delete.setInt(1, sampleId);
            delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + table + "(muestraId, numero, anio) VALUES(?,?,?)")) {
            for (DocumentRef document : documents) {
                insert.setInt(1, sampleId);
                insert.setString(2, document.number());
                insert.setInt(3, document.year());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static List<DocumentRef> parseDocuments(String encoded, boolean quotation) {
        List<DocumentRef> documents = new ArrayList<>();
        if (encoded.isBlank()) return documents;
        Set<String> seen = new HashSet<>();
        for (String item : encoded.split(",", -1)) {
            String[] parts = item.split(":", 2);
            if (parts.length != 2) throw new IllegalArgumentException("documento mal formado");
            int year = positiveInteger(parts[0], "año");
            if (year < 2000 || year > 9999) throw new IllegalArgumentException("año fuera de rango");
            String number;
            try { number = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("documento mal codificado"); }
            number = limited(number, 120, true, quotation ? "cotización" : "informe");
            if (quotation && !number.matches("\\d{4}")) {
                throw new IllegalArgumentException("la cotización debe tener 4 dígitos");
            }
            if (!seen.add(year + "\u0000" + number)) throw new IllegalArgumentException("documento repetido");
            documents.add(new DocumentRef(number, year));
            if (documents.size() > 20) throw new IllegalArgumentException("demasiados documentos");
        }
        return documents;
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> values = new HashMap<>();
        for (String pair : body.split("&", -1)) {
            if (pair.isEmpty()) continue;
            String[] parts = pair.split("=", 2);
            values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8));
        }
        return values;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) throw new IllegalArgumentException("falta el campo " + key);
        return value;
    }

    private static int positiveInteger(String value, String field) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " no válido");
        }
    }

    private static String limited(String value, int maximum, boolean required, String field) {
        String normalized = value == null ? "" : value.trim();
        if ((required && normalized.isEmpty()) || normalized.length() > maximum) {
            throw new IllegalArgumentException(field + " no válido");
        }
        return normalized;
    }

    private record DocumentRef(String number, int year) {}

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
        acknowledgeJob(endpoint, token, jobId, "foto");
    }

    private static void acknowledgeJob(
            URI endpoint, String token, String jobId, String itemName) throws Exception {
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
                    "No fue posible confirmar " + itemName + " sincronizado ("
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
