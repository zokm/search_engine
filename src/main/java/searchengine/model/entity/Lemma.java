package searchengine.model.entity;

import lombok.*;

import javax.persistence.*;

/**
 * Таблица lemma — хранит уникальные леммы (слова) и частоту их появления.
 *
 * @author Tseliar Vladimir
 */
@Entity
@Table(
        name = "lemma",
        indexes = {
                @Index(name = "idx_lemma_site", columnList = "site_id"),
                @Index(name = "idx_lemma_site_lemma", columnList = "site_id, lemma")
        },
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"site_id", "lemma"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lemma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "site_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_lemma_site")
    )
    private Site site;

    @Column(name = "lemma", nullable = false, length = 255)
    private String lemma;

    @Column(name = "frequency", nullable = false)
    private int frequency;
}

