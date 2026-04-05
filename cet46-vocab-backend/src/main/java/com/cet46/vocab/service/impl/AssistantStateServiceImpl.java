package com.cet46.vocab.service.impl;

import com.cet46.vocab.dto.request.AssistantStateSyncRequest;
import com.cet46.vocab.dto.response.AssistantStateResponse;
import com.cet46.vocab.service.AssistantStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AssistantStateServiceImpl implements AssistantStateService {

    private static final int MAX_SESSIONS = 100;
    private static final int MAX_MESSAGES_PER_SESSION = 500;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AssistantStateServiceImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public AssistantStateResponse loadState(Long userId) {
        List<Map<String, Object>> sessionRows = jdbcTemplate.queryForList(
                "SELECT id, client_session_id, title, updated_at, has_interaction, pinned, group_id, group_name, context_json " +
                        "FROM assistant_chat_session WHERE user_id = ? ORDER BY pinned DESC, updated_at DESC, id DESC LIMIT ?",
                userId,
                MAX_SESSIONS
        );

        AssistantStateResponse response = new AssistantStateResponse();
        if (sessionRows.isEmpty()) {
            return response;
        }

        Map<Long, AssistantStateResponse.SessionItem> sessionByDbId = new LinkedHashMap<>();
        Map<String, AssistantStateResponse.GroupItem> groupByClientId = new LinkedHashMap<>();
        List<Long> sessionDbIds = new ArrayList<>();

        for (Map<String, Object> row : sessionRows) {
            Long dbId = toLong(row.get("id"));
            if (dbId == null) {
                continue;
            }
            String clientSessionId = trimToEmpty(row.get("client_session_id"));
            if (!StringUtils.hasText(clientSessionId)) {
                continue;
            }
            AssistantStateResponse.SessionItem item = new AssistantStateResponse.SessionItem();
            item.setId(clientSessionId);
            item.setTitle(trimToEmpty(row.get("title")));
            item.setUpdatedAt(toMillis(row.get("updated_at")));
            item.setHasInteraction(toBoolean(row.get("has_interaction")));
            item.setPinned(toBoolean(row.get("pinned")));
            item.setGroupId(blankToNull(trimToEmpty(row.get("group_id"))));
            item.setContext(parseContext(trimToEmpty(row.get("context_json"))));
            item.setMessages(new ArrayList<>());

            sessionByDbId.put(dbId, item);
            sessionDbIds.add(dbId);

            if (StringUtils.hasText(item.getGroupId())) {
                String groupName = trimToEmpty(row.get("group_name"));
                if (StringUtils.hasText(groupName)) {
                    AssistantStateResponse.GroupItem group = groupByClientId.computeIfAbsent(item.getGroupId(), key -> {
                        AssistantStateResponse.GroupItem created = new AssistantStateResponse.GroupItem();
                        created.setId(key);
                        created.setName(groupName);
                        created.setCreatedAt(item.getUpdatedAt());
                        return created;
                    });
                    if (!StringUtils.hasText(group.getName())) {
                        group.setName(groupName);
                    }
                }
            }
        }

        if (!sessionDbIds.isEmpty()) {
            String placeholders = sessionDbIds.stream().map(id -> "?").collect(Collectors.joining(","));
            List<Object> args = new ArrayList<>(sessionDbIds);
            List<Map<String, Object>> messageRows = jdbcTemplate.queryForList(
                    "SELECT session_id, client_message_id, role, content, feedback, auto_continued, continuation_rounds " +
                            "FROM assistant_chat_message WHERE session_id IN (" + placeholders + ") ORDER BY sort_order ASC, id ASC",
                    args.toArray()
            );
            for (Map<String, Object> row : messageRows) {
                Long sessionId = toLong(row.get("session_id"));
                if (sessionId == null) {
                    continue;
                }
                AssistantStateResponse.SessionItem session = sessionByDbId.get(sessionId);
                if (session == null) {
                    continue;
                }
                AssistantStateResponse.MessageItem message = new AssistantStateResponse.MessageItem();
                message.setId(trimToEmpty(row.get("client_message_id")));
                message.setRole(normalizeRole(trimToEmpty(row.get("role"))));
                message.setContent(trimToEmpty(row.get("content")));
                message.setFeedback(trimToEmpty(row.get("feedback")));
                message.setAutoContinued(toBoolean(row.get("auto_continued")));
                message.setContinuationRounds(toInt(row.get("continuation_rounds")));
                session.getMessages().add(message);
            }
        }

        response.setSessions(new ArrayList<>(sessionByDbId.values()));
        List<AssistantStateResponse.GroupItem> groups = new ArrayList<>(groupByClientId.values());
        groups.sort(Comparator.comparing(AssistantStateResponse.GroupItem::getCreatedAt, Comparator.nullsLast(Long::compareTo)).reversed());
        response.setGroups(groups);
        return response;
    }

    @Override
    @Transactional
    public AssistantStateResponse syncState(Long userId, AssistantStateSyncRequest request) {
        AssistantStateSyncRequest safe = request == null ? new AssistantStateSyncRequest() : request;
        List<AssistantStateSyncRequest.SessionItem> incomingSessions = normalizeSessions(safe.getSessions());
        Map<String, String> groupNameMap = normalizeGroups(safe.getGroups());
        long syncAtMillis = safe.getClientSyncAt() == null || safe.getClientSyncAt() <= 0
                ? System.currentTimeMillis()
                : safe.getClientSyncAt();
        LocalDateTime syncAt = toDateTime(syncAtMillis);

        Set<String> keepSessionIds = new LinkedHashSet<>();
        for (AssistantStateSyncRequest.SessionItem session : incomingSessions) {
            String clientSessionId = blankToNull(session.getId());
            if (!StringUtils.hasText(clientSessionId)) {
                continue;
            }
            keepSessionIds.add(clientSessionId);
            String contextJson = writeJson(session.getContext());
            Long updatedAt = session.getUpdatedAt() == null || session.getUpdatedAt() <= 0 ? System.currentTimeMillis() : session.getUpdatedAt();
            String groupId = blankToNull(session.getGroupId());
            String groupName = StringUtils.hasText(groupId) ? blankToNull(groupNameMap.get(groupId)) : null;

            jdbcTemplate.update(
                    "INSERT INTO assistant_chat_session (user_id, client_session_id, title, updated_at, has_interaction, pinned, group_id, group_name, context_json, deleted, last_synced_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NOW()) " +
                            "ON DUPLICATE KEY UPDATE " +
                            "title = IF(VALUES(updated_at) >= updated_at, VALUES(title), title), " +
                            "has_interaction = IF(VALUES(updated_at) >= updated_at, VALUES(has_interaction), has_interaction), " +
                            "pinned = IF(VALUES(updated_at) >= updated_at, VALUES(pinned), pinned), " +
                            "group_id = IF(VALUES(updated_at) >= updated_at, VALUES(group_id), group_id), " +
                            "group_name = IF(VALUES(updated_at) >= updated_at, VALUES(group_name), group_name), " +
                            "context_json = IF(VALUES(updated_at) >= updated_at, VALUES(context_json), context_json), " +
                            "updated_at = GREATEST(updated_at, VALUES(updated_at)), " +
                            "deleted = 0, " +
                            "last_synced_at = NOW()",
                    userId,
                    clientSessionId,
                    safeTitle(session.getTitle()),
                    toDateTime(updatedAt),
                    Boolean.TRUE.equals(session.getHasInteraction()) ? 1 : 0,
                    Boolean.TRUE.equals(session.getPinned()) ? 1 : 0,
                    groupId,
                    groupName,
                    contextJson
            );
        }

        Map<String, SessionSnapshot> sessionSnapshotByClientId = jdbcTemplate.query(
                "SELECT id, client_session_id, updated_at FROM assistant_chat_session WHERE user_id = ?",
                rs -> {
                    Map<String, SessionSnapshot> map = new HashMap<>();
                    while (rs.next()) {
                        String clientId = rs.getString("client_session_id");
                        long id = rs.getLong("id");
                        if (StringUtils.hasText(clientId)) {
                            map.put(clientId, new SessionSnapshot(id, rs.getTimestamp("updated_at")));
                        }
                    }
                    return map;
                },
                userId
        );

        for (AssistantStateSyncRequest.SessionItem session : incomingSessions) {
            String clientSessionId = blankToNull(session.getId());
            if (!StringUtils.hasText(clientSessionId)) {
                continue;
            }
            SessionSnapshot snapshot = sessionSnapshotByClientId.get(clientSessionId);
            if (snapshot == null || snapshot.id() == null) {
                continue;
            }
            Long incomingUpdatedAt = session.getUpdatedAt() == null || session.getUpdatedAt() <= 0 ? System.currentTimeMillis() : session.getUpdatedAt();
            Long dbUpdatedAt = toMillis(snapshot.updatedAt());
            if (dbUpdatedAt != null && incomingUpdatedAt < dbUpdatedAt) {
                continue;
            }
            List<AssistantStateSyncRequest.MessageItem> messages = normalizeMessages(session.getMessages());
            Set<String> keepMessageIds = new LinkedHashSet<>();
            for (int i = 0; i < messages.size(); i++) {
                AssistantStateSyncRequest.MessageItem message = messages.get(i);
                String clientMessageId = blankToNull(message.getId());
                if (!StringUtils.hasText(clientMessageId)) {
                    continue;
                }
                keepMessageIds.add(clientMessageId);
                jdbcTemplate.update(
                        "INSERT INTO assistant_chat_message (session_id, client_message_id, role, content, feedback, auto_continued, continuation_rounds, sort_order, created_at, updated_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW()) " +
                        "ON DUPLICATE KEY UPDATE role = VALUES(role), content = VALUES(content), feedback = VALUES(feedback), auto_continued = VALUES(auto_continued), " +
                                "continuation_rounds = VALUES(continuation_rounds), sort_order = VALUES(sort_order), updated_at = NOW()",
                        snapshot.id(),
                        clientMessageId,
                        normalizeRole(message.getRole()),
                        trimToEmpty(message.getContent()),
                        trimToEmpty(message.getFeedback()),
                        Boolean.TRUE.equals(message.getAutoContinued()) ? 1 : 0,
                        safeContinuationRounds(message.getContinuationRounds()),
                        i,
                        deriveCreatedAt(clientMessageId)
                );
            }
            deleteMissingMessages(snapshot.id(), keepMessageIds, syncAt);
        }

        deleteMissingSessions(userId, keepSessionIds, syncAt);
        return loadState(userId);
    }

    private void deleteMissingSessions(Long userId, Set<String> keepSessionIds, LocalDateTime syncAt) {
        if (keepSessionIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM assistant_chat_session WHERE user_id = ? AND updated_at <= ?", userId, syncAt);
            return;
        }
        String placeholders = keepSessionIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.add(syncAt);
        args.addAll(keepSessionIds);
        jdbcTemplate.update(
                "DELETE FROM assistant_chat_session WHERE user_id = ? AND updated_at <= ? AND client_session_id NOT IN (" + placeholders + ")",
                args.toArray()
        );
    }

    private void deleteMissingMessages(Long sessionDbId, Set<String> keepMessageIds, LocalDateTime syncAt) {
        if (keepMessageIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM assistant_chat_message WHERE session_id = ? AND updated_at <= ?", sessionDbId, syncAt);
            return;
        }
        String placeholders = keepMessageIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(sessionDbId);
        args.add(syncAt);
        args.addAll(keepMessageIds);
        jdbcTemplate.update(
                "DELETE FROM assistant_chat_message WHERE session_id = ? AND updated_at <= ? AND client_message_id NOT IN (" + placeholders + ")",
                args.toArray()
        );
    }

    private List<AssistantStateSyncRequest.SessionItem> normalizeSessions(List<AssistantStateSyncRequest.SessionItem> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        return sessions.stream()
                .filter(item -> item != null && StringUtils.hasText(item.getId()))
                .limit(MAX_SESSIONS)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<AssistantStateSyncRequest.MessageItem> normalizeMessages(List<AssistantStateSyncRequest.MessageItem> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .filter(item -> item != null && StringUtils.hasText(item.getId()))
                .limit(MAX_MESSAGES_PER_SESSION)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Map<String, String> normalizeGroups(List<AssistantStateSyncRequest.GroupItem> groups) {
        if (groups == null || groups.isEmpty()) {
            return Map.of();
        }
        Map<String, String> map = new HashMap<>();
        for (AssistantStateSyncRequest.GroupItem group : groups) {
            if (group == null || !StringUtils.hasText(group.getId())) {
                continue;
            }
            String id = group.getId().trim();
            String name = trimToEmpty(group.getName());
            if (StringUtils.hasText(name)) {
                map.put(id, name);
            }
        }
        return map;
    }

    private AssistantStateResponse.ContextItem parseContext(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AssistantStateResponse.ContextItem.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDateTime toDateTime(Long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.systemDefault());
    }

    private LocalDateTime deriveCreatedAt(String clientMessageId) {
        if (StringUtils.hasText(clientMessageId)) {
            try {
                long ts = Long.parseLong(clientMessageId.replaceAll("[^0-9]", ""));
                if (ts > 0) {
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneOffset.systemDefault());
                }
            } catch (Exception ignore) {
                // Ignore parse failures and fallback to now.
            }
        }
        return LocalDateTime.now();
    }

    private String safeTitle(String title) {
        String value = trimToEmpty(title);
        if (!StringUtils.hasText(value)) {
            return "未命名会话";
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private int safeContinuationRounds(Integer rounds) {
        if (rounds == null || rounds < 0) {
            return 0;
        }
        return Math.min(rounds, 50);
    }

    private String normalizeRole(String role) {
        String value = trimToEmpty(role).toLowerCase(Locale.ROOT);
        return "assistant".equals(value) ? "assistant" : "user";
    }

    private Boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private Integer toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return 0;
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private Long toMillis(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toInstant().toEpochMilli();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.toInstant(ZoneOffset.systemDefault().getRules().getOffset(ldt)).toEpochMilli();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private String trimToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record SessionSnapshot(Long id, Timestamp updatedAt) {
    }
}
