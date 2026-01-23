package searchengine.model.dto.statistics;

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
}
