package com.cet46.vocab.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_word_progress")
public class UserWordProgress {
    private Long id;
    private Long userId;
    private Long wordId;
    private String wordType;
    private Double easiness;
    private Integer interval;
    private Integer repetition;
    private LocalDate nextReviewDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
