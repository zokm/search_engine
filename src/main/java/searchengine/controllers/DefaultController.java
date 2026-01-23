package searchengine.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * MVC-контроллер главной страницы (Thymeleaf)
 * 
 * @author Tseliar Vladimir
 */
@Controller
public class DefaultController {

    /**
     * Возвращает шаблон {@code index.html} из {@code resources/templates}.
     *
     * @return {@link String} имя шаблона
     */
    @RequestMapping("/")
    public String index() {
        return "index";
    }
}
