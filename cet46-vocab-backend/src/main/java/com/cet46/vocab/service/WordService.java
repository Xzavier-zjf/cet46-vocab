package com.cet46.vocab.service;

import com.cet46.vocab.common.PageResult;
import com.cet46.vocab.dto.request.WordListQuery;
import com.cet46.vocab.dto.response.WordDetailResponse;
import com.cet46.vocab.dto.response.WordListItem;

public interface WordService {

    PageResult<WordListItem> getWordList(WordListQuery query, Long userId);

    WordDetailResponse getWordDetail(Long wordId, String wordType, Long userId);

    void addWordToLearn(Long wordId, String wordType, Long userId);
}
