package com.cet46.vocab.mapper;

import com.cet46.vocab.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UserMapperIntegrationTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void insertUserShouldWorkAgainstLocalDatabase() {
        User user = User.builder()
                .username("it_" + System.currentTimeMillis())
                .password("pwd")
                .nickname("it")
                .role("USER")
                .dailyTarget(20)
                .build();

        userMapper.insert(user);
    }
}
