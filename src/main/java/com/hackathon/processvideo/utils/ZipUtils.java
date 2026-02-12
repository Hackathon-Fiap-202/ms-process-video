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

    private ZipUtils() {
    }

    public static InputStream compressFiles(List<File> files) {
        File tempZip = null;
        try {
            tempZip = createSecureTempFile("video_frames_", ".zip");

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


    private static File createSecureTempFile(String prefix, String suffix) throws IOException {
        try {
            final Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            final FileAttribute<Set<PosixFilePermission>> attrs = PosixFilePermissions.asFileAttribute(permissions);

            final Path tempPath = Files.createTempFile(prefix, suffix, attrs);
            LOGGER.log(Level.FINE, "Secure temp file created with restricted permissions, path={0}", tempPath);
            return tempPath.toFile();
        } catch (UnsupportedOperationException e) {
            // POSIX permissions not supported (e.g., on Windows or certain filesystems)
            LOGGER.log(Level.FINE, "POSIX permissions not supported, using default temp file creation");
            final Path tempPath = Files.createTempFile(prefix, suffix);
            final File tempFile = tempPath.toFile();

            // On Windows, set file to be readable/writable by owner only via file permissions
            try {
                if (!tempFile.setReadable(false, false)) { // Remove all other permissions
                    LOGGER.log(Level.FINE, "Could not remove readable permission");
                }
                if (!tempFile.setWritable(false, false)) {
                    LOGGER.log(Level.FINE, "Could not remove writable permission");
                }
                if (!tempFile.setExecutable(false, false)) {
                    LOGGER.log(Level.FINE, "Could not remove executable permission");
                }
                if (!tempFile.setReadable(true, true)) {  // Add owner read
                    LOGGER.log(Level.FINE, "Could not set owner readable permission");
                }
                if (!tempFile.setWritable(true, true)) {  // Add owner write
                    LOGGER.log(Level.FINE, "Could not set owner writable permission");
                }
                LOGGER.log(Level.FINE, "Applied file permissions for Windows, path={0}", tempFile);
            } catch (SecurityException se) {
                LOGGER.log(Level.WARNING, "Could not set file permissions, error=" + se.getMessage());
            }

            return tempFile;
        }
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
