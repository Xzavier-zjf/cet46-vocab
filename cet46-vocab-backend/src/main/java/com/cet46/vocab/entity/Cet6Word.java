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
@TableName("cet6zx")
public class Cet6Word {
    private Integer id;
    private String english;
    private String sent;
    private String chinese;
}
