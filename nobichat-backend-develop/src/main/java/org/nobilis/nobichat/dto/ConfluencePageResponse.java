package org.nobilis.nobichat.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ConfluencePageResponse {
    private String id;
    private String title;
    private ConfluenceBody body;

    @Data
    @NoArgsConstructor
    public static class ConfluenceBody {
        private ConfluenceStorage storage;
    }

    @Data
    @NoArgsConstructor
    public static class ConfluenceStorage {
        private String value;
        private String representation;
    }
}