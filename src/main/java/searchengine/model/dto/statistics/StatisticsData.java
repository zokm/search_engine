package searchengine.model.dto.statistics;

import lombok.Data;

import java.util.List;

/**
 * Контейнер статистики: общая (total) и детальная по сайтам (detailed).
 * 
 * @author Tseliar Vladimir
 */
@Data
public class StatisticsData {
    private TotalStatistics total;
    private List<DetailedStatisticsItem> detailed;
}
