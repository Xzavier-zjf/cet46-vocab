package com.cet46.vocab.dto.request;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AssistantStateSyncRequest {
    private Long clientSyncAt;
    private List<SessionItem> sessions = new ArrayList<>();
    private List<GroupItem> groups = new ArrayList<>();

    @Data
    public static class SessionItem {
        private String id;
        private String title;
        private Long updatedAt;
        private Boolean hasInteraction;
        private Boolean pinned;
        private String groupId;
        private ContextItem context;
        private List<MessageItem> messages = new ArrayList<>();
    }

    @Data
    public static class GroupItem {
        private String id;
        private String name;
        private Long createdAt;
    }

    @Data
    public static class ContextItem {
        private Long wordId;
        private String wordType;
        private String word;
        private String phonetic;
        private String pos;
        private String chinese;
        private String fromPage;
    }

    @Data
    public static class MessageItem {
        private String id;
        private String role;
        private String content;
        private String feedback;
        private Boolean autoContinued;
        private Integer continuationRounds;
    }
}
