package searchengine.model.dto.statistics;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Детальная статистика по одному сайту.
 * 
 * @author Tseliar Vladimir
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DetailedStatisticsItem {
    private String url;
    private String name;
    private String status;
    private long statusTime;
    private String error;
    private int pages;
    private int lemmas;
}
