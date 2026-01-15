package searchengine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.IndexingConfig;
import searchengine.config.SiteConfig;
import searchengine.model.dto.indexing.IndexingResponseDTO;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис для запуска и управления индексацией сайтов
 *
 * @author Tseliar Vladimir
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IndexingService {

    private final IndexingConfig config;
    private final AsyncSiteIndexingService asyncService;

    private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);
    private final AtomicInteger activeSites = new AtomicInteger(0);

    /**
     * Метод запускает индексацию сайтов из конфигурации по одному в асинхронном режиме
     *
     * @return {@link IndexingResponseDTO} результат запуска
     */
    public IndexingResponseDTO startIndexing() {
        if (!indexingInProgress.compareAndSet(false, true)) {
            return new IndexingResponseDTO(false, "Индексация уже запущена");
        }
        log.info("🚀 Запуск индексации всех сайтов...");
        activeSites.set(0);
        List<SiteConfig> sites = config.getSites();
        activeSites.set(sites.size());
        for (SiteConfig site : sites) {
            asyncService.indexSiteAsync(site, this);
        }
        return new IndexingResponseDTO(true);
    }

    /**
     * Метод для завершения индексации одного сайта.
     * Вызывается из AsyncSiteIndexingService
     */
    public void completeSiteIndexing() {
        if (activeSites.decrementAndGet() <= 0) {
            log.info("🏁 Все сайты проиндексированы, сбрасываем флаг индексации");
            indexingInProgress.set(false);
            if (activeSites.get() < 0) {
                activeSites.set(0);
            }
        }
    }

    /**
     * Метод для принудительной остановки индексации
     *
     * @return {@link IndexingResponseDTO} результат остановки
     */
    public IndexingResponseDTO stopIndexing() {
        if (!indexingInProgress.compareAndSet(false, true)) {
            return new IndexingResponseDTO(false, "Индексация не запущена");
        }
        indexingInProgress.set(false);
        activeSites.set(0);
        log.info("🛑 Индексация принудительно остановлена");
        return new IndexingResponseDTO(true);
    }
}
