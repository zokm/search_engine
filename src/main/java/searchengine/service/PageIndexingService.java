package searchengine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.IndexingConfig;
import searchengine.model.dto.indexing.IndexPageResponse;
import searchengine.model.entity.IndexSearch;
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
import java.util.Map;

/**
 * Класс для лемматизации
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

    @Transactional
    public IndexPageResponse indexPage(String url) {
        if (!isUrlAllowed(url)) {
            return new IndexPageResponse(false,
                    "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }
        try {
            URI uri = URI.create(url);
            String siteUrl = uri.getScheme() + "://" + uri.getHost();
            String path = uri.getPath();
            if (path.isEmpty()) path = "/";
            Site site = siteRepository.findByUrl(siteUrl)
                    .orElseGet(() -> siteRepository.save(
                            Site.builder()
                                    .url(siteUrl)
                                    .name(siteUrl)
                                    .status(SiteStatus.INDEXING)
                                    .statusTime(LocalDateTime.now())
                                    .build()
                    ));
            pageRepository.findBySiteAndPath(site, path).ifPresent(existingPage -> {
                indexRepository.deleteByPage(existingPage);
                lemmaRepository.deleteByPage(existingPage);
                pageRepository.delete(existingPage);
            });
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(config.getUserAgent())
                    .referrer(config.getReferrer())
                    .timeout(10000)
                    .execute();
            String html = response.parse().html();
            int statusCode = response.statusCode();
            Page page = pageRepository.save(
                    Page.builder()
                            .site(site)
                            .path(path)
                            .code(statusCode)
                            .content(html)
                            .build()
            );
            Map<String, Integer> lemmaCounts = lemmaFinder.collectLemmas(html);
            for (Map.Entry<String, Integer> entry : lemmaCounts.entrySet()) {
                String lemmaText = entry.getKey();
                int rank = entry.getValue();
                Lemma lemma = lemmaRepository.findBySiteAndLemma(site, lemmaText)
                        .orElseGet(() -> lemmaRepository.save(
                                Lemma.builder()
                                        .site(site)
                                        .lemma(lemmaText)
                                        .frequency(0)
                                        .build()
                        ));
                lemma.setFrequency(lemma.getFrequency() + 1);
                lemmaRepository.save(lemma);
                indexRepository.save(
                        IndexSearch.builder()
                                .page(page)
                                .lemma(lemma)
                                .rank((float) rank)
                                .build()
                );
            }
            return new IndexPageResponse(true, null);
        } catch (Exception e) {
            log.error("Ошибка индексации страницы {}: {}", url, e.getMessage(), e);
            return new IndexPageResponse(false, "Не удалось проиндексировать страницу: " + e.getMessage());
        }
    }

    /**
     * Метод для проверки, что страница находится в пределах сайтов, указанных в конфигурации
     *
     * @param url {@link String} URL страница
     * @return true если страница в пределах сайтов, false иначе
     */
    private boolean isUrlAllowed(String url) {
        return config.getSites().stream()
                .anyMatch(siteConfig -> url.startsWith(siteConfig.getUrl()));
    }
}