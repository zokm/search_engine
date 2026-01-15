package searchengine.model.dto.indexing;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO для API /api/startIndexing и /api/stopIndexing
 * Содержит флаг успеха и сообщение об ошибке (если есть)
 *
 * @author Tseliar Vladimir
 */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexingResponseDTO {

    private boolean result;
    private String error;

    public IndexingResponseDTO(boolean result) {
        this.result = result;
    }
}
