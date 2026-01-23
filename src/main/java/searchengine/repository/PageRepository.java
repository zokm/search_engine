package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.entity.Page;
import searchengine.model.entity.Site;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для сущности {@link searchengine.model.entity.Page}.
 * 
 * @author Tseliar Vladimir
 */
@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {

    /**
     * Проверяет, существует ли страница для заданного сайта и пути.
     *
     * @param site {@link Site} сайт
     * @param path {@link String} путь страницы
     * @return true, если страница существует
     */
    boolean existsBySiteAndPath(Site site, String path);

    /**
     * Удаляет все страницы сайта.
     *
     * @param site {@link Site} сайт
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Page p WHERE p.site = :site")
    void deleteBySite(@Param("site") Site site);

    /**
     * Ищет страницу по сайту и пути.
     *
     * @param site {@link Site} сайт
     * @param path {@link String} путь страницы
     * @return {@link Optional}<{@link Page}> найденная страница (если есть)
     */
    Optional<Page> findBySiteAndPath(Site site, String path);
    
    /**
     * Возвращает все страницы сайта.
     *
     * @param site {@link Site} сайт
     * @return {@link List}<{@link Page}> список страниц
     */
    List<Page> findBySite(Site site);

    /**
     * Считает количество страниц сайта.
     *
     * @param site {@link Site} сайт
     * @return количество страниц
     */
    long countBySite(Site site);
}
