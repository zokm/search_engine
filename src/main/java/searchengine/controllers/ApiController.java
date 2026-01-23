package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.model.dto.indexing.IndexPageResponse;
import searchengine.model.dto.indexing.IndexingResponseDTO;
import searchengine.model.dto.search.SearchResponse;
import searchengine.model.dto.statistics.StatisticsResponse;
import searchengine.service.IndexingService;
import searchengine.service.PageIndexingService;
import searchengine.service.SearchService;
import searchengine.service.StatisticsService;

/**
 * REST-контроллер API для индексации и статистики.
 *
 * @author Tseliar Vladimir
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final PageIndexingService pageIndexingService;
    private final SearchService searchService;

    /**
     * Возвращает статистику по индексации и текущему состоянию индекса.
     *
     * @return {@link ResponseEntity}<{@link StatisticsResponse}> с ответом со статистикой
     */
    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    /**
     * Запускает полную индексацию всех сайтов из конфигурации.
     *
     * @return {@link ResponseEntity}<{@link IndexingResponseDTO}> с ответом о результате запуска
     */
    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponseDTO> startIndexing() {
        return ResponseEntity.ok(indexingService.startIndexing());
    }

    /**
     * Останавливает текущую индексацию.
     *
     * @return {@link ResponseEntity}<{@link IndexingResponseDTO}> с ответом о результате остановки
     */
    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponseDTO> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }

    /**
     * Переиндексирует одну страницу по URL.
     *
     * @param url {@link String} URL страницы
     * @return {@link ResponseEntity}<{@link IndexPageResponse}> с ответом о результате операции
     */
    @PostMapping("/indexPage")
    public ResponseEntity<IndexPageResponse> indexPage(@RequestParam("url") String url) {
        IndexPageResponse response = pageIndexingService.indexPage(url);
        if (response.isResult()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Выполняет поиск по запросу.
     *
     * @param query {@link String} поисковый запрос
     * @param site {@link String} базовый URL сайта (опционально)
     * @param offset смещение (по умолчанию 0)
     * @param limit количество результатов (по умолчанию 20)
     * @return {@link ResponseEntity}<{@link SearchResponse}> с ответом поиска
     */
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "site", required = false) String site,
            @RequestParam(name = "offset", required = false, defaultValue = "0") int offset,
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit
    ) {
        SearchResponse response = searchService.search(query, site, offset, limit);
        if (response.isResult()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }
}
