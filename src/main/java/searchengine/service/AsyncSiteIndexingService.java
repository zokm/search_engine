package searchengine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.IndexingConfig;
import searchengine.config.SiteConfig;
import searchengine.model.entity.Site;
import searchengine.model.enums.SiteStatus;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для асинхронной индексации сайтов
 *
 * @author Tseliar Vladimir
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncSiteIndexingService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final IndexingConfig config;

    private final Map<String, ForkJoinPool> activePools = new ConcurrentHashMap<>();

    /**
     * Метод запускает индексацию сайта в асинхронном режиме
     *
     * @param siteConfig {@link SiteConfig} конфигурация сайта для индексации
     */
    @Async
    public void indexSiteAsync(SiteConfig siteConfig, IndexingService indexingService) {
        try {
            log.info("Начата индексация сайта: {}", siteConfig.getUrl());
            indexSite(siteConfig, indexingService);
            log.info("Завершена индексация сайта: {}", siteConfig.getUrl());
        } catch (Exception e) {
            log.error("Ошибка при индексации сайта {}: {}", siteConfig.getUrl(), e.getMessage(), e);
            siteRepository.findByUrl(siteConfig.getUrl()).ifPresent(site -> {
                site.setStatus(SiteStatus.FAILED);
                site.setLastError("Ошибка индексации: " + e.getMessage());
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            });
        } finally {
            activePools.remove(siteConfig.getUrl());
            indexingService.completeSiteIndexing();
        }
    }

    /**
     * Метод запускает индексацию сайта
     *
     * @param siteConfig {@link SiteConfig} конфигурация сайта для индексации
     */
    protected void indexSite(SiteConfig siteConfig, IndexingService indexingService) {
        Site site = null;
        try {
            clearSiteData(siteConfig.getUrl());
            site = Site.builder()
                    .url(siteConfig.getUrl())
                    .name(siteConfig.getName())
                    .status(SiteStatus.INDEXING)
                    .statusTime(LocalDateTime.now())
                    .build();
            site = siteRepository.save(site);
            Set<String> visited = ConcurrentHashMap.newKeySet();
            ForkJoinPool pool = new ForkJoinPool();
            activePools.put(siteConfig.getUrl(), pool);
            pool.invoke(new SiteIndexingTask(site, site.getUrl(), visited, pageRepository, siteRepository, config, indexingService));
            if (!indexingService.isStopRequested()) {
                site.setStatus(SiteStatus.INDEXED);
            }
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        } catch (Exception e) {
            log.error("Критическая ошибка при индексации сайта {}: {}",
                    siteConfig.getUrl(), e.getMessage(), e);
            if (site != null) {
                site.setStatus(SiteStatus.FAILED);
                site.setLastError("Критическая ошибка: " + e.getMessage());
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            }
            throw e;
        }
    }

    /**
     * Метод для остановки всех ForkJoinPool
     */
    public void stopAllForkJoinPools() {
        log.info("🛑 Останавливаем {} активных ForkJoinPool...", activePools.size());
        Map<String, ForkJoinPool> poolsCopy = new HashMap<>(activePools);
        int stoppedCount = 0;
        for (Map.Entry<String, ForkJoinPool> entry : poolsCopy.entrySet()) {
            String siteUrl = entry.getKey();
            ForkJoinPool pool = entry.getValue();
            if (pool != null && !pool.isShutdown()) {
                try {
                    log.info("Останавливаем ForkJoinPool для сайта: {}", siteUrl);
                    pool.shutdownNow();
                    pool.getQueuedSubmissionCount();
                    boolean terminated = pool.awaitTermination(3, TimeUnit.SECONDS);
                    if (terminated) {
                        log.info("ForkJoinPool для {} успешно остановлен", siteUrl);
                        stoppedCount++;
                    } else {
                        log.info("ForkJoinPool для {} не завершился вовремя", siteUrl);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Прерывание при остановке ForkJoinPool для {}", siteUrl, e);
                } catch (Exception e) {
                    log.error("Ошибка при остановке ForkJoinPool для {}: {}", siteUrl, e.getMessage());
                }
            }
            activePools.remove(siteUrl);
        }
        log.info("✅ Остановлено {} из {} ForkJoinPool", stoppedCount, poolsCopy.size());
        interruptAllForkJoinThreads();
    }

    /**
     * Принудительно прерываем все потоки ForkJoinPool
     */
    private void interruptAllForkJoinThreads() {
        try {
            Set<Thread> threads = Thread.getAllStackTraces().keySet();
            int interrupted = 0;
            for (Thread thread : threads) {
                if (thread.getName().contains("ForkJoinPool") || thread.getName().contains("ForkJoinWorkerThread")) {
                    try {
                        thread.interrupt();
                        interrupted++;
                    } catch (Exception e) {
                        log.error("Не удалось прервать поток {}: {}", thread.getName(), e.getMessage());
                    }
                }
            }
            log.info("Прервано {} потоков ForkJoinPool", interrupted);
        } catch (Exception e) {
            log.error("Ошибка при прерывании потоков: {}", e.getMessage());
        }
    }

    /**
     * Метод очищает данные сайта и его страниц с БД
     *
     * @param url {@link String} URL сайта
     */
    @Transactional
    public void clearSiteData(String url) {
        siteRepository.findByUrl(url).ifPresent(site -> {
            pageRepository.deleteBySite(site);
            siteRepository.delete(site);
        });
    }
}
