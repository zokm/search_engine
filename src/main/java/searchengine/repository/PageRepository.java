package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.entity.Page;
import searchengine.model.entity.Site;

import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {

    boolean existsBySiteAndPath(Site site, String path);

    @Modifying
    @Transactional
    @Query("DELETE FROM Page p WHERE p.site = :site")
    void deleteBySite(@Param("site") Site site);

    Optional<Page> findBySiteAndPath(Site site, String path);
}
