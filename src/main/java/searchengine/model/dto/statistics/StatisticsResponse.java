package searchengine.model.dto.statistics;

import lombok.Data;

/**
 * DTO ответа для эндпоинта {@code /api/statistics}.
 * 
 * @author Tseliar Vladimir
 */
@Data
public class StatisticsResponse {
    private boolean result;
    private StatisticsData statistics;
}
