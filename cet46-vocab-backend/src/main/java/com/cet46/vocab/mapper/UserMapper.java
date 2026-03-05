package com.cet46.vocab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cet46.vocab.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    User selectByUsername(@Param("username") String username);
}
