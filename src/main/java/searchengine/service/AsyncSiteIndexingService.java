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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

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

    /**
     * Метод запускает индексацию сайта в асинхронном режиме
     *
     * @param siteConfig {@link SiteConfig} конфигурация сайта для индексации
     */
    @Async
    public void indexSiteAsync(SiteConfig siteConfig, IndexingService indexingService) {
        try {
            indexSite(siteConfig);
        } catch (Exception e) {
            siteRepository.findByUrl(siteConfig.getUrl()).ifPresent(site -> {
                site.setStatus(SiteStatus.FAILED);
                site.setLastError("Ошибка индексации: " + e.getMessage());
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            });
        } finally {
            indexingService.completeSiteIndexing();
        }
    }

    /**
     * Метод запускает индексацию сайта
     *
     * @param siteConfig {@link SiteConfig} конфигурация сайта для индексации
     */
    protected void indexSite(SiteConfig siteConfig) {
        try {
            clearSiteData(siteConfig.getUrl());
            Site site = siteRepository.save(
                    Site.builder()
                            .url(siteConfig.getUrl())
                            .name(siteConfig.getName())
                            .status(SiteStatus.INDEXING)
                            .statusTime(LocalDateTime.now())
                            .build()
            );
            Set<String> visited = ConcurrentHashMap.newKeySet();
            ForkJoinPool pool = new ForkJoinPool();
            pool.invoke(new SiteIndexingTask(site, site.getUrl(), visited, pageRepository, siteRepository, config));
            site.setStatus(SiteStatus.INDEXED);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

        } catch (Exception e) {
            siteRepository.findByUrl(siteConfig.getUrl()).ifPresent(site -> {
                site.setStatus(SiteStatus.FAILED);
                site.setLastError(e.getMessage());
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            });
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
