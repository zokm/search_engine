package searchengine.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Описание одного сайта из конфигурации индексации.
 * 
 * @author Tseliar Vladimir
 */
@Setter
@Getter
public class SiteConfig {

    private String url;
    private String name;
}
