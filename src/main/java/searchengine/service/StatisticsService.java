package searchengine.service;

import searchengine.model.dto.statistics.StatisticsResponse;

/**
 * Сервис получения статистики по индексации и состоянию индекса.
 * 
 * @author Tseliar Vladimir
 */
public interface StatisticsService {

    /**
     * Возвращает статистику в формате API.
     *
     * @return {@link StatisticsResponse} статистика
     */
    StatisticsResponse getStatistics();
}
