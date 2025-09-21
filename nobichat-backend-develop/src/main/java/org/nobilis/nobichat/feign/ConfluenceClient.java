package org.nobilis.nobichat.feign;

import org.nobilis.nobichat.dto.ConfluencePageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "confluenceClient", url = "${confluence.api.url}", configuration = ConfluenceClientConfig.class)
public interface ConfluenceClient {

    /**
     * Получает содержимое страницы Confluence по ID.
     * @param pageId ID страницы Confluence.
     * @return Объект ConfluencePageResponse.
     */
    @GetMapping("/rest/api/content/{pageId}?expand=body.storage")
    ConfluencePageResponse getPageContent(@PathVariable("pageId") String pageId);
}
