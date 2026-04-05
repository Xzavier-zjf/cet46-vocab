package com.cet46.vocab.service.impl;

import com.cet46.vocab.dto.request.AssistantStateSyncRequest;
import com.cet46.vocab.dto.response.AssistantStateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantStateServiceImplConcurrencyTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AssistantStateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AssistantStateServiceImpl(jdbcTemplate, new ObjectMapper());
        doAnswer(invocation -> List.of()).when(jdbcTemplate).queryForList(anyString(), any(Object[].class));
    }

    @Test
    void syncState_shouldKeepUpdatedAtGuardInDeleteSessionsForOutOfOrderSnapshot() throws Exception {
        Long userId = 1001L;
        long oldClientSyncAt = 1_710_000_000_000L;

        mockSessionSnapshotRows(userId,
                new SnapshotRow(11L, "s_old", oldClientSyncAt + 20_000),
                new SnapshotRow(12L, "s_new", oldClientSyncAt + 40_000)
        );

        AssistantStateSyncRequest req = new AssistantStateSyncRequest();
        req.setClientSyncAt(oldClientSyncAt);
        req.setSessions(List.of(buildSession("s_old", oldClientSyncAt - 5_000, List.of())));

        service.syncState(userId, req);

        List<String> sqls = captureUpdateSql();
        assertTrue(sqls.stream().anyMatch(sql ->
                sql.contains("DELETE FROM assistant_chat_session") && sql.contains("updated_at <= ?")),
                "Expected session delete SQL to include updated_at boundary guard"
        );
    }

    @Test
    void syncState_shouldSkipMessageDeletionWhenIncomingSnapshotIsOlderThanDbSession() throws Exception {
        Long userId = 2002L;
        long oldClientSyncAt = 1_710_000_100_000L;

        mockSessionSnapshotRows(userId, new SnapshotRow(21L, "s1", oldClientSyncAt + 60_000));

        AssistantStateSyncRequest req = new AssistantStateSyncRequest();
        req.setClientSyncAt(oldClientSyncAt);
        req.setSessions(List.of(buildSession(
                "s1",
                oldClientSyncAt - 10_000,
                List.of(buildMessage("m1", "user", "hi"))
        )));

        service.syncState(userId, req);

        List<String> sqls = captureUpdateSql();
        assertFalse(sqls.stream().anyMatch(sql -> sql.contains("assistant_chat_message") && sql.startsWith("INSERT INTO")),
                "Out-of-order snapshot should not insert/update messages");
        assertFalse(sqls.stream().anyMatch(sql -> sql.contains("DELETE FROM assistant_chat_message")),
                "Out-of-order snapshot should not delete newer messages");
    }

    private void mockSessionSnapshotRows(Long userId, SnapshotRow... rows) throws Exception {
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (!sql.startsWith("SELECT id, client_session_id, updated_at FROM assistant_chat_session")) {
                return java.util.Map.of();
            }
            @SuppressWarnings("unchecked")
            ResultSetExtractor<Object> extractor = (ResultSetExtractor<Object>) invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            if (rows.length == 1) {
                when(rs.next()).thenReturn(true, false);
                when(rs.getString("client_session_id")).thenReturn(rows[0].clientId);
                when(rs.getLong("id")).thenReturn(rows[0].id);
                when(rs.getTimestamp("updated_at")).thenReturn(new Timestamp(rows[0].updatedAtMillis));
            } else {
                Boolean[] seq = new Boolean[rows.length + 1];
                for (int i = 0; i < rows.length; i++) {
                    seq[i] = true;
                }
                seq[rows.length] = false;
                when(rs.next()).thenReturn(seq[0], java.util.Arrays.copyOfRange(seq, 1, seq.length));

                String[] clientIds = new String[rows.length];
                Long[] ids = new Long[rows.length];
                Timestamp[] ts = new Timestamp[rows.length];
                for (int i = 0; i < rows.length; i++) {
                    clientIds[i] = rows[i].clientId;
                    ids[i] = rows[i].id;
                    ts[i] = new Timestamp(rows[i].updatedAtMillis);
                }
                when(rs.getString("client_session_id")).thenReturn(clientIds[0], java.util.Arrays.copyOfRange(clientIds, 1, clientIds.length));
                when(rs.getLong("id")).thenReturn(ids[0], java.util.Arrays.copyOfRange(ids, 1, ids.length));
                when(rs.getTimestamp("updated_at")).thenReturn(ts[0], java.util.Arrays.copyOfRange(ts, 1, ts.length));
            }
            return extractor.extractData(rs);
        }).when(jdbcTemplate).query(anyString(), any(ResultSetExtractor.class), eq(userId));
    }

    private List<String> captureUpdateSql() {
        return org.mockito.Mockito.mockingDetails(jdbcTemplate)
                .getInvocations()
                .stream()
                .filter(inv -> "update".equals(inv.getMethod().getName()))
                .map(inv -> String.valueOf((Object) inv.getArgument(0)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private AssistantStateSyncRequest.SessionItem buildSession(String id,
                                                               long updatedAt,
                                                               List<AssistantStateSyncRequest.MessageItem> messages) {
        AssistantStateSyncRequest.SessionItem item = new AssistantStateSyncRequest.SessionItem();
        item.setId(id);
        item.setTitle("session-" + id);
        item.setUpdatedAt(updatedAt);
        item.setHasInteraction(true);
        item.setPinned(false);
        item.setMessages(messages);
        return item;
    }

    private AssistantStateSyncRequest.MessageItem buildMessage(String id, String role, String content) {
        AssistantStateSyncRequest.MessageItem item = new AssistantStateSyncRequest.MessageItem();
        item.setId(id);
        item.setRole(role);
        item.setContent(content);
        return item;
    }

    private static class SnapshotRow {
        private final Long id;
        private final String clientId;
        private final long updatedAtMillis;

        private SnapshotRow(Long id, String clientId, long updatedAtMillis) {
            this.id = id;
            this.clientId = clientId;
            this.updatedAtMillis = updatedAtMillis;
        }
    }
}
