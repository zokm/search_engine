package searchengine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import searchengine.config.IndexingConfig;
import searchengine.config.SiteConfig;
import searchengine.model.entity.Page;
import searchengine.model.entity.Site;
import searchengine.model.enums.SiteStatus;
import searchengine.repository.IndexSearchRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * Сервис асинхронной индексации сайтов из конфигурации.
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
    private final LemmaFinder lemmaFinder;
    private final LemmaRepository lemmaRepository;
    private final IndexSearchRepository indexRepository;
    private final LemmaIndexingService lemmaIndexingService;
    private final TransactionTemplate transactionTemplate;

    private final Map<String, ForkJoinPool> activePools = new ConcurrentHashMap<>();

    /**
     * Запускает индексацию одного сайта в асинхронном потоке.
     *
     * @param siteConfig {@link SiteConfig} конфигурация сайта
     * @param indexingService {@link IndexingService} сервис состояния индексации
     */
    @Async
    public void indexSiteAsync(SiteConfig siteConfig, IndexingService indexingService) {
        try {
            log.info("Начата индексация сайта: {}", siteConfig.getUrl());
            indexSite(siteConfig, indexingService);
            log.info("Завершена индексация сайта: {}", siteConfig.getUrl());
        } catch (Exception e) {
            log.error("Ошибка при индексации сайта {}: {}", siteConfig.getUrl(), e.getMessage(), e);
            List<Site> sites = siteRepository.findAllByUrl(siteConfig.getUrl());
            if (!sites.isEmpty()) {
                sites.forEach(site -> {
                    site.setStatus(SiteStatus.FAILED);
                    site.setLastError("Ошибка индексации: " + e.getMessage());
                    site.setStatusTime(LocalDateTime.now());
                    siteRepository.save(site);
                });
            }
        } finally {
            activePools.remove(siteConfig.getUrl());
            indexingService.completeSiteIndexing();
        }
    }

    /**
     * Выполняет индексацию одного сайта с обходом ссылок в {@link ForkJoinPool}.
     *
     * @param siteConfig {@link SiteConfig} конфигурация сайта
     * @param indexingService {@link IndexingService} сервис состояния индексации
     */
    protected void indexSite(SiteConfig siteConfig, IndexingService indexingService) {
        if (indexingService.isStopRequested()) {
            log.info("Остановка индексации запрошена, пропускаем индексацию сайта: {}", siteConfig.getUrl());
            return;
        }
        
        Site site = null;
        try {
            transactionTemplate.executeWithoutResult(status -> clearSiteData(siteConfig.getUrl()));
            
            if (indexingService.isStopRequested()) {
                log.info("Остановка индексации запрошена после очистки данных, пропускаем создание сайта: {}", siteConfig.getUrl());
                return;
            }
            
            site = transactionTemplate.execute(status -> siteRepository.save(
                    Site.builder()
                            .url(siteConfig.getUrl())
                            .name(siteConfig.getName())
                            .status(SiteStatus.INDEXING)
                            .statusTime(LocalDateTime.now())
                            .build()
            ));
            if (site == null || site.getId() == null) {
                throw new IllegalStateException("Не удалось создать запись Site перед индексацией");
            }
            
            Set<String> visited = ConcurrentHashMap.newKeySet();
            ForkJoinPool pool = new ForkJoinPool(determineParallelism());
            activePools.put(siteConfig.getUrl(), pool);
            pool.invoke(new SiteIndexingTask(site, site.getUrl(), visited, pageRepository, siteRepository, config,
                    indexingService, lemmaFinder, lemmaRepository, indexRepository, lemmaIndexingService));
            
            Integer siteId = site.getId();
            transactionTemplate.executeWithoutResult(status -> {
                Site current = siteRepository.findById(siteId).orElse(null);
                if (current == null) {
                    return;
                }
                if (!indexingService.isStopRequested()) {
                    current.setStatus(SiteStatus.INDEXED);
                    current.setLastError(null);
                }
                current.setStatusTime(LocalDateTime.now());
                siteRepository.save(current);
            });
        } catch (Exception e) {
            log.error("Критическая ошибка при индексации сайта {}: {}",
                    siteConfig.getUrl(), e.getMessage(), e);
            if (site != null && site.getId() != null) {
                try {
                    Site currentSite = siteRepository.findById(site.getId()).orElse(null);
                    if (currentSite != null) {
                        currentSite.setStatus(SiteStatus.FAILED);
                        currentSite.setLastError("Критическая ошибка: " + e.getMessage());
                        currentSite.setStatusTime(LocalDateTime.now());
                        siteRepository.save(currentSite);
                        siteRepository.flush();
                    } else {
                        log.debug("Сайт с id {} не найден в БД при попытке обновить статус при ошибке", site.getId());
                    }
                } catch (Exception ex) {
                    if (ex instanceof org.hibernate.StaleStateException || 
                        ex instanceof org.hibernate.OptimisticLockException) {
                        log.debug("Сайт был удален из БД, не можем обновить его статус: {}", ex.getMessage());
                    } else {
                        log.error("Не удалось обновить статус сайта при ошибке: {}", ex.getMessage(), ex);
                    }
                }
            }
            throw e;
        }
    }

    /**
     * Останавливает все активные {@link ForkJoinPool}.
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
     * Пытается прервать потоки ForkJoinPool (best effort).
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
     * Удаляет из БД все данные по сайту с указанным URL.
     *
     * @param url {@link String} базовый URL сайта
     */
    public void clearSiteData(String url) {
        List<Site> sites = siteRepository.findAllByUrl(url);
        if (sites.isEmpty()) {
            log.debug("Сайт {} не найден в БД, очистка не требуется", url);
            return;
        }

        try {
            log.info("Начинаем очистку данных для сайта: {} (найдено записей: {})", url, sites.size());
            
            for (Site site : sites) {
                List<Page> pages = pageRepository.findBySite(site);
                for (Page page : pages) {
                    indexRepository.deleteByPage(page);
                }
            }
            siteRepository.deleteAll(sites);
            siteRepository.flush();
            log.info("Данные сайта {} очищены успешно (удалено записей: {})", url, sites.size());
        } catch (Exception e) {
            log.error("Ошибка при очистке данных сайта {}: {}", url, e.getMessage(), e);
            throw new RuntimeException("Не удалось очистить данные сайта: " + url, e);
        }
    }

    /**
     * Возвращает уровень параллелизма для обхода ссылок.
     *
     * @return параллелизм
     */
    private static int determineParallelism() {
        return 2;
    }
}
