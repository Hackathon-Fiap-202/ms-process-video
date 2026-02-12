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
                final boolean deleted = tempZip.delete();
                if (!deleted) {
                    LOGGER.log(Level.WARNING, "Não foi possível deletar ZIP temporário após erro.");
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

        if (!file.delete()) {
            LOGGER.log(Level.WARNING, "Falha ao deletar arquivo: {0}. Agendando deleção para o encerramento.", file.getAbsolutePath());
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
                LOGGER.log(Level.WARNING, "Could not set file permissions, error=" + se.getMessage());
                // If we can't set secure permissions, the file is in an insecure state - delete it
                if (tempFile.exists() && !tempFile.delete()) {
                    tempFile.deleteOnExit();
                }
                throw new IOException("Failed to set secure permissions on temp file", se);
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
        // First, restrict all permissions
        if (!file.setReadable(false, false)) {
            LOGGER.log(Level.FINE, "Could not remove readable permission");
        }
        if (!file.setWritable(false, false)) {
            LOGGER.log(Level.FINE, "Could not remove writable permission");
        }
        if (!file.setExecutable(false, false)) {
            LOGGER.log(Level.FINE, "Could not remove executable permission");
        }
        // Then, grant only owner permissions
        if (!file.setReadable(true, true)) {
            LOGGER.log(Level.FINE, "Could not set owner readable permission");
        }
        if (!file.setWritable(true, true)) {
            LOGGER.log(Level.FINE, "Could not set owner writable permission");
        }
    }

    private static void applyDirectoryPermissions(File directory) throws SecurityException {
        if (!directory.setReadable(false, false)) {
            LOGGER.log(Level.FINE, "Could not remove readable permission");
        }
        if (!directory.setWritable(false, false)) {
            LOGGER.log(Level.FINE, "Could not remove writable permission");
        }
        if (!directory.setExecutable(false, false)) {
            LOGGER.log(Level.FINE, "Could not remove executable permission");
        }
        if (!directory.setReadable(true, true)) {
            LOGGER.log(Level.FINE, "Could not set owner readable permission");
        }
        if (!directory.setWritable(true, true)) {
            LOGGER.log(Level.FINE, "Could not set owner writable permission");
        }
        if (!directory.setExecutable(true, true)) {
            LOGGER.log(Level.FINE, "Could not set owner executable permission");
        }
    }

    private static Path getSecureTempDirectory() throws IOException {
        final String customTempDir = System.getProperty("app.temp.dir");

        if (customTempDir != null && !customTempDir.trim().isEmpty()) {
            final Path dirPath = Path.of(customTempDir);
            if (!Files.exists(dirPath)) {
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
                    try {
                        final File dirFile = dirPath.toFile();
                        applyDirectoryPermissions(dirFile);
                        LOGGER.log(Level.FINE, "Applied directory permissions for Windows, path={0}", dirPath);
                    } catch (SecurityException se) {
                        LOGGER.log(Level.WARNING, "Could not set directory permissions, error=" + se.getMessage());
                    }
                }
            }
            return dirPath;
        }

        final String appName = "processvideo";
        final Path secureTempSubdir = Path.of(System.getProperty("java.io.tmpdir"), appName);

        if (!Files.exists(secureTempSubdir)) {
            try {
                final Set<PosixFilePermission> dirPermissions = EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE
                );
                final FileAttribute<Set<PosixFilePermission>> attrs = PosixFilePermissions.asFileAttribute(dirPermissions);
                Files.createDirectories(secureTempSubdir, attrs);
                LOGGER.log(Level.FINE, "Created secure temp subdirectory with POSIX permissions, path={0}", secureTempSubdir);
            } catch (UnsupportedOperationException e) {
                Files.createDirectories(secureTempSubdir);
                try {
                    final File subDirFile = secureTempSubdir.toFile();
                    applyDirectoryPermissions(subDirFile);
                    LOGGER.log(Level.FINE, "Applied directory permissions for Windows, path={0}", secureTempSubdir);
                } catch (SecurityException se) {
                    LOGGER.log(Level.WARNING, "Could not set directory permissions, error=" + se.getMessage());
                }
            }
        }

        return secureTempSubdir;
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
                    final boolean deleted = file.delete();
                    if (!deleted) {
                        LOGGER.log(Level.WARNING, "Não foi possível limpar o ZIP temporário: {0}", file.getAbsolutePath());
                        file.deleteOnExit();
                    }
                }
            }
        }
    }
}
