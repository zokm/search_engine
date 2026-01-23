package searchengine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import searchengine.config.IndexingConfig;
import searchengine.config.SiteConfig;
import searchengine.model.dto.statistics.DetailedStatisticsItem;
import searchengine.model.dto.statistics.StatisticsData;
import searchengine.model.dto.statistics.StatisticsResponse;
import searchengine.model.dto.statistics.TotalStatistics;
import searchengine.model.entity.Site;
import searchengine.model.enums.SiteStatus;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Реализация статистики на основе данных из БД и конфигурации сайтов.
 * 
 * @author Tseliar Vladimir
 */
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final IndexingConfig sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexingService indexingService;

    /**
     * Формирует общую и детальную статистику по сайтам.
     *
     * @return {@link StatisticsResponse} ответ API статистики
     */
    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setPages(safeLongToInt(pageRepository.count()));
        total.setLemmas(safeLongToInt(lemmaRepository.count()));
        total.setIndexing(indexingService.getIndexingInProgress().get());

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<SiteConfig> sitesList = sites.getSites();
        for (SiteConfig siteConfig : sitesList) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(siteConfig.getName());
            item.setUrl(siteConfig.getUrl());
            Site site = siteRepository.findByUrl(siteConfig.getUrl()).orElse(null);
            if (site == null) {
                item.setStatus(SiteStatus.FAILED.name());
                item.setStatusTime(0L);
                item.setPages(0);
                item.setLemmas(0);
                item.setError("Индексация не запускалась");
            } else {
                item.setStatus(site.getStatus().name());
                item.setStatusTime(toEpochSeconds(site.getStatusTime()));
                item.setPages(safeLongToInt(pageRepository.countBySite(site)));
                item.setLemmas(safeLongToInt(lemmaRepository.countBySite(site)));
                if (site.getStatus() == SiteStatus.FAILED && StringUtils.hasText(site.getLastError())) {
                    item.setError(site.getLastError());
                }
            }
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    /**
     * Преобразует {@link LocalDateTime} в секунды Unix epoch.
     *
     * @param time {@link LocalDateTime} дата/время
     * @return секунды Unix epoch
     */
    private static long toEpochSeconds(LocalDateTime time) {
        if (time == null) {
            return 0L;
        }
        return time.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    /**
     * Безопасно приводит long к int (с ограничением диапазона).
     *
     * @param value значение long
     * @return значение int в допустимом диапазоне
     */
    private static int safeLongToInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }
}
