package org.nobilis.nobichat.feign;

import org.nobilis.nobichat.dto.kontur.KonturFocusCompanyDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "kontur-focus", url = "https://focus-api.kontur.ru/api3",
        configuration = KonturFocusFeignClientConfiguration.class)
public interface KonturFocusFeignClient {

    @GetMapping("/briefReport")
    List<KonturFocusCompanyDto> briefReport(
            @RequestParam("inn") String inn
    );
}
