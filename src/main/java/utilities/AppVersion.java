package utilities;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.prefs.Preferences;

public final class AppVersion {

    private static final String RESOURCE = "/app-version.properties";
    private static final String UNKNOWN_VERSION = "1.2.0";
    private static final String UNKNOWN_BUILD = "local";
    private static final String PREF_LAST_SEEN = "lastSeenBuildId";
    private static final DateTimeFormatter BUILD_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final Preferences PREFS = Preferences.userNodeForPackage(AppVersion.class);
    private static final Properties PROPERTIES = loadProperties();

    private AppVersion() {}

    public static String getVersion() {
        return clean(PROPERTIES.getProperty("app.version"), UNKNOWN_VERSION);
    }

    public static String getBuild() {
        return clean(PROPERTIES.getProperty("app.build"), UNKNOWN_BUILD);
    }

    public static String getNotes() {
        return clean(PROPERTIES.getProperty("app.notes"), "Incluye mejoras recientes del sistema.");
    }

    public static String getDisplayVersion() {
        return getShortDisplayVersion() + ("local".equals(getBuild()) ? "" : " - " + getBuildDisplay());
    }

    public static String getShortDisplayVersion() {
        return "Version " + getVersion();
    }

    public static String getBuildDisplay() {
        String build = getBuild();
        try {
            return BUILD_FORMAT.format(Instant.parse(build));
        } catch (Exception ignored) {
            return build;
        }
    }

    public static String getBuildId() {
        return getVersion() + "|" + getBuild();
    }

    public static boolean shouldNotifyCurrentVersion() {
        String current = getBuildId();
        String lastSeen = PREFS.get(PREF_LAST_SEEN, "");
        return !current.equals(lastSeen);
    }

    public static void markCurrentVersionSeen() {
        PREFS.put(PREF_LAST_SEEN, getBuildId());
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream input = AppVersion.class.getResourceAsStream(RESOURCE)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
            // Si no se puede leer la version, la app sigue funcionando con valores locales.
        }
        return properties;
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.isBlank() || value.contains("${")) {
            return fallback;
        }
        return value.trim();
    }
}
