package com.cet46.vocab.algorithm;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SM2AlgorithmTest {

    @Test
    void score1_遗忘时应重置间隔和重复次数() {
        // 测试意图：score=1 时 interval=1, repetition=0, nextReviewDate=明天
        LocalDate today = LocalDate.now();
        SM2Algorithm.SM2Result result = SM2Algorithm.calculate(1, 2.5, 6, 2);

        assertEquals(1, result.interval());
        assertEquals(0, result.repetition());
        assertEquals(today.plusDays(1), result.nextReviewDate());
    }

    @Test
    void score3_首次复习应设置interval1_repetition1() {
        // 测试意图：score=3 且 repetition=0 时 interval=1, repetition=1
        SM2Algorithm.SM2Result result = SM2Algorithm.calculate(3, 2.5, 1, 0);

        assertEquals(1, result.interval());
        assertEquals(1, result.repetition());
    }

    @Test
    void score3_第二次复习应设置interval6_repetition2() {
        // 测试意图：score=3 且 repetition=1 时 interval=6, repetition=2
        SM2Algorithm.SM2Result result = SM2Algorithm.calculate(3, 2.5, 1, 1);

        assertEquals(6, result.interval());
        assertEquals(2, result.repetition());
    }

    @Test
    void score5_多次复习后应按efactor计算间隔() {
        // 测试意图：score=5 且 repetition>=2 时 interval=round(interval*newEasiness)
        SM2Algorithm.SM2Result result = SM2Algorithm.calculate(5, 2.5, 6, 2);

        double expectedEasiness = 2.6;
        int expectedInterval = (int) Math.round(6 * expectedEasiness);

        assertEquals(expectedEasiness, result.easiness(), 0.0001);
        assertEquals(expectedInterval, result.interval());
        assertEquals(3, result.repetition());
    }

    @Test
    void efactor下限测试_多次低分后不低于13() {
        // 测试意图：连续低分计算后 E-Factor 不低于 1.3
        double easiness = 1.31;
        int interval = 6;
        int repetition = 2;
        for (int i = 0; i < 10; i++) {
            SM2Algorithm.SM2Result result = SM2Algorithm.calculate(3, easiness, interval, repetition);
            easiness = result.easiness();
            interval = result.interval();
            repetition = result.repetition();
        }
        assertTrue(easiness >= 1.3);
    }

    @Test
    void nextReviewDate应始终晚于今天() {
        // 测试意图：不同评分下 nextReviewDate 均为今天之后
        LocalDate today = LocalDate.now();

        SM2Algorithm.SM2Result forgetResult = SM2Algorithm.calculate(1, 2.5, 1, 0);
        SM2Algorithm.SM2Result normalResult = SM2Algorithm.calculate(3, 2.5, 1, 1);
        SM2Algorithm.SM2Result goodResult = SM2Algorithm.calculate(5, 2.5, 6, 2);

        assertTrue(forgetResult.nextReviewDate().isAfter(today));
        assertTrue(normalResult.nextReviewDate().isAfter(today));
        assertTrue(goodResult.nextReviewDate().isAfter(today));
    }
}
