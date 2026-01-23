package searchengine.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Сущность страницы сайта.
 * 
 * @author Tseliar Vladimir
 */
@Entity
@Table(name = "page", indexes = {
        @Index(name = "path_index", columnList = "path"),
        @Index(name = "idx_page_site_path", columnList = "site_id, path")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false, foreignKey = @ForeignKey(name = "fk_page_site"))
    @JsonIgnore
    private Site site;

    @Column(name = "path", nullable = false, length = 512)
    private String path;

    @Column(name = "code", nullable = false)
    private Integer code;

    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    @JsonIgnore
    private String content;

    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<IndexSearch> indexes = new ArrayList<>();
}
