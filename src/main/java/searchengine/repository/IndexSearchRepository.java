package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.entity.IndexSearch;
import searchengine.model.entity.Lemma;
import searchengine.model.entity.Page;
import searchengine.model.entity.Site;

import java.util.List;

/**
 * Репозиторий для сущности {@link searchengine.model.entity.IndexSearch}.
 * 
 * @author Tseliar Vladimir
 */
@Repository
public interface IndexSearchRepository extends JpaRepository<IndexSearch, Integer> {

    /**
     * Проверяет, существует ли строка индекса для пары страница-лемма.
     *
     * @param page {@link Page} страница
     * @param lemma {@link Lemma} лемма
     * @return true, если запись существует
     */
    boolean existsByPageAndLemma(Page page, Lemma lemma);

    /**
     * Удаляет все записи индекса для страницы.
     *
     * @param page страница
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM IndexSearch i WHERE i.page = :page")
    void deleteByPage(@Param("page") Page page);

    /**
     * Удаляет все записи индекса для сайта.
     *
     * @param site {@link Site} сайт
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM IndexSearch i WHERE i.lemma.site = :site")
    void deleteBySite(@Param("site") Site site);

    /**
     * Считает количество записей индекса, где встречается лемма.
     *
     * @param lemma {@link Lemma} лемма
     * @return количество записей индекса
     */
    long countByLemma(Lemma lemma);

    /**
     * Атомарно добавляет запись индекса или обновляет rank для существующей пары (page_id, lemma_id).
     *
     * @param pageId ID страницы
     * @param lemmaId ID леммы
     * @param rankValue значение rank
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO search_index (page_id, lemma_id, rank_value)
            VALUES (:pageId, :lemmaId, :rankValue)
            ON DUPLICATE KEY UPDATE rank_value = VALUES(rank_value)
            """, nativeQuery = true)
    void upsertRank(@Param("pageId") int pageId, @Param("lemmaId") int lemmaId, @Param("rankValue") float rankValue);

    /**
     * Возвращает список страниц, содержащих все указанные леммы, с суммой rank по этим леммам.
     *
     * @param siteId ID сайта
     * @param lemmaIds ID лемм
     * @param lemmaCount количество лемм (для условия пересечения)
     * @return {@link List}<{@link Object[]}> список строк вида {@code [page_id, abs_sum]}
     */
    @Query(value = """
            SELECT si.page_id AS pageId, SUM(si.rank_value) AS absSum
            FROM search_index si
            JOIN page p ON p.id = si.page_id
            WHERE p.site_id = :siteId
              AND p.code < 400
              AND si.lemma_id IN (:lemmaIds)
            GROUP BY si.page_id
            HAVING COUNT(DISTINCT si.lemma_id) = :lemmaCount
            """, nativeQuery = true)
    List<Object[]> findPageAbsRelevance(
            @Param("siteId") int siteId,
            @Param("lemmaIds") List<Integer> lemmaIds,
            @Param("lemmaCount") int lemmaCount
    );
}
