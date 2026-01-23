package searchengine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.entity.Lemma;
import searchengine.model.entity.Page;
import searchengine.model.entity.Site;
import searchengine.repository.IndexSearchRepository;
import searchengine.repository.LemmaRepository;

import java.util.Map;
import java.util.TreeMap;

/**
 * Сервис сохранения лемм и индекса для страницы.
 * 
 * @author Tseliar Vladimir
 */
@Service
@RequiredArgsConstructor
public class LemmaIndexingService {

    private final LemmaRepository lemmaRepository;
    private final IndexSearchRepository indexRepository;

    /**
     * Сохраняет леммы и строки индекса для страницы.
     *
     * <p>Для работы в многопоточном режиме использует UPSERT на уровне БД, чтобы избегать ошибок дубликатов.</p>
     *
     * @param page {@link Page} страница
     * @param lemmaCounts {@link Map}<{@link String}, {@link Integer}> карта {@code лемма -> количество} для страницы
     * @param site {@link Site} сайт
     */
    @Transactional
    public void saveLemmasAndIndex(Page page, Map<String, Integer> lemmaCounts, Site site) {
        if (site == null || page == null) {
            throw new IllegalArgumentException("Site и Page не могут быть null");
        }
        Map<String, Integer> sorted = (lemmaCounts instanceof TreeMap)
                ? lemmaCounts
                : new TreeMap<>(lemmaCounts);
        for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
            String lemmaText = entry.getKey();
            if (lemmaText == null || lemmaText.isBlank()) {
                continue;
            }
            int rank = entry.getValue();
            lemmaRepository.upsertIncrementFrequency(site.getId(), lemmaText);
            Lemma lemma = lemmaRepository.findBySiteAndLemma(site, lemmaText)
                    .orElseThrow(() -> new IllegalStateException("Lemma not found after upsert: " + lemmaText));

            indexRepository.upsertRank(page.getId(), lemma.getId(), (float) rank);
        }
    }
}
