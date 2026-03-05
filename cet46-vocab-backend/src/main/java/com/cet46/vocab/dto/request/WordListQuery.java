package com.cet46.vocab.dto.request;

import lombok.Data;

@Data
public class WordListQuery {
    private String type;
    private Integer page = 1;
    private Integer size = 20;
    private String keyword;
    private String pos;
}
