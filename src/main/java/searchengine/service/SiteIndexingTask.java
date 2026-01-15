package searchengine.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.util.StringUtils;
import searchengine.config.IndexingConfig;
import searchengine.model.entity.Page;
import searchengine.model.entity.Site;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Класс задачи индексации сайта
 *
 * @author Tseliar Vladimir
 */
@Slf4j
public class SiteIndexingTask extends RecursiveAction {

    private final Site site;
    private final String url;
    private final Set<String> visited;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final IndexingConfig config;
    private final IndexingService indexingService;

    public SiteIndexingTask(Site site, String url, Set<String> visited, PageRepository pageRepository,
                            SiteRepository siteRepository, IndexingConfig config, IndexingService indexingService) {
        this.site = site;
        this.url = url;
        this.visited = visited;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.config = config;
        this.indexingService = indexingService;
    }

    /**
     * Метод запуска задачи
     */
    @Override
    protected void compute() {
        if (indexingService.isStopRequested() || !visited.add(url)) {
            return;
        }
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(100, 500));
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(config.getUserAgent())
                    .referrer(config.getReferrer())
                    .timeout(10000)
                    .execute();

            Document doc = response.parse();
            savePage(doc.html(), response.statusCode());
            updateStatusTime();
            if (!indexingService.isStopRequested()) {
                List<SiteIndexingTask> tasks = new ArrayList<>();
                for (Element link : doc.select("a[href]")) {
                    String absUrl = link.attr("abs:href");
                    if (isValid(absUrl)) {
                        tasks.add(new SiteIndexingTask(site, absUrl, visited, pageRepository, siteRepository, config, indexingService));
                    }
                }
                if (!tasks.isEmpty()) {
                    invokeAll(tasks);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Метод сохранения страницы
     *
     * @param content {@link String} содержимое страницы
     * @param code    {@link Integer} код ответа сервера
     */
    private void savePage(String content, int code) {
        URI uri = URI.create(url);
        String path = uri.getPath();
        if (!StringUtils.hasText(path)) {
            path = "/";
        }
        if (!StringUtils.hasText(path)) path = "/";

        if (pageRepository.existsBySiteAndPath(site, path)) {
            return;
        }
        pageRepository.save(Page.builder()
                .site(site)
                .path(path)
                .code(code)
                .content(content)
                .build());
    }

    /**
     * Метод обновления времени последнего обновления сайта
     */
    private void updateStatusTime() {
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }

    /**
     * Метод проверки ссылки на валидность
     *
     * @param link {@link String} ссылка на страницу
     * @return {@link Boolean} результат проверки true - валидная, false - не валидная
     */
    private boolean isValid(String link) {
        return link.startsWith(site.getUrl())
                && !link.contains("#")
                && !link.contains("?")
                && !link.endsWith(".pdf");
    }
}

