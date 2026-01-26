package searchengine.service;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import searchengine.config.IndexingConfig;
import searchengine.config.SiteConfig;
import searchengine.model.dto.search.SearchResponse;
import searchengine.model.dto.search.SearchResultItem;
import searchengine.model.entity.Lemma;
import searchengine.model.entity.Page;
import searchengine.model.entity.Site;
import searchengine.model.enums.SiteStatus;
import searchengine.repository.IndexSearchRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Реализация поиска на основе поискового индекса (lemma + search_index).
 * 
 * @author Tseliar Vladimir
 */
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private static final int SNIPPET_LENGTH = 240;
    private static final double MAX_FREQUENCY_RATIO = 0.8d;
    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}\\p{Nd}]+");

    private final IndexingConfig indexingConfig;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexSearchRepository indexSearchRepository;
    private final LemmaFinder lemmaFinder;

    /**
     * Выполняет поиск по запросу.
     *
     * @param query {@link String} поисковый запрос
     * @param siteUrl {@link String} базовый URL сайта (если задан)
     * @param offset смещение
     * @param limit количество результатов
     * @return {@link SearchResponse} ответ поиска
     */
    @Override
    @Transactional(readOnly = true)
    public SearchResponse search(String query, String siteUrl, int offset, int limit) {
        String trimmedQuery = query == null ? "" : query.trim();
        if (!StringUtils.hasText(trimmedQuery)) {
            return SearchResponse.error("Задан пустой поисковый запрос");
        }

        if (offset < 0) {
            return SearchResponse.error("Некорректный параметр offset");
        }
        if (limit < 1) {
            return SearchResponse.error("Некорректный параметр limit");
        }

        int safeOffset = offset;
        int safeLimit = limit;

        List<String> queryTerms = extractQueryTerms(trimmedQuery);
        if (queryTerms.isEmpty()) {
            return SearchResponse.error("Задан пустой поисковый запрос");
        }

        Map<String, Integer> lemmaCounts = lemmaFinder.collectLemmasFromText(trimmedQuery);
        Set<String> lemmaTexts = lemmaCounts.keySet();
        if (lemmaTexts.isEmpty()) {
            return SearchResponse.error("Задан пустой поисковый запрос");
        }

        if (StringUtils.hasText(siteUrl) && findSiteConfig(siteUrl) == null) {
            return SearchResponse.error("Данный сайт отсутствует в конфигурационном файле");
        }
        List<Site> sites = resolveSitesForSearch(siteUrl);
        if (sites.isEmpty()) {
            return SearchResponse.error(StringUtils.hasText(siteUrl)
                    ? "Сайт не проиндексирован"
                    : "Нет проиндексированных сайтов для поиска");
        }

        List<SearchHit> hits = new ArrayList<>();
        for (Site site : sites) {
            List<Lemma> lemmas = loadLemmasForSite(site, lemmaTexts);
            if (lemmas.isEmpty()) {
                continue;
            }

            long pagesOnSite = pageRepository.countBySite(site);
            int frequencyThreshold = (int) Math.ceil(pagesOnSite * MAX_FREQUENCY_RATIO);
            List<Lemma> filtered = filterTooFrequentLemmas(lemmas, frequencyThreshold);
            if (filtered.isEmpty()) {
                continue;
            }
            filtered.sort(Comparator.comparingInt(Lemma::getFrequency));

            List<Integer> lemmaIds = filtered.stream().map(Lemma::getId).toList();
            if (lemmaIds.isEmpty()) {
                continue;
            }

            List<Object[]> rows = indexSearchRepository.findPageAbsRelevance(site.getId(), lemmaIds, lemmaIds.size());
            for (Object[] row : rows) {
                int pageId = ((Number) row[0]).intValue();
                double abs = ((Number) row[1]).doubleValue();
                hits.add(new SearchHit(site.getId(), pageId, abs));
            }
        }

        if (hits.isEmpty()) {
            return SearchResponse.ok(0, List.of());
        }

        hits.sort(Comparator.comparingDouble(SearchHit::absRelevance).reversed());
        double maxAbs = hits.stream().mapToDouble(SearchHit::absRelevance).max().orElse(1.0d);
        int totalCount = hits.size();

        int from = Math.min(safeOffset, totalCount);
        int to = Math.min(safeOffset + safeLimit, totalCount);
        List<SearchHit> pageHits = hits.subList(from, to);

        List<Integer> pageIds = pageHits.stream().map(SearchHit::pageId).toList();
        Map<Integer, Page> pagesById = new HashMap<>();
        for (Page page : pageRepository.findAllByIdInWithSite(pageIds)) {
            pagesById.put(page.getId(), page);
        }

        List<SearchResultItem> data = new ArrayList<>();
        for (SearchHit hit : pageHits) {
            Page page = pagesById.get(hit.pageId());
            if (page == null) {
                continue;
            }
            Site site = page.getSite();
            String siteBase = normalizeBaseUrl(site.getUrl());
            String content = page.getContent();
            Document doc = Jsoup.parse(content == null ? "" : content);
            String title = extractTitle(doc);
            String snippet = buildSnippet(doc.text(), queryTerms, lemmaTexts, SNIPPET_LENGTH);
            float relevance = (float) (hit.absRelevance() / maxAbs);
            data.add(new SearchResultItem(siteBase, site.getName(), page.getPath(), title, snippet, relevance));
        }

        return SearchResponse.ok(totalCount, data);
    }

    /**
     * Определяет список сайтов, по которым выполнять поиск: либо один выбранный сайт, либо все проиндексированные.
     *
     * @param siteUrl {@link String} базовый URL выбранного сайта (если задан)
     * @return {@link List}<{@link Site}> список сайтов для поиска
     */
    private List<Site> resolveSitesForSearch(String siteUrl) {
        if (StringUtils.hasText(siteUrl)) {
            Site site = findConfiguredIndexedSite(siteUrl);
            return site == null ? List.of() : List.of(site);
        }

        List<Site> result = new ArrayList<>();
        for (SiteConfig cfg : indexingConfig.getSites()) {
            Site site = findSiteByConfig(cfg);
            if (site != null && site.getStatus() == SiteStatus.INDEXED) {
                result.add(site);
            }
        }
        return result;
    }

    /**
     * Возвращает сайт из конфигурации, если он существует в БД и имеет статус {@link SiteStatus#INDEXED}.
     *
     * @param siteUrl {@link String} базовый URL сайта
     * @return {@link Site} сайт или {@code null}
     */
    private Site findConfiguredIndexedSite(String siteUrl) {
        SiteConfig cfg = findSiteConfig(siteUrl);
        if (cfg == null) {
            return null;
        }
        Site site = findSiteByConfig(cfg);
        if (site == null || site.getStatus() != SiteStatus.INDEXED) {
            return null;
        }
        return site;
    }

    /**
     * Ищет запись сайта в БД по конфигурации, учитывая возможный слеш в конце URL.
     *
     * @param cfg {@link SiteConfig} конфигурация сайта
     * @return {@link Site} сайт или {@code null}
     */
    private Site findSiteByConfig(SiteConfig cfg) {
        if (cfg == null || !StringUtils.hasText(cfg.getUrl())) {
            return null;
        }
        String url = cfg.getUrl();
        Site found = siteRepository.findByUrl(url).orElse(null);
        if (found != null) {
            return found;
        }
        String normalized = normalizeBaseUrl(url);
        if (url.endsWith("/")) {
            return siteRepository.findByUrl(normalized).orElse(null);
        }
        return siteRepository.findByUrl(normalized + "/").orElse(null);
    }

    /**
     * Ищет конфигурацию сайта по URL (нормализуя слеши в конце).
     *
     * @param url базовый URL
     * @return конфигурация сайта или {@code null}
     */
    private SiteConfig findSiteConfig(String url) {
        String normalized = normalizeBaseUrl(url);
        for (SiteConfig cfg : indexingConfig.getSites()) {
            String cfgNormalized = normalizeBaseUrl(cfg.getUrl());
            if (cfgNormalized.equals(normalized)) {
                return cfg;
            }
        }
        return null;
    }

    /**
     * Загружает леммы для сайта. Если хотя бы одной леммы нет в индексе сайта, возвращает пустой список.
     *
     * @param site {@link Site} сайт
     * @param lemmaTexts {@link Collection}<{@link String}> тексты лемм
     * @return {@link List}<{@link Lemma}> список лемм или пустой список
     */
    private List<Lemma> loadLemmasForSite(Site site, Collection<String> lemmaTexts) {
        List<Lemma> lemmas = lemmaRepository.findBySiteAndLemmaIn(site, lemmaTexts);
        if (lemmas.size() != lemmaTexts.size()) {
            return List.of();
        }
        return lemmas;
    }

    /**
     * Отбрасывает леммы, которые встречаются на слишком большом количестве страниц.
     *
     * @param lemmas {@link List}<{@link Lemma}> список лемм
     * @param threshold максимальная допустимая частота
     * @return {@link List}<{@link Lemma}> отфильтрованный список лемм
     */
    private List<Lemma> filterTooFrequentLemmas(List<Lemma> lemmas, int threshold) {
        if (threshold <= 0) {
            return new ArrayList<>(lemmas);
        }
        List<Lemma> result = new ArrayList<>();
        for (Lemma lemma : lemmas) {
            if (lemma.getFrequency() <= threshold) {
                result.add(lemma);
            }
        }
        return result;
    }

    /**
     * Извлекает заголовок страницы (title или h1).
     *
     * @param doc {@link Document} документ страницы
     * @return {@link String} заголовок
     */
    private static String extractTitle(Document doc) {
        String title = doc.title();
        if (StringUtils.hasText(title)) {
            return title.trim();
        }
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && StringUtils.hasText(h1.text())) {
            return h1.text().trim();
        }
        return "";
    }

    /**
     * Разбивает запрос на токены для подсветки в сниппете.
     *
     * @param query {@link String} запрос
     * @return {@link List}<{@link String}> список уникальных токенов (в нижнем регистре), отсортированных по длине (убывание)
     */
    private static List<String> extractQueryTerms(String query) {
        String[] raw = query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+");
        Set<String> unique = new HashSet<>();
        for (String token : raw) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            if (token.length() < 2) {
                continue;
            }
            unique.add(token);
        }
        List<String> result = new ArrayList<>(unique);
        result.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return result;
    }

    /**
     * Формирует сниппет фиксированной длины с подсветкой найденных слов запроса.
     *
     * @param text {@link String} исходный текст страницы
     * @param queryTerms {@link List}<{@link String}> токены запроса (в нижнем регистре)
     * @param queryLemmas {@link Set}<{@link String}> леммы запроса (в нижнем регистре)
     * @param maxLength максимальная длина сниппета
     * @return {@link String} сниппет в HTML-формате
     */
    private String buildSnippet(String text, List<String> queryTerms, Set<String> queryLemmas, int maxLength) {
        String normalized = (text == null ? "" : text).replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return "";
        }

        int matchIndex = findFirstMatchIndex(normalized, queryTerms, queryLemmas);

        int start;
        if (matchIndex < 0) {
            start = 0;
        } else {
            start = Math.max(0, matchIndex - (maxLength / 4));
        }
        int end = Math.min(normalized.length(), start + maxLength);
        if (end - start < maxLength && start > 0) {
            start = Math.max(0, end - maxLength);
        }

        String snippet = normalized.substring(start, end).trim();
        List<String> highlightTerms = buildHighlightTerms(queryTerms, queryLemmas, snippet);
        String escaped = escapeHtml(snippet);
        String highlighted = highlightTerms(escaped, highlightTerms);

        String prefix = start > 0 ? "... " : "";
        String suffix = end < normalized.length() ? " ..." : "";
        return prefix + highlighted + suffix;
    }

    /**
     * Находит позицию первого совпадения в тексте: сначала по токенам запроса, затем по леммам.
     *
     * @param text {@link String} исходный текст страницы
     * @param queryTerms {@link List}<{@link String}> токены запроса
     * @param queryLemmas {@link Set}<{@link String}> леммы запроса
     * @return позиция совпадения или -1, если совпадений не найдено
     */
    private int findFirstMatchIndex(String text, List<String> queryTerms, Set<String> queryLemmas) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        Set<String> termSet = new HashSet<>(queryTerms);
        boolean hasLemmas = queryLemmas != null && !queryLemmas.isEmpty();

        Matcher m = WORD_PATTERN.matcher(text);
        while (m.find()) {
            String word = m.group();
            String lowerWord = word.toLowerCase(Locale.ROOT);
            if (termSet.contains(lowerWord)) {
                return m.start();
            }
            if (hasLemmas) {
                String lemma = lemmaFinder.getLemmaForWord(word);
                if (lemma != null && queryLemmas.contains(lemma)) {
                    return m.start();
                }
            }
        }
        return -1;
    }

    /**
     * Формирует список токенов для подсветки в сниппете.
     *
     * <p>Кроме исходных токенов запроса добавляет словоформы из сниппета, которые соответствуют леммам запроса.</p>
     *
     * @param queryTerms {@link List}<{@link String}> токены запроса
     * @param queryLemmas {@link Set}<{@link String}> леммы запроса
     * @param snippet {@link String} текст сниппета (без HTML)
     * @return {@link List}<{@link String}> список токенов для подсветки (в нижнем регистре), отсортированных по длине
     */
    private List<String> buildHighlightTerms(List<String> queryTerms, Set<String> queryLemmas, String snippet) {
        Set<String> terms = new HashSet<>(queryTerms);
        if (snippet != null && !snippet.isBlank() && queryLemmas != null && !queryLemmas.isEmpty()) {
            Matcher m = WORD_PATTERN.matcher(snippet);
            while (m.find()) {
                String word = m.group();
                String lemma = lemmaFinder.getLemmaForWord(word);
                if (lemma != null && queryLemmas.contains(lemma)) {
                    terms.add(word.toLowerCase(Locale.ROOT));
                }
            }
        }
        List<String> result = new ArrayList<>(terms);
        result.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return result;
    }

    /**
     * Оборачивает найденные токены в тег {@code <b>}.
     *
     * @param text {@link String} текст (уже экранированный)
     * @param terms {@link List}<{@link String}> токены
     * @return {@link String} текст с подсветкой
     */
    private static String highlightTerms(String text, List<String> terms) {
        String result = text;
        for (String term : terms) {
            Pattern p = Pattern.compile("(?iu)(?<![\\p{L}\\p{Nd}])" + Pattern.quote(term) + "(?![\\p{L}\\p{Nd}])");
            Matcher m = p.matcher(result);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, "<b>" + Matcher.quoteReplacement(m.group()) + "</b>");
            }
            m.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }

    /**
     * Экранирует базовые HTML-символы.
     *
     * @param s {@link String} строка
     * @return {@link String} экранированная строка
     */
    private static String escapeHtml(String s) {
        String out = s;
        out = out.replace("&", "&amp;");
        out = out.replace("<", "&lt;");
        out = out.replace(">", "&gt;");
        out = out.replace("\"", "&quot;");
        return out;
    }

    /**
     * Нормализует базовый URL: trim и удаление завершающих слешей.
     *
     * @param url {@link String} URL
     * @return {@link String} нормализованный URL без слеша в конце
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
     * Внутреннее представление результата поиска (для сортировки/пагинации).
     *
     * @param siteId ID сайта
     * @param pageId ID страницы
     * @param absRelevance абсолютная релевантность (сумма rank)
     */
    private record SearchHit(int siteId, int pageId, double absRelevance) {
    }
}
