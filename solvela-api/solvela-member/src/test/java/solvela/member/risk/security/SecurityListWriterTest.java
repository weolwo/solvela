package solvela.member.risk.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import solvela.enums.SecurityListDimensionEnum;
import solvela.enums.SecurityListStatusEnum;
import solvela.enums.SecurityListTypeEnum;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自动写入名单时「读完到写之间被人改了」的处理。
 *
 * <p>🔴 复用 / 延长两条 UPDATE 都带旧状态条件，影响 0 行说明那一行刚被别人改过（最要紧的是被人工重新加成了有效条目）。
 * 这时要重新读一遍再决定，而不是假装写成了 —— 更不能把人工条目改成自动的、把长期封禁缩成 24 小时。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityListWriterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private SecurityListWriter writer;

    private static final SecurityListWriter.Entry ENTRY = new SecurityListWriter.Entry(SecurityListTypeEnum.BLACK,
            SecurityListDimensionEnum.IP, "10.0.0.1", "HONEYPOT", "用例", 1_900_000_000L, null);

    @Test
    @DisplayName("🔴 读到已撤销、复用时影响 0 行，重读发现已被人工加成有效：不动它，返回保留人工")
    void 复用撞上人工重加() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(row(SecurityListStatusEnum.REVOKED, 2, 0)))
                .thenReturn(List.of(row(SecurityListStatusEnum.VALID, SecurityListWriter.SOURCE_MANUAL, 0)));
        when(jdbcTemplate.update(contains("revoke_operator = NULL"), any(Object[].class))).thenReturn(0);

        assertEquals(SecurityListWriter.Outcome.KEPT_MANUAL, writer.upsert(ENTRY));

        // 复用只试了一次；人工条目没有被第二次复用或延长
        verify(jdbcTemplate, times(1)).update(contains("revoke_operator = NULL"), any(Object[].class));
        verify(jdbcTemplate, times(0)).update(contains("hit_count = hit_count + 1"), any(Object[].class));
    }

    @Test
    @DisplayName("读到有效的自动条目、延长时影响 0 行，重读发现已被撤销：改走复用")
    void 延长撞上撤销() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(row(SecurityListStatusEnum.VALID, 2, 0)))
                .thenReturn(List.of(row(SecurityListStatusEnum.REVOKED, 2, 0)));
        when(jdbcTemplate.update(contains("hit_count = hit_count + 1"), any(Object[].class))).thenReturn(0);
        when(jdbcTemplate.update(contains("revoke_operator = NULL"), any(Object[].class))).thenReturn(1);

        assertEquals(SecurityListWriter.Outcome.REUSED, writer.upsert(ENTRY));
    }

    @Test
    @DisplayName("连续两次都没抢到：抛出去，不假装写成了")
    void 两次都没抢到() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(row(SecurityListStatusEnum.REVOKED, 2, 0)));
        when(jdbcTemplate.update(contains("revoke_operator = NULL"), any(Object[].class))).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> writer.upsert(ENTRY));
    }

    private static Map<String, Object> row(SecurityListStatusEnum status, int source, int expired) {
        return Map.of("id", 7L, "source", source, "status", status.getValue(), "expired", expired);
    }
}
