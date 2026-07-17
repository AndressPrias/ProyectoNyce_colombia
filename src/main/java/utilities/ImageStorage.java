package utilities;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

public final class ImageStorage {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String SAMPLE_PHOTOS_PREFIX = "fotos_muestras/";
    private static final String USER_AVATARS_PREFIX = "avatar_usuarios/";

    private ImageStorage() {}

    public static String copySamplePhoto(File sourceFile) throws IOException {
        return copyImageToFolder(sourceFile, AppConfig.getSamplePhotosFolder(), "muestra");
    }

    public static String copySamplePhoto(File sourceFile, String baseName) throws IOException {
        return copyImageToFolder(sourceFile, AppConfig.getSamplePhotosFolder(), baseName);
    }

    public static String copyUserAvatar(File sourceFile) throws IOException {
        return copyImageToFolder(sourceFile, AppConfig.getUserAvatarsFolder(), "avatar");
    }

    public static void deleteUserAvatarIfManaged(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }

        String path = storedPath.trim();
        if (!path.startsWith(USER_AVATARS_PREFIX)) {
            return;
        }

        String fileName = path.substring(USER_AVATARS_PREFIX.length());
        if (fileName.matches("Avatar_\\d+\\.png")) {
            return;
        }

        try {
            File avatar = resolveSharedFile(AppConfig.getUserAvatarsFolder(), fileName);
            if (avatar != null) {
                Files.deleteIfExists(avatar.toPath());
            }
        } catch (IOException ignored) {
            // La limpieza de archivos antiguos no debe impedir guardar el usuario.
        }
    }

    public static String normalizeSamplePhotoName(String storedPath, String baseName) throws IOException {
        if (storedPath == null || storedPath.isBlank() || baseName == null || baseName.isBlank()) {
            return storedPath;
        }

        File sourceFile = resolveImageFile(storedPath);
        if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
            return storedPath;
        }

        String extension = getExtension(sourceFile.getName());
        String safeBaseName = sanitizeBaseName(baseName, "muestra");
        String fileName = safeBaseName + extension;
        Path destinationFolder = AppConfig.getSamplePhotosFolder();
        Path destination = destinationFolder.resolve(fileName).toAbsolutePath().normalize();
        Path source = sourceFile.toPath().toAbsolutePath().normalize();

        if (!source.equals(destination)) {
            deleteSamplePhotosWithBaseName(safeBaseName, fileName);
            Files.createDirectories(destinationFolder);
            if (source.startsWith(destinationFolder.toAbsolutePath().normalize())) {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        return storedRelativePath(destinationFolder, destination);
    }

    public static String resolveImageUrl(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }

        String path = storedPath.trim();
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }

        if (path.startsWith("file:")) {
            File file = resolveImageFile(path);
            return file == null ? null : file.toURI().toString();
        }

        if (path.startsWith(SAMPLE_PHOTOS_PREFIX)) {
            return resolveSharedFileUrl(AppConfig.getSamplePhotosFolder(), path.substring(SAMPLE_PHOTOS_PREFIX.length()));
        }

        if (path.startsWith(USER_AVATARS_PREFIX)) {
            return resolveSharedFileUrl(AppConfig.getUserAvatarsFolder(), path.substring(USER_AVATARS_PREFIX.length()));
        }

        if (path.startsWith("/avatarUsuarios/") || path.startsWith("/avatar_usuarios/")) {
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            String sharedAvatarUrl = resolveSharedFileUrl(AppConfig.getUserAvatarsFolder(), fileName);
            if (sharedAvatarUrl != null) return sharedAvatarUrl;
            URL resource = ImageStorage.class.getResource(path);
            return resource == null ? null : resource.toExternalForm();
        }

        if (!path.contains(File.separator) && !path.contains("/") && !path.contains("\\")) {
            String sharedAvatarUrl = resolveSharedFileUrl(AppConfig.getUserAvatarsFolder(), path);
            if (sharedAvatarUrl != null) return sharedAvatarUrl;
            String sharedSampleUrl = resolveSharedFileUrl(AppConfig.getSamplePhotosFolder(), path);
            if (sharedSampleUrl != null) return sharedSampleUrl;
        }

        File file = new File(path);
        if (file.exists()) {
            return file.toURI().toString();
        }

        String fileName = file.getName();
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String sharedSampleUrl = resolveSharedFileUrl(AppConfig.getSamplePhotosFolder(), fileName);
        if (sharedSampleUrl != null) return sharedSampleUrl;
        return resolveSharedFileUrl(AppConfig.getUserAvatarsFolder(), fileName);
    }


    public static File resolveImageFile(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }

        String path = storedPath.trim();
        if (path.startsWith(SAMPLE_PHOTOS_PREFIX)) {
            return resolveSharedFile(AppConfig.getSamplePhotosFolder(), path.substring(SAMPLE_PHOTOS_PREFIX.length()));
        }

        if (path.startsWith(USER_AVATARS_PREFIX)) {
            return resolveSharedFile(AppConfig.getUserAvatarsFolder(), path.substring(USER_AVATARS_PREFIX.length()));
        }

        if (path.startsWith("/avatarUsuarios/") || path.startsWith("/avatar_usuarios/")) {
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            return resolveSharedFile(AppConfig.getUserAvatarsFolder(), fileName);
        }

        if (!path.startsWith("file:") && !path.startsWith("http://") && !path.startsWith("https://")
                && !path.contains(File.separator) && !path.contains("/") && !path.contains("\\")) {
            File sharedAvatar = resolveSharedFile(AppConfig.getUserAvatarsFolder(), path);
            if (sharedAvatar != null) return sharedAvatar;
            return resolveSharedFile(AppConfig.getSamplePhotosFolder(), path);
        }

        if (path.startsWith("file:")) {
            try {
                File file = new File(java.net.URI.create(path));
                if (file.exists()) {
                    return file;
                }
                return resolveSharedFileByName(file.getName());
            } catch (Exception ignored) {
                return null;
            }
        }

        File file = new File(path);
        return file.exists() ? file : resolveSharedFileByName(file.getName());
    }

    public static File getUserAvatarsInitialDirectory(String currentAvatarPath) {
        copyBundledAvatarsIfNeeded();

        File currentFile = resolveImageFile(currentAvatarPath);
        if (currentFile != null && currentFile.isFile()) {
            File parent = currentFile.getParentFile();
            if (parent != null && parent.isDirectory()) {
                return parent;
            }
        }

        File sharedFolder = AppConfig.getUserAvatarsFolder().toFile();
        return sharedFolder.isDirectory() ? sharedFolder : null;
    }

    private static void copyBundledAvatarsIfNeeded() {
        Path avatarsFolder = AppConfig.getUserAvatarsFolder();
        for (int i = 1; i <= 13; i++) {
            String fileName = "Avatar_" + i + ".png";
            Path destination = avatarsFolder.resolve(fileName).toAbsolutePath().normalize();
            if (Files.exists(destination)) {
                continue;
            }

            try (InputStream input = ImageStorage.class.getResourceAsStream("/avatarUsuarios/" + fileName)) {
                if (input != null) {
                    Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ignored) {
                // Si un avatar base no se puede copiar, el selector igual abre la carpeta compartida.
            }
        }
    }

    private static String copyImageToFolder(File sourceFile, Path destinationFolder, String defaultBaseName) throws IOException {
        if (sourceFile == null) return null;
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            throw new IOException("La imagen seleccionada no existe: " + sourceFile);
        }

        Path folder = destinationFolder.toAbsolutePath().normalize();
        Path source = sourceFile.toPath().toAbsolutePath().normalize();
        Files.createDirectories(folder);
        if (source.startsWith(folder)) {
            return storedRelativePath(destinationFolder, source);
        }

        String extension = getExtension(sourceFile.getName());
        String safeBaseName = sanitizeBaseName(sourceFile.getName(), defaultBaseName);
        String fileName = safeBaseName + "_" + LocalDateTime.now().format(TIMESTAMP)
                + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
        Path destination = folder.resolve(fileName).toAbsolutePath().normalize();

        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        return storedRelativePath(destinationFolder, destination);
    }

    private static void deleteSamplePhotosWithBaseName(String safeBaseName, String exceptFileName) throws IOException {
        Path folder = AppConfig.getSamplePhotosFolder();
        if (!Files.isDirectory(folder)) {
            return;
        }

        try (var files = Files.list(folder)) {
            for (Path file : files.toList()) {
                String fileName = file.getFileName().toString();
                int dot = fileName.lastIndexOf('.');
                String currentBaseName = dot > 0 ? fileName.substring(0, dot) : fileName;
                if (currentBaseName.equals(safeBaseName) && !fileName.equals(exceptFileName)) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private static String storedRelativePath(Path destinationFolder, Path destination) {
        Path folder = destinationFolder.toAbsolutePath().normalize();
        String fileName = destination.getFileName().toString();
        if (folder.equals(AppConfig.getSamplePhotosFolder().toAbsolutePath().normalize())) {
            return SAMPLE_PHOTOS_PREFIX + fileName;
        }
        if (folder.equals(AppConfig.getUserAvatarsFolder().toAbsolutePath().normalize())) {
            return USER_AVATARS_PREFIX + fileName;
        }
        return fileName;
    }

    private static String resolveSharedFileUrl(Path folder, String fileName) {
        File file = resolveSharedFile(folder, fileName);
        return file == null ? null : file.toURI().toString();
    }

    private static File resolveSharedFile(Path folder, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        Path sharedFile = folder.resolve(fileName).toAbsolutePath().normalize();
        return Files.exists(sharedFile) ? sharedFile.toFile() : null;
    }

    private static File resolveSharedFileByName(String fileName) {
        File sharedSample = resolveSharedFile(AppConfig.getSamplePhotosFolder(), fileName);
        return sharedSample != null ? sharedSample : resolveSharedFile(AppConfig.getUserAvatarsFolder(), fileName);
    }

    private static String getExtension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return ".jpg";
        return fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String sanitizeBaseName(String fileName, String defaultBaseName) {
        if (fileName == null || fileName.isBlank()) return defaultBaseName;
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        base = base.replaceAll("[^A-Za-z0-9_-]", "_");
        base = base.replaceAll("_+", "_");
        if (base.isBlank()) return defaultBaseName;
        return base.length() > 40 ? base.substring(0, 40) : base;
    }
}
