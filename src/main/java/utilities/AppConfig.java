package utilities;

import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class AppConfig {

    private static final String CONFIG_FILE_NAME = "config.properties";
    private static final String KEY_STORAGE_FOLDER = "storage.folder";
    private static final String DB_FILE_NAME = "lencdb.db";
    private static final String PHOTOS_FOLDER_NAME = "fotos_muestras";
    private static final String REMISSIONS_FOLDER_NAME = "remisiones";
    private static final String USER_AVATARS_FOLDER_NAME = "avatar_usuarios";

    private static Path storageFolder;

    private AppConfig() {}

    public static void ensureStorageFolderSelected(Window owner) throws IOException {
        Properties properties = loadProperties();
        String configuredPath = properties.getProperty(KEY_STORAGE_FOLDER);

        if (configuredPath != null && !configuredPath.isBlank()) {
            Path configuredFolder = Paths.get(configuredPath).toAbsolutePath().normalize();
            if (Files.isDirectory(configuredFolder)) {
                initializeFolders(configuredFolder);
                storageFolder = configuredFolder;
                return;
            }
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Seleccione la carpeta de OneDrive para la base de datos y fotos");
        File home = new File(System.getProperty("user.home"));
        if (home.exists()) {
            chooser.setInitialDirectory(home);
        }

        File selected = chooser.showDialog(owner);
        if (selected == null) {
            throw new IllegalStateException("Debe seleccionar una carpeta para almacenar la base de datos y las fotos.");
        }

        Path selectedFolder = selected.toPath().toAbsolutePath().normalize();
        initializeFolders(selectedFolder);
        storageFolder = selectedFolder;

        properties.setProperty(KEY_STORAGE_FOLDER, selectedFolder.toString());
        saveProperties(properties);
    }

    public static Path getStorageFolder() {
        if (storageFolder == null) {
            try {
                ensureStorageFolderSelected(null);
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo cargar la carpeta de almacenamiento", e);
            }
        }
        return storageFolder;
    }

    public static Path getDatabasePath() {
        return getStorageFolder().resolve(DB_FILE_NAME);
    }

    public static Path getSamplePhotosFolder() {
        Path folder = getStorageFolder().resolve(PHOTOS_FOLDER_NAME);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear la carpeta de fotos: " + folder, e);
        }
        return folder;
    }
    public static Path getRemissionsFolder() {
        Path folder = getStorageFolder().resolve(REMISSIONS_FOLDER_NAME);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear la carpeta de remisiones: " + folder, e);
        }
        return folder;
    }

    public static Path getUserAvatarsFolder() {
        Path folder = getStorageFolder().resolve(USER_AVATARS_FOLDER_NAME);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear la carpeta de avatares: " + folder, e);
        }
        return folder;
    }

    public static Path getConfigPath() {
        return getAppFolder().resolve(CONFIG_FILE_NAME);
    }

    private static Path getAppFolder() {
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    private static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        Path configPath = getConfigPath();
        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
            }
        }
        return properties;
    }

    private static void saveProperties(Properties properties) throws IOException {
        Path configPath = getConfigPath();
        Files.createDirectories(configPath.getParent());
        try (OutputStream output = Files.newOutputStream(configPath)) {
            properties.store(output, "Configuracion de almacenamiento ControlMuestrasLENC");
        }
    }

    private static void initializeFolders(Path folder) throws IOException {
        Files.createDirectories(folder);
        Files.createDirectories(folder.resolve(PHOTOS_FOLDER_NAME));
        Files.createDirectories(folder.resolve(REMISSIONS_FOLDER_NAME));
        Files.createDirectories(folder.resolve(USER_AVATARS_FOLDER_NAME));
        if (!Files.isWritable(folder)) {
            throw new IOException("La carpeta seleccionada no permite escritura: " + folder);
        }
    }
}