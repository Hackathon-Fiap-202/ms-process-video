package com.hackathon.process_video.infra.adapter.inbound;
import com.hackathon.process_video.domain.port.in.ProcessVideoUseCase;
import com.hackathon.process_video.domain.port.out.LoggerPort;
import com.hackathon.process_video.utils.JsonConverter;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ConsumerVideoQueue {
    private ProcessVideoUseCase processVideoUseCase;
    private JsonConverter jsonConverter;
    private LoggerPort loggerPort;

    @SqsListener(
            value = "${app.queues.video-process-command}",
            acknowledgementMode = "MANUAL"
    )
    public void consumeMessage(String payload, Acknowledgement acknowledgement) {
        try {
            final var event = jsonConverter.toEventVideo(payload);
            loggerPort.info("Iniciando processamento do vídeo: {} no bucket: {}", event.keyName, event.bucketName);

            processVideoUseCase.execute(event.keyName, event.bucketName);

            // IMPORTANTE: Deleta a mensagem SOMENTE após sucesso completo
            acknowledgement.acknowledge();

            loggerPort.info("Processamento concluído para: {}", event.keyName);
        } catch (Exception e) {
            loggerPort.error("Erro ao processar mensagem da fila: {}", e.getMessage());
            // Não deleta - mensagem volta para fila após visibility timeout
            throw e;
        }
    }
}