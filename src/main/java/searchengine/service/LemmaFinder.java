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
 * Извлекает леммы из текста с использованием Lucene Morphology.
 * 
 * @author Tseliar Vladimir
 */
@Service
public class LemmaFinder {

    private final LuceneMorphology luceneMorph;

    /**
     * Создаёт экземпляр анализатора и инициализирует морфологию русского языка.
     */
    public LemmaFinder() {
        try {
            this.luceneMorph = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException("Не удалось инициализировать морфологический анализатор", e);
        }
    }

    /**
     * Собирает леммы из HTML-текста и считает их количество.
     *
     * @param html {@link String} HTML-код страницы
     * @return {@link Map}<{@link String}, {@link Integer}> карта {@code лемма -> количество} для текста страницы
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
     * Собирает леммы из обычного текста (без HTML) и считает их количество.
     *
     * @param text {@link String} текст запроса/строка текста
     * @return {@link Map}<{@link String}, {@link Integer}> карта {@code лемма -> количество} для переданного текста
     */
    public Map<String, Integer> collectLemmasFromText(String text) {
        if (text == null || text.isBlank()) {
            return new HashMap<>();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        String[] words = normalized.replaceAll("[^а-яё\\s]", " ")
                .trim()
                .split("\\s+");
        Map<String, Integer> result = new HashMap<>();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            List<String> morphInfo = luceneMorph.getMorphInfo(word);
            if (isServiceWord(morphInfo)) {
                continue;
            }
            List<String> normalForms = luceneMorph.getNormalForms(word);
            if (normalForms.isEmpty()) {
                continue;
            }
            String lemma = normalForms.get(0);
            result.merge(lemma, 1, Integer::sum);
        }
        return result;
    }

    /**
     * Удаляет HTML-теги, оставляя только текст.
     *
     * @param html {@link String} HTML-код
     * @return {@link String} текст без тегов
     */
    public String cleanHtml(String html) {
        if (html == null || html.isBlank()) return "";
        return Jsoup.parse(html).text();
    }

    /**
     * Определяет, является ли слово служебной частью речи, которую нужно исключить.
     *
     * @param morphInfo {@link List}<{@link String}> морфологическая информация
     * @return true, если слово нужно игнорировать
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
