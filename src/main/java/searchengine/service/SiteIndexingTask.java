package searchengine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.UnsupportedMimeTypeException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.util.StringUtils;
import searchengine.config.IndexingConfig;
import searchengine.model.entity.Page;
import searchengine.model.entity.Site;
import searchengine.model.enums.SiteStatus;
import searchengine.repository.IndexSearchRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Задача ForkJoin: обходит одну страницу, сохраняет её и планирует обход дочерних ссылок.
 * 
 * @author Tseliar Vladimir
 */
@Slf4j
@RequiredArgsConstructor
public class SiteIndexingTask extends RecursiveAction {

    private final Site site;
    private final String url;
    private final Set<String> visited;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final IndexingConfig config;
    private final IndexingService indexingService;
    private final LemmaFinder lemmaFinder;
    private final LemmaRepository lemmaRepository;
    private final IndexSearchRepository indexRepository;
    private final LemmaIndexingService lemmaIndexingService;

    /**
     * Выполняет обход текущего URL и планирует задачи для найденных ссылок.
     */
    @Override
    protected void compute() {
        if (indexingService.isStopRequested() || !visited.add(url)) {
            return;
        }
        try {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(100, 500));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
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
                log.warn("Пропускаем страницу с кодом {}: {}", statusCode, url);
                return;
            }
            String contentType = response.contentType();
            if (contentType == null || !(contentType.startsWith("text/") || contentType.contains("xml"))) {
                log.debug("Пропускаем не-текстовый контент ({}): {}", contentType, url);
                return;
            }

            Document doc = response.parse();
            savePage(doc.html(), response.statusCode());
            updateStatusTime();
            if (!indexingService.isStopRequested()) {
                List<SiteIndexingTask> tasks = new ArrayList<>();
                for (Element link : doc.select("a[href]")) {
                    String absUrl = link.attr("abs:href");
                    if (isValid(absUrl)) {
                        tasks.add(new SiteIndexingTask(site, absUrl, visited, pageRepository, siteRepository, config,
                                indexingService, lemmaFinder, lemmaRepository, indexRepository, lemmaIndexingService));
                    }
                }
                if (!tasks.isEmpty()) {
                    invokeAll(tasks);
                }
            }
        } catch (UnsupportedMimeTypeException e) {
            log.debug("Пропускаем неподдерживаемый content-type: {} ({})", e.getMimeType(), url);
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted() || indexingService.isStopRequested()) {
                return;
            }
            log.error("Ошибка при обработке страницы {}: {}", url, e.getMessage(), e);
            try {
                Site currentSite = siteRepository.findById(site.getId()).orElse(null);
                if (currentSite != null) {
                    currentSite.setStatus(SiteStatus.FAILED);
                    currentSite.setLastError("Ошибка при обработке страницы: " + e.getMessage());
                    currentSite.setStatusTime(LocalDateTime.now());
                    siteRepository.save(currentSite);
                }
            } catch (Exception siteUpdateError) {
                log.debug("Не удалось обновить статус сайта (возможно, он был удален): {}", siteUpdateError.getMessage());
            }
        }
    }

    /**
     * Сохраняет страницу и запускает сохранение лемм/индекса.
     *
     * @param content {@link String} HTML-код страницы
     * @param code HTTP-код ответа
     */
    private void savePage(String content, int code) {
        try {
            if (indexingService.isStopRequested()) {
                log.debug("Остановка индексации запрошена, пропускаем сохранение страницы: {}", url);
                return;
            }
            
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (!StringUtils.hasText(path)) {
                path = "/";
            }
            Site currentSite = siteRepository.findById(site.getId()).orElse(null);
            
            if (currentSite == null) {
                if (indexingService.isStopRequested()) {
                    log.debug("Сайт с id {} не найден в БД, но остановка запрошена - это нормально", site.getId());
                    return;
                }
                log.warn("Сайт с id {} не найден в БД при сохранении страницы {}", site.getId(), url);
                return;
            }
            
            if (pageRepository.existsBySiteAndPath(currentSite, path)) {
                return;
            }
            
            Page page = pageRepository.save(
                    Page.builder()
                            .site(currentSite)
                            .path(path)
                            .code(code)
                            .content(content)
                            .build()
            );
            
            try {
                Map<String, Integer> lemmaCounts = lemmaFinder.collectLemmas(content);
                if (lemmaCounts != null && !lemmaCounts.isEmpty()) {
                    saveLemmasWithRetry(page, lemmaCounts, currentSite);
                    log.debug("Сохранена страница: {} для сайта: {} (лемм: {})", path, currentSite.getUrl(), lemmaCounts.size());
                } else {
                    log.warn("Не найдено лемм на странице: {} для сайта: {}", path, currentSite.getUrl());
                }
            } catch (Exception lemmaError) {
                log.error("Ошибка при сохранении лемм для страницы {}: {}", url, lemmaError.getMessage(), lemmaError);
            }
        } catch (Exception e) {
            if (indexingService.isStopRequested()) {
                log.debug("Ошибка при сохранении страницы {} во время остановки индексации: {}", url, e.getMessage());
                return;
            }
            log.error("Ошибка при сохранении страницы {}: {}", url, e.getMessage(), e);
        }
    }

    /**
     * Сохраняет леммы и строки индекса с повторными попытками при дедлоках в БД.
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
                    Thread.sleep(100L * attempt + ThreadLocalRandom.current().nextInt(0, 150));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Обновляет поле {@link Site#getStatusTime()} для текущего сайта.
     */
    private void updateStatusTime() {
        try {
            Site currentSite = siteRepository.findById(site.getId()).orElse(null);
            if (currentSite != null) {
                currentSite.setStatusTime(LocalDateTime.now());
                siteRepository.save(currentSite);
            }
        } catch (Exception e) {
            log.debug("Не удалось обновить время статуса сайта: {}", e.getMessage());
        }
    }

    /**
     * Проверяет, нужно ли обходить ссылку.
     *
     * @param link {@link String} абсолютный URL
     * @return true, если ссылку нужно обходить
     */
    private boolean isValid(String link) {
        if (!StringUtils.hasText(link)) {
            return false;
        }
        if (link.startsWith("mailto:") || link.startsWith("tel:") || link.startsWith("javascript:")) {
            return false;
        }
        String base = normalizeBaseUrl(site.getUrl());
        String normalizedLink = normalizeBaseUrl(link);
        if (!normalizedLink.startsWith(base)) {
            return false;
        }
        String lower = normalizedLink.toLowerCase();
        if (hasBannedExtension(lower)) {
            return false;
        }
        return !lower.contains("#")
                && !lower.contains("?")
                && !lower.endsWith(".pdf");
    }

    /**
     * Нормализует URL: trim и удаление завершающих слешей.
     *
     * @param url {@link String} URL
     * @return {@link String} нормализованный URL
     */
    private static String normalizeBaseUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Возвращает true для URL, которые похожи на не-HTML ресурсы (картинки, шрифты, архивы и т.п.).
     *
     * @param url {@link String} нормализованный URL (в нижнем регистре)
     * @return true, если расширение запрещено для обхода
     */
    private static boolean hasBannedExtension(String url) {
        return url.endsWith(".jpg") || url.endsWith(".jpeg") || url.endsWith(".png") || url.endsWith(".gif")
                || url.endsWith(".webp") || url.endsWith(".svg") || url.endsWith(".ico")
                || url.endsWith(".css") || url.endsWith(".js") || url.endsWith(".json")
                || url.endsWith(".zip") || url.endsWith(".rar") || url.endsWith(".7z")
                || url.endsWith(".mp3") || url.endsWith(".mp4") || url.endsWith(".avi") || url.endsWith(".webm")
                || url.endsWith(".woff") || url.endsWith(".woff2") || url.endsWith(".ttf") || url.endsWith(".eot");
    }
}
