package searchengine.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Сущность леммы (нормальной формы слова) в рамках конкретного сайта.
 *
 * <p>Поле frequency показывает количество страниц, на которых лемма встречается хотя бы один раз.</p>
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
    @JsonIgnore
    private Site site;

    @Column(name = "lemma", nullable = false, length = 255)
    private String lemma;

    @Column(name = "frequency", nullable = false)
    private int frequency;

    @OneToMany(mappedBy = "lemma", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<IndexSearch> indexes = new ArrayList<>();
}
