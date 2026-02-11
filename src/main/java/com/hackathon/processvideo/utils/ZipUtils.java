package com.hackathon.processvideo.utils;

import com.hackathon.processvideo.domain.exception.VideoProcessingException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Utilitário para compressão de arquivos em ZIP.
 * NOTA: Para processamento de muitos frames, prefira usar {@link com.hackathon.processvideo.infra.adapter.outbound.FramesExtractor#extractFramesAsZip(InputStream, String)}
 * pois evita carregar todos os arquivos na memória simultaneamente.
 */
public class ZipUtils {

    private static final String FILE_NAME_PATTERN = "frame_%04d.png";

    private ZipUtils() {
    }

    /**
     * Comprime arquivos em um ZIP.
     * @deprecated Prefira streaming com FramesExtractor.extractFramesAsZip() para grande volume de frames.
     */
    @Deprecated(since = "1.1.0", forRemoval = false)
    public static InputStream compressFiles(List<File> files) {
        try {
            File tempZip = File.createTempFile("output_", ".zip");
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip))) {
                for (File file : files) {
                    ZipEntry entry = new ZipEntry(file.getName());
                    zos.putNextEntry(entry);
                    try (FileInputStream fis = new FileInputStream(file)) {
                        fis.transferTo(zos);
                    }
                    zos.closeEntry();
                    file.delete(); // DELETA O PNG IMEDIATAMENTE APÓS ZIPAR
                }
            }
            return new FileInputStream(tempZip);
        } catch (IOException e) {
            throw new VideoProcessingException("Erro ao zipar", e);
        }
    }

    private static void addFileToZip(ZipOutputStream zos, String fileName, InputStream content) throws IOException {
        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);
        content.transferTo(zos);
        zos.closeEntry();
    }
}
