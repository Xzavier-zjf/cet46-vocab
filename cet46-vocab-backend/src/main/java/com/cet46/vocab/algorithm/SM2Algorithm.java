package com.cet46.vocab.algorithm;

import java.time.LocalDate;

public class SM2Algorithm {

    private SM2Algorithm() {
    }

    /**
     * 执行一次 SM-2 计算
     *
     * @param score      用户评分（前端传入 1/3/5，内部直接使用）
     * @param easiness   当前 E-Factor（初始 2.5）
     * @param interval   当前复习间隔天数
     * @param repetition 当前成功复习次数
     * @return 更新后的 SM2Result
     */
    public static SM2Result calculate(int score, double easiness,
                                      int interval, int repetition) {
        // 评分 < 3 视为遗忘，重置进度
        if (score < 3) {
            return new SM2Result(easiness, 1, 0,
                    LocalDate.now().plusDays(1));
        }

        // 更新 E-Factor（下限 1.3）
        double newEasiness = easiness
                + (0.1 - (5 - score) * (0.08 + (5 - score) * 0.02));
        newEasiness = Math.max(1.3, newEasiness);

        // 计算新间隔
        int newInterval;
        int newRepetition = repetition + 1;
        if (repetition == 0) {
            newInterval = 1;
        } else if (repetition == 1) {
            newInterval = 6;
        } else {
            newInterval = (int) Math.round(interval * newEasiness);
        }

        LocalDate nextReviewDate = LocalDate.now().plusDays(newInterval);
        return new SM2Result(newEasiness, newInterval,
                newRepetition, nextReviewDate);
    }

    public record SM2Result(
            double easiness,
            int interval,
            int repetition,
            LocalDate nextReviewDate
    ) {
    }
}
