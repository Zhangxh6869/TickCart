package com.tickcart.common.idempotent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.idempotent.entity.IdempotentRecord;
import com.tickcart.common.idempotent.mapper.IdempotentRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link IdempotentService} 单元测试。
 *
 * <p>覆盖：无键放行、首次占位、冲突回放、冲突但无单号、键超长。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("幂等服务")
class IdempotentServiceTest {

    private static final Long UID = 1001L;
    private static final String SCENE = IdempotentService.SCENE_ORDER_CREATE;
    private static final long RETENTION_HOURS = 24L;

    @Mock
    private IdempotentRecordMapper idempotentRecordMapper;

    private IdempotentService idempotentService;

    /**
     * {@code LambdaQueryWrapper} 会立即解析列名，纯单元测试没有 Spring 容器，
     * 需先手动初始化实体的 TableInfo 缓存，否则报 "can not find lambda cache"。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), IdempotentRecord.class);
    }

    @BeforeEach
    void setUp() {
        idempotentService = new IdempotentService(idempotentRecordMapper, RETENTION_HOURS);
    }

    @Test
    @DisplayName("未携带幂等键：放行且不写记录（兼容旧调用方）")
    void claimWithoutKey() {
        assertThat(idempotentService.claim(UID, SCENE, null)).isNull();
        assertThat(idempotentService.claim(UID, SCENE, "  ")).isNull();
        verify(idempotentRecordMapper, never()).insert(any(IdempotentRecord.class));
    }

    @Test
    @DisplayName("首次占位：写入记录并设置过期时间")
    void claimFirstTime() {
        assertThat(idempotentService.claim(UID, SCENE, "key-1")).isNull();

        ArgumentCaptor<IdempotentRecord> captor = ArgumentCaptor.forClass(IdempotentRecord.class);
        verify(idempotentRecordMapper).insert(captor.capture());
        IdempotentRecord saved = captor.getValue();
        assertThat(saved.getScene()).isEqualTo(SCENE);
        assertThat(saved.getUserId()).isEqualTo(UID);
        assertThat(saved.getIdemKey()).isEqualTo("key-1");
        assertThat(saved.getBizNo()).isNull();
        // 过期时间 = 当前 + 保留时长（允许几秒误差）
        assertThat(saved.getExpireAt())
                .isBetween(LocalDateTime.now().plusHours(RETENTION_HOURS).minusMinutes(1),
                        LocalDateTime.now().plusHours(RETENTION_HOURS).plusMinutes(1));
    }

    @Test
    @DisplayName("幂等键首尾空白会被裁剪，避免同一键写成两行")
    void claimTrimsKey() {
        idempotentService.claim(UID, SCENE, "  key-1  ");

        ArgumentCaptor<IdempotentRecord> captor = ArgumentCaptor.forClass(IdempotentRecord.class);
        verify(idempotentRecordMapper).insert(captor.capture());
        assertThat(captor.getValue().getIdemKey()).isEqualTo("key-1");
    }

    @Test
    @DisplayName("唯一键冲突：回放首次成功的业务单号")
    void claimDuplicateReplaysBizNo() {
        when(idempotentRecordMapper.insert(any(IdempotentRecord.class)))
                .thenThrow(new DuplicateKeyException("uk_scene_user_key"));
        when(idempotentRecordMapper.selectOne(any(Wrapper.class)))
                .thenReturn(record("NO20261018120000001"));

        assertThat(idempotentService.claim(UID, SCENE, "key-1")).isEqualTo("NO20261018120000001");
    }

    @Test
    @DisplayName("冲突但未取到业务单号：拒绝并提示稍后重试")
    void claimDuplicateWithoutBizNo() {
        when(idempotentRecordMapper.insert(any(IdempotentRecord.class)))
                .thenThrow(new DuplicateKeyException("uk_scene_user_key"));
        when(idempotentRecordMapper.selectOne(any(Wrapper.class))).thenReturn(record(null));

        assertThatThrownBy(() -> idempotentService.claim(UID, SCENE, "key-1"))
                .isInstanceOf(BizException.class)
                .hasMessage("请求处理中，请稍后重试");
    }

    @Test
    @DisplayName("冲突但回查为空：同样按处理中拒绝，不静默放行")
    void claimDuplicateWithMissingRow() {
        when(idempotentRecordMapper.insert(any(IdempotentRecord.class)))
                .thenThrow(new DuplicateKeyException("uk_scene_user_key"));
        when(idempotentRecordMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> idempotentService.claim(UID, SCENE, "key-1"))
                .isInstanceOf(BizException.class)
                .hasMessage("请求处理中，请稍后重试");
    }

    @Test
    @DisplayName("幂等键超长：拒绝并提示长度上限")
    void claimRejectsTooLongKey() {
        assertThatThrownBy(() -> idempotentService.claim(UID, SCENE, "k".repeat(65)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("64");
        verify(idempotentRecordMapper, never()).insert(any(IdempotentRecord.class));
    }

    private IdempotentRecord record(String bizNo) {
        IdempotentRecord r = new IdempotentRecord();
        r.setId(1L);
        r.setScene(SCENE);
        r.setUserId(UID);
        r.setIdemKey("key-1");
        r.setBizNo(bizNo);
        r.setExpireAt(LocalDateTime.now().plusHours(1));
        return r;
    }
}
