package org.nobilis.nobichat.dto.kontur;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KonturFocusCompanyDto {
    private String inn;
    private String ogrn;
    private String focusHref;
    private BriefReportDto briefReport;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BriefReportDto {
        private SummaryDto summary;
        private String href;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SummaryDto {
            @JsonProperty("greenStatements")
            private Boolean greenStatements;
            @JsonProperty("yellowStatements")
            private Boolean yellowStatements;
            @JsonProperty("redStatements")
            private Boolean redStatements;
        }
    }
}