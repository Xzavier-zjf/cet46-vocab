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
@TableName("review_log")
public class ReviewLog {
    private Long id;
    private Long userId;
    private Long wordId;
    private String wordType;
    private Integer score;
    private Long timeSpentMs;
    private LocalDateTime reviewedAt;
}
