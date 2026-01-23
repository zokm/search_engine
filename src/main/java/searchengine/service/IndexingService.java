package searchengine.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.IndexingConfig;
import searchengine.config.SiteConfig;
import searchengine.model.dto.indexing.IndexingResponseDTO;
import searchengine.model.entity.Site;
import searchengine.model.enums.SiteStatus;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис управления индексацией: запуск, остановка и состояние процесса.
 * 
 * @author Tseliar Vladimir
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Getter
public class IndexingService {

    private final IndexingConfig config;
    private final AsyncSiteIndexingService asyncService;
    private final SiteRepository siteRepository;

    private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);
    private final AtomicInteger activeSites = new AtomicInteger(0);
    private volatile boolean stopRequested = false;

    /**
     * Возвращает признак того, что пользователь запросил остановку индексации.
     *
     * @return true, если остановка запрошена
     */
    public boolean isStopRequested() {
        return stopRequested;
    }

    /**
     * Запускает индексацию всех сайтов из конфигурации.
     *
     * @return {@link IndexingResponseDTO} DTO-ответ о результате запуска
     */
    public IndexingResponseDTO startIndexing() {
        if (!indexingInProgress.compareAndSet(false, true)) {
            return new IndexingResponseDTO(false, "Индексация уже запущена");
        }
        stopRequested = false;
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
     * Отмечает завершение индексации одного сайта (вызывается из {@link AsyncSiteIndexingService}).
     */
    public void completeSiteIndexing() {
        if (activeSites.decrementAndGet() <= 0) {
            log.info("🏁 Все сайты проиндексированы, сбрасываем флаг индексации");
            indexingInProgress.set(false);
            stopRequested = false;
            if (activeSites.get() < 0) {
                activeSites.set(0);
            }
        }
    }

    /**
     * Останавливает текущую индексацию.
     *
     * @return {@link IndexingResponseDTO} DTO-ответ о результате остановки
     */
    public synchronized IndexingResponseDTO stopIndexing() {
        if (!indexingInProgress.get()) {
            log.info("Попытка остановить индексацию, но она не запущена");
            return new IndexingResponseDTO(false, "Индексация не запущена");
        }
        log.info("🛑 Получен запрос на остановку индексации...");
        stopRequested = true;
        asyncService.stopAllForkJoinPools();
        updateAllSitesToFailed();
        indexingInProgress.set(false);
        activeSites.set(0);
        log.info("✅ Индексация остановлена. Флаги сброшены.");
        return new IndexingResponseDTO(true);
    }

    /**
     * Переводит все сайты из конфигурации в статус {@link SiteStatus#FAILED} с указанием причины остановки.
     */
    private void updateAllSitesToFailed() {
        try {
            List<SiteConfig> siteConfigs = config.getSites();
            for (SiteConfig siteConfig : siteConfigs) {
                String url = siteConfig.getUrl();
                List<Site> sites = siteRepository.findAllByUrl(url);
                if (!sites.isEmpty()) {
                    for (Site site : sites) {
                        site.setStatus(SiteStatus.FAILED);
                        site.setLastError("Индексация остановлена пользователем");
                        site.setStatusTime(LocalDateTime.now());
                        siteRepository.save(site);
                    }
                } else {
                    log.debug("Сайт {} не найден при остановке индексации, пропускаем создание", url);
                }
            }
            log.info("Обновлено статусов для остановки индексации");
        } catch (Exception e) {
            log.error("Ошибка при обновлении статусов: {}", e.getMessage(), e);
        }
    }
}
