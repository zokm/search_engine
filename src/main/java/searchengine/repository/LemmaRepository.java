package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.entity.Lemma;
import searchengine.model.entity.Page;
import searchengine.model.entity.Site;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для сущности {@link searchengine.model.entity.Lemma}.
 * 
 * @author Tseliar Vladimir
 */
@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {

    /**
     * Ищет лемму по сайту и тексту леммы.
     *
     * @param site {@link Site} сайт
     * @param lemma {@link String} текст леммы
     * @return {@link Optional}<{@link Lemma}> найденная лемма (если есть)
     */
    Optional<Lemma> findBySiteAndLemma(Site site, String lemma);

    /**
     * Возвращает леммы сайта по списку текстов лемм.
     *
     * @param site {@link Site} сайт
     * @param lemmas {@link Collection}<{@link String}> список текстов лемм
     * @return {@link List}<{@link Lemma}> список найденных лемм
     */
    List<Lemma> findBySiteAndLemmaIn(Site site, Collection<String> lemmas);

    /**
     * Удаляет все леммы сайта.
     *
     * @param site {@link Site} сайт
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Lemma l WHERE l.site = :site")
    void deleteBySite(@Param("site") Site site);

    /**
     * Удаляет леммы сайта с частотой 0.
     *
     * @param site {@link Site} сайт
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Lemma l WHERE l.site = :site AND l.frequency = 0")
    void deleteUnusedLemmasBySite(@Param("site") Site site);

    /**
     * Возвращает уникальные леммы, связанные со страницей.
     *
     * @param page {@link Page} страница
     * @return {@link List}<{@link Lemma}> список лемм
     */
    @Query("SELECT DISTINCT i.lemma FROM IndexSearch i WHERE i.page = :page")
    List<Lemma> findLemmasByPage(@Param("page") Page page);

    /**
     * Считает количество записей индекса, в которых встречается лемма.
     *
     * @param lemma {@link Lemma} лемма
     * @return количество ссылок на лемму
     */
    @Query("SELECT COUNT(i) FROM IndexSearch i WHERE i.lemma = :lemma")
    long countUsageByLemma(@Param("lemma") Lemma lemma);

    /**
     * Считает количество лемм для сайта.
     *
     * @param site {@link Site} сайт
     * @return количество лемм
     */
    long countBySite(Site site);

    /**
     * Атомарно добавляет новую лемму (frequency=1) или увеличивает frequency существующей леммы на 1.
     *
     * @param siteId ID сайта
     * @param lemma {@link String} текст леммы
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO lemma (site_id, lemma, frequency)
            VALUES (:siteId, :lemma, 1)
            ON DUPLICATE KEY UPDATE frequency = frequency + 1
            """, nativeQuery = true)
    void upsertIncrementFrequency(@Param("siteId") int siteId, @Param("lemma") String lemma);
}
