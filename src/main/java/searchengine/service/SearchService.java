package searchengine.service;

import searchengine.model.dto.search.SearchResponse;

/**
 * Сервис поиска страниц по запросу.
 * 
 * @author Tseliar Vladimir
 */
public interface SearchService {

    /**
     * Выполняет поиск по запросу.
     *
     * @param query {@link String} поисковый запрос
     * @param siteUrl {@link String} базовый URL сайта для поиска (если задан)
     * @param offset смещение (постраничный вывод)
     * @param limit количество результатов
     * @return {@link SearchResponse} ответ API поиска
     */
    SearchResponse search(String query, String siteUrl, int offset, int limit);
}

