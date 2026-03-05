package com.cet46.vocab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cet46.vocab.entity.WordMeta;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WordMetaMapper extends BaseMapper<WordMeta> {

    WordMeta selectByWordAndStyle(@Param("wordId") Long wordId,
                                  @Param("wordType") String wordType,
                                  @Param("style") String style);
}
