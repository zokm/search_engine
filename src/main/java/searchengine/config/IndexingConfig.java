package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Настройки индексации из конфигурационного файла.
 * 
 * @author Tseliar Vladimir
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "indexing-settings")
public class IndexingConfig {

    private List<SiteConfig> sites;
    private String userAgent;
    private String referrer;
}
