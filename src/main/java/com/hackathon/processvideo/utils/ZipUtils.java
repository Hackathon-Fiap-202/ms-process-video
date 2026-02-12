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
import java.util.List;
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
            tempZip = File.createTempFile("video_frames_", ".zip");

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