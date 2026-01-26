package searchengine.model.dto.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO ответа для эндпоинта {@code /api/search}.
 * 
 * @author Tseliar Vladimir
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResponse {
    private boolean result;
    private String error;
    private Integer count;
    private List<SearchResultItem> data;

    /**
     * Создаёт успешный ответ.
     *
     * @param count общее количество результатов
     * @param data {@link List}<{@link SearchResultItem}> список результатов
     * @return {@link SearchResponse} с успешным ответом
     */
    public static SearchResponse ok(int count, List<SearchResultItem> data) {
        return new SearchResponse(true, null, count, data);
    }

    /**
     * Создаёт ответ с ошибкой.
     *
     * @param error {@link String} текст ошибки
     * @return {@link SearchResponse} с ошибкой
     */
    public static SearchResponse error(String error) {
        return new SearchResponse(false, error, null, null);
    }
}
