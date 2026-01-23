package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.entity.Site;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для сущности {@link searchengine.model.entity.Site}.
 * 
 * @author Tseliar Vladimir
 */
@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {

    /**
     * Ищет сайт по базовому URL.
     *
     * @param url {@link String} базовый URL
     * @return {@link Optional}<{@link Site}> найденный сайт (если есть)
     */
    Optional<Site> findByUrl(String url);
    
    /**
     * Возвращает все записи сайтов с указанным URL (на случай дубликатов).
     *
     * @param url {@link String} базовый URL
     * @return {@link List}<{@link Site}> список сайтов
     */
    List<Site> findAllByUrl(String url);
}
