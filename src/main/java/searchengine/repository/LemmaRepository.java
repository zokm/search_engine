package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.entity.Lemma;
import searchengine.model.entity.Page;
import searchengine.model.entity.Site;

import java.util.Optional;

/**
 * @author Tseliar Vladimir
 */
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {

    Optional<Lemma> findBySiteAndLemma(Site site, String lemma);

    @Modifying
    @Query("DELETE FROM Lemma l WHERE l.id IN " +
            "(SELECT i.lemma.id FROM IndexSearch i WHERE i.page = :page)")
    void deleteByPage(@Param("page") Page page);
}
