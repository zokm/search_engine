package searchengine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.IndexingConfig;
import searchengine.config.SiteConfig;
import searchengine.model.dto.indexing.IndexPageResponse;
import searchengine.model.entity.Lemma;
import searchengine.model.entity.Page;
import searchengine.model.entity.Site;
import searchengine.model.enums.SiteStatus;
import searchengine.repository.IndexSearchRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Сервис индексации отдельной страницы (эндпоинт {@code /api/indexPage}).
 * 
 * @author Tseliar Vladimir
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PageIndexingService {

    private final IndexingConfig config;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexSearchRepository indexRepository;
    private final LemmaFinder lemmaFinder;
    private final LemmaIndexingService lemmaIndexingService;

    /**
     * Индексирует одну страницу по URL: сохраняет страницу в БД, извлекает леммы и сохраняет связи в индексе.
     *
     * @param url {@link String} URL страницы
     * @return {@link IndexPageResponse} DTO-ответ о результате операции
     */
    @Transactional
    public IndexPageResponse indexPage(String url) {
        SiteConfig siteConfig = findSiteConfig(url);
        if (siteConfig == null) {
            return new IndexPageResponse(false,
                    "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }
        Site site = null;
        boolean siteExisted = false;
        SiteStatus previousStatus = null;
        String previousError = null;
        try {
            URI uri = URI.create(url);
            String siteUrl = siteConfig.getUrl();
            String path = uri.getPath();
            if (path.isEmpty()) path = "/";
            site = siteRepository.findByUrl(siteUrl).orElse(null);
            siteExisted = site != null;
            if (siteExisted) {
                previousStatus = site.getStatus();
                previousError = site.getLastError();
            } else {
                site = siteRepository.save(
                        Site.builder()
                                .url(siteUrl)
                                .name(siteConfig.getName())
                                .status(SiteStatus.INDEXING)
                                .statusTime(LocalDateTime.now())
                                .build()
                );
            }

            Connection.Response response = Jsoup.connect(url)
                    .userAgent(config.getUserAgent())
                    .referrer(config.getReferrer())
                    .timeout(10000)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .execute();
            int statusCode = response.statusCode();
            if (statusCode >= 400) {
                updateSiteAfterFailure(site, siteExisted, previousStatus, previousError,
                        "Ошибка индексации страницы: HTTP " + statusCode);
                return new IndexPageResponse(false, "Не удалось проиндексировать страницу: HTTP " + statusCode);
            }
            String contentType = response.contentType();
            if (contentType == null || !(contentType.startsWith("text/") || contentType.contains("xml"))) {
                updateSiteAfterFailure(site, siteExisted, previousStatus, previousError,
                        "Ошибка индексации страницы: неподдерживаемый Content-Type " + contentType);
                return new IndexPageResponse(false, "Не удалось проиндексировать страницу: неподдерживаемый Content-Type " + contentType);
            }
            String html = response.body();

            pageRepository.findBySiteAndPath(site, path)
                    .ifPresent(this::removePageData);

            Page page = pageRepository.save(
                    Page.builder()
                            .site(site)
                            .path(path)
                            .code(statusCode)
                            .content(html)
                            .build()
            );
            Map<String, Integer> lemmaCounts = lemmaFinder.collectLemmas(html);
            if (lemmaCounts != null && !lemmaCounts.isEmpty()) {
                saveLemmasWithRetry(page, lemmaCounts, site);
            }
            if (siteExisted && previousStatus == SiteStatus.INDEXING) {
                site.setStatus(SiteStatus.INDEXING);
            } else {
                site.setStatus(SiteStatus.INDEXED);
            }
            site.setStatusTime(LocalDateTime.now());
            site.setLastError(null);
            siteRepository.save(site);
            return new IndexPageResponse(true, null);
        } catch (UnsupportedMimeTypeException e) {
            log.debug("Неподдерживаемый content-type при индексации {}: {}", url, e.getMimeType());
            updateSiteAfterFailure(site, siteExisted, previousStatus, previousError,
                    "Ошибка индексации страницы: неподдерживаемый Content-Type " + e.getMimeType());
            return new IndexPageResponse(false, "Не удалось проиндексировать страницу: неподдерживаемый Content-Type " + e.getMimeType());
        } catch (Exception e) {
            log.error("Ошибка индексации страницы {}: {}", url, e.getMessage(), e);
            updateSiteAfterFailure(site, siteExisted, previousStatus, previousError,
                    "Ошибка индексации страницы: " + e.getMessage());
            return new IndexPageResponse(false, "Не удалось проиндексировать страницу: " + e.getMessage());
        }
    }

    /**
     * Обновляет статус сайта после неудачной попытки индексации страницы.
     *
     * <p>Если сайт уже существовал в БД, статус и lastError возвращаются к предыдущим значениям,
     * чтобы не оставлять сайт в состоянии INDEXING из-за ошибки одной страницы.</p>
     *
     * <p>Если сайт был создан в рамках текущего вызова, он переводится в статус FAILED и получает lastError.</p>
     *
     * @param site {@link Site} сайт
     * @param siteExisted признак, что сайт существовал до вызова
     * @param previousStatus {@link SiteStatus} статус сайта до попытки индексации страницы
     * @param previousError {@link String} lastError сайта до попытки индексации страницы
     * @param error {@link String} текст ошибки для нового сайта
     */
    private void updateSiteAfterFailure(
            Site site,
            boolean siteExisted,
            SiteStatus previousStatus,
            String previousError,
            String error
    ) {
        if (site == null) {
            return;
        }
        if (siteExisted) {
            site.setStatus(previousStatus);
            site.setLastError(previousError);
        } else {
            site.setStatus(SiteStatus.FAILED);
            site.setLastError(error);
        }
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }

    /**
     * Находит конфигурацию сайта, которому принадлежит URL, либо возвращает {@code null}.
     *
     * @param url {@link String} URL страницы
     * @return {@link SiteConfig} конфигурация сайта или {@code null}
     */
    private SiteConfig findSiteConfig(String url) {
        for (SiteConfig siteConfig : config.getSites()) {
            String baseUrl = siteConfig.getUrl();
            if (url.startsWith(baseUrl)) {
                return siteConfig;
            }
            if (baseUrl.endsWith("/")) {
                String withoutSlash = baseUrl.substring(0, baseUrl.length() - 1);
                if (url.startsWith(withoutSlash)) {
                    return siteConfig;
                }
            }
        }
        return null;
    }

    /**
     * Удаляет информацию о странице перед переиндексацией: записи в {@code search_index}, саму страницу,
     * а также удаляет/пересчитывает frequency у затронутых лемм.
     *
     * @param page {@link Page} страница
     */
    private void removePageData(Page page) {
        List<Lemma> lemmas = lemmaRepository.findLemmasByPage(page);
        indexRepository.deleteByPage(page);
        pageRepository.delete(page);

        for (Lemma lemma : lemmas) {
            long usage = lemmaRepository.countUsageByLemma(lemma);
            if (usage <= 0) {
                lemmaRepository.delete(lemma);
            } else {
                lemma.setFrequency((int) usage);
                lemmaRepository.save(lemma);
            }
        }
    }

    /**
     * Сохраняет леммы/индекс с повторными попытками при дедлоках в MySQL.
     *
     * @param page {@link Page} страница
     * @param lemmaCounts {@link Map}<{@link String}, {@link Integer}> леммы и их количество на странице
     * @param site {@link Site} сайт
     */
    private void saveLemmasWithRetry(Page page, Map<String, Integer> lemmaCounts, Site site) {
        int attempt = 0;
        while (true) {
            try {
                lemmaIndexingService.saveLemmasAndIndex(page, lemmaCounts, site);
                return;
            } catch (CannotAcquireLockException e) {
                if (attempt++ >= 5) {
                    throw e;
                }
                try {
                    Thread.sleep(100L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }
}
