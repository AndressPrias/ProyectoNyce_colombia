package utilities;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

public final class AppUpdateService {

    private static final String MANIFEST_FILE = "latest.properties";

    private AppUpdateService() {}

    public static Optional<UpdateInfo> findAvailableUpdate() {
        Path manifest = AppConfig.getUpdatesFolder().resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifest)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(manifest)) {
            properties.load(input);
        } catch (IOException e) {
            return Optional.empty();
        }

        String build = clean(properties.getProperty("app.build"));
        String installer = clean(properties.getProperty("installer.path"));
        if (build == null || installer == null || !isNewerBuild(build, AppVersion.getBuild())) {
            return Optional.empty();
        }

        return Optional.of(new UpdateInfo(
                clean(properties.getProperty("app.version"), AppVersion.getVersion()),
                build,
                clean(properties.getProperty("app.notes"), "Hay una actualizacion disponible."),
                installer
        ));
    }

    public static void openInstaller(UpdateInfo update) throws IOException {
        if (update == null || update.installerPath().isBlank()) {
            return;
        }

        String installer = update.installerPath().trim();
        if (installer.startsWith("http://") || installer.startsWith("https://")) {
            Desktop.getDesktop().browse(URI.create(installer));
            return;
        }

        Path path = toInstallerPath(installer);
        if (!path.isAbsolute()) {
            path = AppConfig.getUpdatesFolder().resolve(installer);
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("No se encontro el instalador: " + path);
        }
        new ProcessBuilder(path.toString()).start();
    }

    private static Path toInstallerPath(String installer) throws IOException {
        if (installer.startsWith("file:/")) {
            try {
                return Path.of(new URI(installer));
            } catch (IllegalArgumentException | URISyntaxException e) {
                throw new IOException("Ruta del instalador no valida: " + installer, e);
            }
        }
        return Path.of(installer);
    }

    private static boolean isNewerBuild(String candidate, String current) {
        try {
            return Instant.parse(candidate).isAfter(Instant.parse(current));
        } catch (Exception ignored) {
            return !candidate.equals(current);
        }
    }

    private static String clean(String value) {
        return clean(value, null);
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.isBlank() || value.contains("${")) {
            return fallback;
        }
        return value.trim();
    }

    public record UpdateInfo(String version, String build, String notes, String installerPath) {}
}
