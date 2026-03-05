package com.cet46.vocab.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("word_meta")
public class WordMeta {
    private Long id;
    private Long wordId;
    private String wordType;
    private String style;
    private String sentenceEn;
    private String sentenceZh;
    private String sentenceDifficulty;
    private String synonymsJson;
    private String mnemonic;
    private String rootAnalysis;
    private String pos;
    private String genStatus;
    private String promptHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
