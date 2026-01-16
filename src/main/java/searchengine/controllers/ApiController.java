package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.model.dto.indexing.IndexPageResponse;
import searchengine.model.dto.indexing.IndexingResponseDTO;
import searchengine.model.dto.statistics.StatisticsResponse;
import searchengine.service.IndexingService;
import searchengine.service.PageIndexingService;
import searchengine.service.StatisticsService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final PageIndexingService pageIndexingService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponseDTO> startIndexing() {
        return ResponseEntity.ok(indexingService.startIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponseDTO> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }

    @PostMapping("/indexPage")
    public IndexPageResponse indexPage(@RequestParam("url") String url) {
        return pageIndexingService.indexPage(url);
    }
}
