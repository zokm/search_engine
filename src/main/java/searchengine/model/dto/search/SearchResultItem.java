package searchengine.model.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Элемент поисковой выдачи.
 * 
 * @author Tseliar Vladimir
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultItem {

    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;
}

