package org.nobilis.nobichat.service;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class BuiltQuery {
    private String dataSql;
    private String countSql;
    private List<Object> dataParams;
    private List<Object> countParams;
}