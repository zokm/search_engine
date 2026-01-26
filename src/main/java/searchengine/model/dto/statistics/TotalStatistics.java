package searchengine.model.dto.statistics;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Общая статистика по приложению.
 * 
 * @author Tseliar Vladimir
 */
@Data
public class TotalStatistics {
    private int sites;
    private int pages;
    private int lemmas;
    private boolean indexing;

    /**
     * Возвращает признак, что индексация сейчас выполняется.
     *
     * Метод добавлен для совместимости с фронтендом из шаблона, который ожидает поле {@code isIndexing}
     * в JSON-ответе
     *
     * @return true, если индексация выполняется
     */
    @JsonProperty("isIndexing")
    public boolean getIsIndexing() {
        return indexing;
    }
}
