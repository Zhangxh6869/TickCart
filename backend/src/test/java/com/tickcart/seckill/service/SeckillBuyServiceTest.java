package com.tickcart.seckill.service;

import com.tickcart.common.exception.BizException;
import com.tickcart.common.exception.RateLimitedException;
import com.tickcart.member.entity.UserAddress;
import com.tickcart.member.service.AddressService;
import com.tickcart.seckill.cache.SeckillActivitySnapshot;
import com.tickcart.seckill.mq.SeckillMessageProducer;
import com.tickcart.seckill.mq.SeckillOrderMessage;
import com.tickcart.seckill.vo.SeckillBuyResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SeckillBuyService} 单元测试。
 *
 * <p>覆盖抢购编排的每条分支：未预热、未开始、已结束、超限购、重复提交、售罄、
 * 投递失败回补、成功受理。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("秒杀抢购服务")
class SeckillBuyServiceTest {

    private static final Long ACTIVITY_ID = 9L;
    private static final Long USER_ID = 2L;
    private static final Long ADDRESS_ID = 124L;

    @Mock
    private SeckillStockService stockService;
    @Mock
    private SeckillMessageProducer messageProducer;
    @Mock
    private AddressService addressService;

    private SeckillBuyService service;

    @BeforeEach
    void setUp() {
        service = new SeckillBuyService(stockService, messageProducer, addressService);
        // 默认地址有效；需要验证「地址无效」的用例单独覆盖
        UserAddress address = new UserAddress();
        address.setId(ADDRESS_ID);
        address.setUserId(USER_ID);
        lenient().when(addressService.getOwned(USER_ID, ADDRESS_ID)).thenReturn(address);
    }

    @Test
    @DisplayName("未预热：503，提示稍后重试")
    void notPreloaded() {
        when(stockService.snapshot(ACTIVITY_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.buy(USER_ID, ACTIVITY_ID, 1, ADDRESS_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("尚未就绪");
    }

    @Test
    @DisplayName("未开始：409，且不进 Lua")
    void pendingActivity() {
        SeckillActivitySnapshot pending = snapshot(LocalDateTime.now().plusHours(2));
        pending.setStartTime(LocalDateTime.now().plusMinutes(30));
        when(stockService.snapshot(ACTIVITY_ID)).thenReturn(pending);

        assertThatThrownBy(() -> service.buy(USER_ID, ACTIVITY_ID, 1, ADDRESS_ID))
                .isInstanceOf(BizException.class)
                .hasMessage("活动尚未开始");
        verify(stockService, never()).tryDeduct(anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("已结束：409")
    void endedActivity() {
        when(stockService.snapshot(ACTIVITY_ID)).thenReturn(snapshot(LocalDateTime.now().minusHours(1)));

        assertThatThrownBy(() -> service.buy(USER_ID, ACTIVITY_ID, 1, ADDRESS_ID))
                .isInstanceOf(BizException.class)
                .hasMessage("活动已结束");
    }

    @Test
    @DisplayName("已下线：按已结束处理（快照 enabled=0）")
    void disabledActivity() {
        SeckillActivitySnapshot snapshot = snapshot(LocalDateTime.now().plusHours(1));
        snapshot.setEnabled(0);
        when(stockService.snapshot(ACTIVITY_ID)).thenReturn(snapshot);

        assertThatThrownBy(() -> service.buy(USER_ID, ACTIVITY_ID, 1, ADDRESS_ID))
                .isInstanceOf(BizException.class)
                .hasMessage("活动已结束");
    }

    @Test
    @DisplayName("超过限购：提前挡，不执行 Lua")
    void overLimitRequested() {
        when(stockService.snapshot(ACTIVITY_ID)).thenReturn(snapshot(LocalDateTime.now().plusHours(1)));

        assertThatThrownBy(() -> service.buy(USER_ID, ACTIVITY_ID, 3, ADDRESS_ID))
                .isInstanceOf(BizException.class)
                .hasMessage("每人限购 2 件");
        verify(stockService, never()).tryDeduct(anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("重复提交：429，不执行 Lua")
    void duplicateRequest() {
        when(stockService.snapshot(ACTIVITY_ID)).thenReturn(snapshot(LocalDateTime.now().plusHours(1)));
        when(stockService.tryLockRequest(anyLong(), anyLong(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.buy(USER_ID, ACTIVITY_ID, 1, ADDRESS_ID))
                .isInstanceOf(RateLimitedException.class);
        verify(stockService, never()).tryDeduct(anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("收货地址无效：拒绝，且不扣库存（不消耗名额）")
    void invalidAddressRejected() {
        when(stockService.snapshot(ACTIVITY_ID)).thenReturn(snapshot(LocalDateTime.now().plusHours(1)));
        when(stockService.tryLockRequest(anyLong(), anyLong(), anyString())).thenReturn(true);
        when(addressService.getOwned(USER_ID, ADDRESS_ID))
                .thenThrow(BizException.notFound("收货地址不存在"));

        assertThatThrownBy(() -> service.buy(USER_ID, ACTIVITY_ID, 1, ADDRESS_ID))
                .isInstanceOf(BizException.class)
                .hasMessage("收货地址不存在");
        verify(stockService, never()).tryDeduct(anyLong(), anyLong(), anyInt(), anyInt());
        verify(stockService).unlockRequest(ACTIVITY_ID, USER_ID);
    }

    @Test
    @DisplayName("售罄：409，并释放请求标记让用户可重试")
    void soldOut() {
        when(stockService.snapshot(ACTIVITY_ID)).thenReturn(snapshot(LocalDateTime.now().plusHours(1)));
        when(stockService.tryLockRequest(anyLong(), anyLong(), anyString())).thenReturn(true);
        when(stockService.tryDeduct(anyLong(), anyLong(), anyInt(), anyInt()))
                .thenReturn(SeckillStockService.RESULT_SOLD_OUT);

        assertThatThrownBy(() -> service.buy(USER_ID, ACTIVITY_ID, 1, ADDRESS_ID))
                .isInstanceOf(BizException.class)
                .hasMessage("已抢光");
        verify(stockService).unlockRequest(ACTIVITY_ID, USER_ID);
    }

    @Test
    @DisplayName("Lua 判定超限购：409")
    void overLimitByLua() {
        when(stockService.snapshot(ACTIVITY_ID)).thenReturn(snapshot(LocalDateTime.now().plusHours(1)));
        when(stockService.tryLockRequest(anyLong(), anyLong(), anyString())).thenReturn(true);
        when(stockService.tryDeduct(anyLong(), anyLong(), anyInt(), anyInt()))
                .thenReturn(SeckillStockService.RESULT_OVER_LIMIT);

        assertThatThrownBy(() -> service.buy(USER_ID, ACTIVITY_ID, 1, ADDRESS_ID))
                .isInstanceOf(BizException.class)
                .hasMessage("每人限购 2 件");
    }

    @Test
    @DisplayName("MQ 投递失败：回补库存 + 释放标记 + 503")
    void sendFailureRollsBackStock() {
        when(stockService.snapshot(ACTIVITY_ID)).thenReturn(snapshot(LocalDateTime.now().plusHours(1)));
        when(stockService.tryLockRequest(anyLong(), anyLong(), anyString())).thenReturn(true);
        when(stockService.tryDeduct(anyLong(), anyLong(), anyInt(), anyInt())).thenReturn(99L);
        when(messageProducer.sendOrder(any())).thenReturn(false);

        assertThatThrownBy(() -> service.buy(USER_ID, ACTIVITY_ID, 1, ADDRESS_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("下单繁忙");

        verify(stockService).restore(ACTIVITY_ID, USER_ID, 1);
        verify(stockService).unlockRequest(ACTIVITY_ID, USER_ID);
    }

    @Test
    @DisplayName("成功受理：返回 requestId 与剩余库存，消息带价格快照")
    void success() {
        when(stockService.snapshot(ACTIVITY_ID)).thenReturn(snapshot(LocalDateTime.now().plusHours(1)));
        when(stockService.tryLockRequest(anyLong(), anyLong(), anyString())).thenReturn(true);
        when(stockService.tryDeduct(anyLong(), anyLong(), anyInt(), anyInt())).thenReturn(99L);
        when(messageProducer.sendOrder(any())).thenReturn(true);

        SeckillBuyResultVO result = service.buy(USER_ID, ACTIVITY_ID, 1, ADDRESS_ID);

        assertThat(result.getRequestId()).isNotBlank();
        assertThat(result.getRemainingStock()).isEqualTo(99L);
        assertThat(result.getStatus()).isEqualTo("QUEUED");

        ArgumentCaptor<SeckillOrderMessage> captor = ArgumentCaptor.forClass(SeckillOrderMessage.class);
        verify(messageProducer).sendOrder(captor.capture());
        SeckillOrderMessage message = captor.getValue();
        assertThat(message.getActivityId()).isEqualTo(ACTIVITY_ID);
        assertThat(message.getUserId()).isEqualTo(USER_ID);
        assertThat(message.getSeckillPrice()).isEqualByComparingTo("9.90");
        assertThat(message.getAddressId()).isEqualTo(ADDRESS_ID);
        // 成功后不释放标记：挡住建单排队期间的连点
        verify(stockService, never()).unlockRequest(anyLong(), anyLong());
    }

    private SeckillActivitySnapshot snapshot(LocalDateTime endTime) {
        SeckillActivitySnapshot snapshot = new SeckillActivitySnapshot();
        snapshot.setActivityId(ACTIVITY_ID);
        snapshot.setProductId(302L);
        snapshot.setSkuId(490L);
        snapshot.setSeckillPrice(new BigDecimal("9.90"));
        snapshot.setLimitPerUser(2);
        snapshot.setStartTime(LocalDateTime.now().minusMinutes(5));
        snapshot.setEndTime(endTime);
        snapshot.setEnabled(1);
        return snapshot;
    }
}
