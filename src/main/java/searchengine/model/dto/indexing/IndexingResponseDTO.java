package searchengine.model.dto.indexing;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO ответа для эндпоинтов запуска/остановки индексации.
 * 
 * @author Tseliar Vladimir
 */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexingResponseDTO {

    private boolean result;
    private String error;

    /**
     * Конструктор ответа без текста ошибки.
     *
     * @param result {@link boolean} результат операции
     */
    public IndexingResponseDTO(boolean result) {
        this.result = result;
    }
}
