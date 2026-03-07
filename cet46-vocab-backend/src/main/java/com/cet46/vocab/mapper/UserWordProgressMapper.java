package com.cet46.vocab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cet46.vocab.entity.UserWordProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserWordProgressMapper extends BaseMapper<UserWordProgress> {
    List<UserWordProgress> selectTodayReview(@Param("userId") Long userId);

    List<UserWordProgress> selectImmediateReview(@Param("userId") Long userId, @Param("limit") Integer limit);
}
