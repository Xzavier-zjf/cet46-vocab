package com.cet46.vocab.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("cet4zx")
public class Cet4Word {
    private Integer id;
    private String english;
    private String sent;
    private String chinese;
}
