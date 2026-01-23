package searchengine.model.dto.indexing;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO ответа для эндпоинта {@code /api/indexPage}
 * 
 * @author Tseliar Vladimir
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexPageResponse {

    private boolean result;
    private String error;

    /**
     * Конструктор ответа с текстом ошибки.
     *
     * @param result {@link boolean} результат операции
     */
    public IndexPageResponse(boolean result) {
        this.result = result;
        this.error = null;
    }
}
