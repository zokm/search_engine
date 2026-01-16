package searchengine.model.dto.indexing;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для API indexPage
 * Содержит флаг успеха и сообщение об ошибке (если есть)
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
}

