package com.hackathon.processvideo.utils;

import com.hackathon.processvideo.domain.exception.VideoProcessingException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipUtils {

    private static final Logger LOGGER = Logger.getLogger(ZipUtils.class.getName());
    private static final String TEMP_FILE_PREFIX = "video_frames_";
    private static final String TEMP_FILE_SUFFIX = ".zip";

    // Permission type constants
    private static final String PERMISSION_READABLE = "setReadable";
    private static final String PERMISSION_WRITABLE = "setWritable";
    private static final String PERMISSION_EXECUTABLE = "setExecutable";
    private static final int PERMISSION_PREFIX_LENGTH = 3; // Length of "set" prefix

    private ZipUtils() {
    }

    public static InputStream compressFiles(List<File> files) {
        File tempZip = null;
        try {
            tempZip = createSecureTempFile();

            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tempZip)))) {
                for (File file : files) {
                    addToZip(file, zos);
                }
            }

            return new FileDeletingInputStream(tempZip);

        } catch (IOException e) {
            if (tempZip != null && tempZip.exists()) {
                try {
                    Files.delete(tempZip.toPath());
                } catch (IOException deleteException) {
                    LOGGER.log(Level.WARNING, "Não foi possível deletar ZIP temporário após erro: {0}", deleteException.getMessage());
                }
            }
            throw new VideoProcessingException("Erro ao processar compressão de arquivos", e);
        }
    }

    private static void addToZip(File file, ZipOutputStream zos) throws IOException {
        final ZipEntry entry = new ZipEntry(file.getName());
        zos.putNextEntry(entry);

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            bis.transferTo(zos);
        }

        zos.closeEntry();

        try {
            Files.delete(file.toPath());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Falha ao deletar arquivo: {0}. Agendando deleção para o encerramento: {1}",
                    new Object[]{file.getAbsolutePath(), e.getMessage()});
            file.deleteOnExit();
        }
    }


    private static File createSecureTempFile() throws IOException {

        final Path tempDir = getSecureTempDirectory();

        try {
            final Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            final FileAttribute<Set<PosixFilePermission>> attrs = PosixFilePermissions.asFileAttribute(permissions);

            final Path tempPath = Files.createTempFile(tempDir, TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX, attrs);
            LOGGER.log(Level.FINE, "Secure temp file created with restricted permissions, path={0}", tempPath);
            return tempPath.toFile();
        } catch (UnsupportedOperationException e) {
            // POSIX permissions not supported (e.g., on Windows or certain filesystems)
            LOGGER.log(Level.FINE, "POSIX permissions not supported, using fallback permission method");
            final Path tempPath = Files.createTempFile(tempDir, TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
            final File tempFile = tempPath.toFile();

            try {
                applyFilePermissions(tempFile);
                LOGGER.log(Level.FINE, "Applied file permissions for Windows, path={0}", tempFile);
            } catch (SecurityException se) {
                handleFilePermissionError(tempFile, se);
                throw new IOException("Failed to set secure permissions on temp file: " + se.getMessage(), se);
            }

            return tempFile;
        }
    }

    /**
     * Applies restrictive file permissions: removes all permissions except owner read/write.
     * First restricts all permissions, then grants only owner permissions.
     *
     * @param file the file to apply permissions to
     * @throws SecurityException if permissions cannot be set
     */
    private static void applyFilePermissions(File file) throws SecurityException {
        applyRestrictivePermissions(file, false);
    }

    private static void applyDirectoryPermissions(File directory) throws SecurityException {
        applyRestrictivePermissions(directory, true);
    }

    /**
     * Applies restrictive permissions to a file or directory.
     * Removes all permissions, then grants only owner permissions.
     *
     * @param fileOrDir   the file or directory to apply permissions to
     * @param isDirectory true if applying to a directory, false if applying to a file
     * @throws SecurityException if permissions cannot be set
     */
    private static void applyRestrictivePermissions(File fileOrDir, boolean isDirectory) throws SecurityException {
        // First, restrict all permissions
        applyPermission(fileOrDir, PERMISSION_READABLE, false, false);
        applyPermission(fileOrDir, PERMISSION_WRITABLE, false, false);
        applyPermission(fileOrDir, PERMISSION_EXECUTABLE, false, false);

        // Then, grant only owner permissions
        applyPermission(fileOrDir, PERMISSION_READABLE, true, true);
        applyPermission(fileOrDir, PERMISSION_WRITABLE, true, true);

        // Directories need execute permission for owner
        if (isDirectory) {
            applyPermission(fileOrDir, PERMISSION_EXECUTABLE, true, true);
        }
    }

    /**
     * Applies a single permission setting and logs if it fails.
     *
     * @param fileOrDir      the file or directory
     * @param permissionType the type of permission (setReadable, setWritable, setExecutable)
     * @param value          the value to set
     * @param ownerOnly      whether to apply only to owner
     */
    private static void applyPermission(File fileOrDir, String permissionType, boolean value, boolean ownerOnly) {
        boolean success = false;
        try {
            success = switch (permissionType) {
                case PERMISSION_READABLE -> fileOrDir.setReadable(value, ownerOnly);
                case PERMISSION_WRITABLE -> fileOrDir.setWritable(value, ownerOnly);
                case PERMISSION_EXECUTABLE -> fileOrDir.setExecutable(value, ownerOnly);
                default -> false;
            };
        } catch (SecurityException e) {
            LOGGER.log(Level.FINE, "Exception while applying {0} permission: {1}",
                    new Object[]{permissionType, e.getMessage()});
        }

        if (!success) {
            LOGGER.log(Level.FINE, "Could not {0} {1} permission",
                    new Object[]{value ? "set" : "remove",
                            permissionType.substring(PERMISSION_PREFIX_LENGTH).toLowerCase()});
        }
    }

    private static Path getSecureTempDirectory() throws IOException {
        final String customTempDir = System.getProperty("app.temp.dir");

        if (customTempDir != null && !customTempDir.trim().isEmpty()) {
            final Path dirPath = Path.of(customTempDir);
            if (!Files.exists(dirPath)) {
                createSecureDirectory(dirPath);
            }
            return dirPath;
        }

        final String appName = "processvideo";
        final Path secureTempSubdir = Path.of(System.getProperty("java.io.tmpdir"), appName);

        if (!Files.exists(secureTempSubdir)) {
            createSecureDirectory(secureTempSubdir);
        }

        return secureTempSubdir;
    }

    private static void createSecureDirectory(Path dirPath) throws IOException {
        try {
            final Set<PosixFilePermission> dirPermissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            );
            final FileAttribute<Set<PosixFilePermission>> attrs = PosixFilePermissions.asFileAttribute(dirPermissions);
            Files.createDirectories(dirPath, attrs);
            LOGGER.log(Level.FINE, "Created secure temp directory with POSIX permissions, path={0}", dirPath);
        } catch (UnsupportedOperationException e) {
            // POSIX not supported, create normally and set permissions
            Files.createDirectories(dirPath);
            applyWindowsDirectoryPermissions(dirPath);
        }
    }

    private static void applyWindowsDirectoryPermissions(Path dirPath) throws IOException {
        try {
            final File dirFile = dirPath.toFile();
            applyDirectoryPermissions(dirFile);
            LOGGER.log(Level.FINE, "Applied directory permissions for Windows, path={0}", dirPath);
        } catch (SecurityException se) {
            handleDirectoryPermissionError(dirPath, se);
            throw new IOException("Failed to set secure directory permissions on " + dirPath + ": " + se.getMessage(), se);
        }
    }

    private static void handleFilePermissionError(File tempFile, SecurityException se) {
        LOGGER.log(Level.WARNING, "Could not set file permissions on temp file {0}: {1}",
                new Object[]{tempFile.getAbsolutePath(), se.getMessage()});
        try {
            Files.delete(tempFile.toPath());
        } catch (IOException e) {
            tempFile.deleteOnExit();
            LOGGER.log(Level.FINE, "Failed to delete insecure temp file, scheduling for deletion on exit: {0}",
                    tempFile.getAbsolutePath());
        }
    }

    private static void handleDirectoryPermissionError(Path dirPath, SecurityException se) {
        LOGGER.log(Level.WARNING, "Failed to set directory permissions on {0}: {1}",
                new Object[]{dirPath.toAbsolutePath(), se.getMessage()});
    }

    private static class FileDeletingInputStream extends FileInputStream {
        private final File file;

        FileDeletingInputStream(File file) throws FileNotFoundException {
            super(file);
            this.file = file;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                if (file.exists()) {
                    try {
                        Files.delete(file.toPath());
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Não foi possível limpar o ZIP temporário: {0}: {1}",
                                new Object[]{file.getAbsolutePath(), e.getMessage()});
                        file.deleteOnExit();
                    }
                }
            }
        }
    }
}
