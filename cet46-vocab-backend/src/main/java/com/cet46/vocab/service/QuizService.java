package com.cet46.vocab.service;

import com.cet46.vocab.dto.request.QuizGenerateRequest;
import com.cet46.vocab.dto.request.QuizSubmitRequest;

import java.util.Map;

public interface QuizService {

    Map<String, Object> generateQuiz(Long userId, QuizGenerateRequest req);

    Map<String, Object> submitQuiz(Long userId, QuizSubmitRequest req);
}
