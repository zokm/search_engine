package searchengine.service;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author Tseliar Vladimir
 */
@Service
public class LemmaFinder {

    private final LuceneMorphology luceneMorph;

    public LemmaFinder() throws IOException {
        this.luceneMorph = new RussianLuceneMorphology();
    }

    /**
     * Метод собирает леммы из текста HTML
     *
     * @param html {@link String} текст
     * @return {@link Map}<{@link String}, {@link Integer}> коллекция лемм и их количества в тексте html
     */
    public Map<String, Integer> collectLemmas(String html) {
        String text = cleanHtml(html).toLowerCase(Locale.ROOT);
        String[] words = text.replaceAll("[^а-яё\\s]", " ")
                .trim()
                .split("\\s+");
        Map<String, Integer> result = new HashMap<>();
        for (String word : words) {
            if (word.isBlank()) continue;
            List<String> morphInfo = luceneMorph.getMorphInfo(word);
            if (isServiceWord(morphInfo)) continue;
            List<String> normalForms = luceneMorph.getNormalForms(word);
            if (normalForms.isEmpty()) continue;
            String lemma = normalForms.get(0);
            result.merge(lemma, 1, Integer::sum);
        }
        return result;
    }

    /**
     * Метод очищает HTML-код страницы, оставляя только текст
     */
    public String cleanHtml(String html) {
        if (html == null || html.isBlank()) return "";
        return Jsoup.parse(html).text();
    }

    /**
     * Метод для определения служебных слов
     *
     * @param morphInfo {@link List}<{@link String}> список морфологических характеристик слова
     * @return {@link Boolean} true - если слово служебное, иначе false
     */
    private boolean isServiceWord(List<String> morphInfo) {
        for (String info : morphInfo) {
            if (info.contains("СОЮЗ")
                    || info.contains("ПРЕДЛ")
                    || info.contains("ЧАСТ")
                    || info.contains("МЕЖД")) {
                return true;
            }
        }
        return false;
    }
}
