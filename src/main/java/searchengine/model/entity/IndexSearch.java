package searchengine.model.entity;

import lombok.*;

import javax.persistence.*;

/**
 * Таблица search_index — связывает страницы и леммы с весом (rank).
 *
 * @author Tseliar Vladimir
 */
@Entity
@Table(
        name = "search_index",
        indexes = {
                @Index(name = "idx_index_page", columnList = "page_id"),
                @Index(name = "idx_index_lemma", columnList = "lemma_id"),
                @Index(name = "idx_index_page_lemma", columnList = "page_id, lemma_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"page_id", "lemma_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndexSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "page_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_index_page")
    )
    private Page page;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "lemma_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_index_lemma")
    )
    private Lemma lemma;

    @Column(name = "rank_value", nullable = false)
    private Float rank;
}

