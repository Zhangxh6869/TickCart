package com.tickcart.trade.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tickcart.cart.service.CartService;
import com.tickcart.cart.vo.CartItemVO;
import com.tickcart.cart.vo.CartSummary;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.mq.OrderMessageProducer;
import com.tickcart.member.entity.UserAddress;
import com.tickcart.member.service.AddressService;
import com.tickcart.trade.dto.AdminOrderQuery;
import com.tickcart.trade.dto.OrderCreateRequest;
import com.tickcart.trade.dto.OrderQuery;
import com.tickcart.trade.entity.Order;
import com.tickcart.trade.entity.OrderItem;
import com.tickcart.trade.enums.OrderStatus;
import com.tickcart.trade.mapper.OrderItemMapper;
import com.tickcart.trade.mapper.OrderMapper;
import com.tickcart.trade.vo.AdminOrderListItemVO;
import com.tickcart.trade.vo.OrderDetailVO;
import com.tickcart.trade.vo.OrderVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link OrderService} 单元测试。
 *
 * <p>覆盖下单主链路（快照、扣库存、清购物车）、状态机防御与归属校验。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单服务")
class OrderServiceTest {

    private static final Long UID = 1001L;

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private AddressService addressService;
    @Mock
    private CartService cartService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private OrderMessageProducer orderMessageProducer;

    @InjectMocks
    private OrderService orderService;

    /**
     * 纯单元测试没有 Spring 容器，而 {@code LambdaUpdateWrapper.set(SFunction, value)}
     * 会立即解析列名，需先手动初始化实体的 TableInfo 缓存，否则报 "can not find lambda cache"。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
    }

    private UserAddress address() {
        UserAddress a = new UserAddress();
        a.setId(1L);
        a.setUserId(UID);
        a.setReceiver("张三");
        a.setPhone("13800138000");
        a.setProvince("广东省");
        a.setCity("深圳市");
        a.setDistrict("南山区");
        a.setDetail("科技园路 1 号");
        return a;
    }

    private CartItemVO cartItemVO(Long productId, Long skuId, int quantity, String price, boolean valid) {
        CartItemVO vo = new CartItemVO();
        vo.setId(productId * 100);
        vo.setProductId(productId);
        vo.setSkuId(skuId);
        vo.setQuantity(quantity);
        vo.setChecked(1);
        vo.setProductName("商品" + productId);
        vo.setProductImage("/img/" + productId + ".png");
        vo.setSpecValue(skuId > 0 ? "黑色" : null);
        vo.setPrice(new BigDecimal(price));
        vo.setStock(100);
        vo.setSubtotal(new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)));
        vo.setValid(valid);
        return vo;
    }

    private CartSummary summaryOf(CartItemVO... items) {
        CartSummary summary = new CartSummary();
        summary.setItems(List.of(items));
        summary.setTotalCount(items.length);
        return summary;
    }

    private Order order(String orderNo, Long userId, OrderStatus status) {
        Order o = new Order();
        o.setId(100L);
        o.setOrderNo(orderNo);
        o.setUserId(userId);
        o.setChannel(0);
        o.setStatus(status.getCode());
        o.setTotalAmount(new BigDecimal("198.00"));
        o.setPayAmount(new BigDecimal("198.00"));
        o.setReceiver("张三");
        o.setReceiverPhone("13800138000");
        o.setReceiverAddr("广东省深圳市南山区科技园路 1 号");
        return o;
    }

    private OrderCreateRequest createReq(Long addressId) {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(addressId);
        return req;
    }

    // ---------------- 下单 ----------------

    @Test
    @DisplayName("下单成功：生成订单号、写入快照、扣库存、清理购物车")
    void createSuccess() {
        when(addressService.getOwned(UID, 1L)).thenReturn(address());
        when(cartService.list(UID)).thenReturn(summaryOf(
                cartItemVO(10L, 0L, 2, "99.00", true)));
        when(orderMapper.insert(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return 1;
        });
        when(orderItemMapper.insert(any(OrderItem.class))).thenReturn(1);

        OrderVO result = orderService.create(UID, createReq(1L));

        assertThat(result.getOrderNo()).startsWith("NO");
        assertThat(result.getPayAmount()).isEqualByComparingTo("198.00");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT.getCode());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(UID);
        assertThat(saved.getReceiver()).isEqualTo("张三");
        assertThat(saved.getReceiverAddr()).isEqualTo("广东省深圳市南山区科技园路 1 号");
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT.getCode());

        verify(inventoryService).deduct(10L, 0L, 2);
        verify(cartService).deleteChecked(UID);
        // 下单后必须投递超时延迟消息，否则订单永远不会自动关闭
        verify(orderMessageProducer).sendOrderTimeout(result.getOrderNo());
    }

    @Test
    @DisplayName("订单明细保存商品快照并关联订单ID")
    void createWritesItemSnapshot() {
        when(addressService.getOwned(UID, 1L)).thenReturn(address());
        when(cartService.list(UID)).thenReturn(summaryOf(cartItemVO(10L, 5L, 3, "50.00", true)));
        when(orderMapper.insert(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return 1;
        });
        when(orderItemMapper.insert(any(OrderItem.class))).thenReturn(1);

        orderService.create(UID, createReq(1L));

        ArgumentCaptor<OrderItem> captor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemMapper).insert(captor.capture());
        OrderItem item = captor.getValue();
        assertThat(item.getOrderId()).isEqualTo(100L);
        assertThat(item.getProductId()).isEqualTo(10L);
        assertThat(item.getSkuId()).isEqualTo(5L);
        assertThat(item.getProductName()).isEqualTo("商品10");
        assertThat(item.getSpecValue()).isEqualTo("黑色");
        assertThat(item.getPrice()).isEqualByComparingTo("50.00");
        assertThat(item.getSubtotal()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("无勾选商品时拒绝下单")
    void createWithoutCheckedItems() {
        when(addressService.getOwned(UID, 1L)).thenReturn(address());
        when(cartService.list(UID)).thenReturn(summaryOf());

        assertThatThrownBy(() -> orderService.create(UID, createReq(1L)))
                .isInstanceOf(BizException.class)
                .hasMessage("请先选择要结算的商品");
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("勾选项全部失效时拒绝下单并提示失效")
    void createWithOnlyInvalidItems() {
        when(addressService.getOwned(UID, 1L)).thenReturn(address());
        when(cartService.list(UID)).thenReturn(summaryOf(cartItemVO(10L, 0L, 1, "99.00", false)));

        assertThatThrownBy(() -> orderService.create(UID, createReq(1L)))
                .isInstanceOf(BizException.class)
                .hasMessage("勾选的商品已失效，请重新选择");
    }

    @Test
    @DisplayName("失效商品被排除，只结算有效项")
    void createSkipsInvalidItems() {
        when(addressService.getOwned(UID, 1L)).thenReturn(address());
        when(cartService.list(UID)).thenReturn(summaryOf(
                cartItemVO(10L, 0L, 2, "99.00", true),
                cartItemVO(11L, 0L, 1, "10.00", false)));
        when(orderMapper.insert(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return 1;
        });
        when(orderItemMapper.insert(any(OrderItem.class))).thenReturn(1);

        OrderVO result = orderService.create(UID, createReq(1L));

        assertThat(result.getPayAmount()).isEqualByComparingTo("198.00");
        verify(inventoryService).deduct(10L, 0L, 2);
        verify(inventoryService, never()).deduct(11L, 0L, 1);
    }

    @Test
    @DisplayName("库存不足时不写入订单")
    void createFailsOnInsufficientStock() {
        when(addressService.getOwned(UID, 1L)).thenReturn(address());
        when(cartService.list(UID)).thenReturn(summaryOf(cartItemVO(10L, 0L, 2, "99.00", true)));
        org.mockito.Mockito.doThrow(BizException.badRequest("库存不足"))
                .when(inventoryService).deduct(10L, 0L, 2);

        assertThatThrownBy(() -> orderService.create(UID, createReq(1L)))
                .isInstanceOf(BizException.class)
                .hasMessage("库存不足");
        verify(orderMapper, never()).insert(any(Order.class));
        verify(cartService, never()).deleteChecked(UID);
    }

    @Test
    @DisplayName("地址不属于当前用户时无法下单")
    void createWithForeignAddress() {
        when(addressService.getOwned(UID, 9L))
                .thenThrow(BizException.notFound("收货地址不存在"));

        assertThatThrownBy(() -> orderService.create(UID, createReq(9L)))
                .isInstanceOf(BizException.class)
                .hasMessage("收货地址不存在");
    }

    // ---------------- 取消与确认收货 ----------------

    @Test
    @DisplayName("取消待付款订单：置为已取消并回补库存")
    void cancelPendingPaymentRestoresStock() {
        when(orderMapper.selectOne(any())).thenReturn(order("NO1", UID, OrderStatus.PENDING_PAYMENT));
        when(orderItemMapper.selectList(any())).thenReturn(List.of());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        orderService.cancel(UID, "NO1");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED.getCode());
        assertThat(captor.getValue().getCancelTime()).isNotNull();
    }

    @Test
    @DisplayName("已支付订单不能取消")
    void cancelPaidOrderRejected() {
        when(orderMapper.selectOne(any())).thenReturn(order("NO2", UID, OrderStatus.PENDING_SHIPMENT));

        assertThatThrownBy(() -> orderService.cancel(UID, "NO2"))
                .isInstanceOf(BizException.class)
                .hasMessage("订单当前为「待发货」，无法取消");
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    @DisplayName("取消订单时按明细回补库存")
    void cancelRestoresStockFromItems() {
        OrderItem item = new OrderItem();
        item.setProductId(10L);
        item.setSkuId(5L);
        item.setQuantity(2);
        when(orderMapper.selectOne(any())).thenReturn(order("NO3", UID, OrderStatus.PENDING_PAYMENT));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        orderService.cancel(UID, "NO3");

        verify(inventoryService).restore(10L, 5L, 2);
    }

    @Test
    @DisplayName("确认收货：待收货置为已完成")
    void confirmReceipt() {
        when(orderMapper.selectOne(any())).thenReturn(order("NO4", UID, OrderStatus.PENDING_RECEIPT));
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        orderService.confirm(UID, "NO4");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.COMPLETED.getCode());
        assertThat(captor.getValue().getFinishTime()).isNotNull();
    }

    @Test
    @DisplayName("未发货订单不能确认收货")
    void confirmBeforeShipmentRejected() {
        when(orderMapper.selectOne(any())).thenReturn(order("NO5", UID, OrderStatus.PENDING_PAYMENT));

        assertThatThrownBy(() -> orderService.confirm(UID, "NO5"))
                .isInstanceOf(BizException.class)
                .hasMessage("订单当前为「待付款」，无法确认收货");
    }

    // ---------------- 支付推进与超时关单 ----------------

    @Test
    @DisplayName("支付成功：待付款置为待发货并写支付时间")
    void markPaidAdvancesStatus() {
        when(orderMapper.selectOne(any())).thenReturn(order("NO12", UID, OrderStatus.PENDING_PAYMENT));
        when(orderMapper.update(ArgumentMatchers.<Order>isNull(), any())).thenReturn(1);

        orderService.markPaid("NO12");

        verify(orderMapper).update(ArgumentMatchers.<Order>isNull(), any());
    }

    @Test
    @DisplayName("已支付订单不能重复推进")
    void markPaidRejectedWhenNotPending() {
        when(orderMapper.selectOne(any())).thenReturn(order("NO13", UID, OrderStatus.PENDING_SHIPMENT));

        assertThatThrownBy(() -> orderService.markPaid("NO13"))
                .isInstanceOf(BizException.class)
                .hasMessage("订单当前为「待发货」，无法支付");
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("并发下状态被抢先变更时支付失败")
    void markPaidRejectedWhenConcurrentUpdateLoses() {
        when(orderMapper.selectOne(any())).thenReturn(order("NO14", UID, OrderStatus.PENDING_PAYMENT));
        when(orderMapper.update(ArgumentMatchers.<Order>isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> orderService.markPaid("NO14"))
                .isInstanceOf(BizException.class)
                .hasMessage("订单状态已变更，请刷新后重试");
    }

    @Test
    @DisplayName("超时关单：待付款订单关闭并回补库存")
    void closeTimeoutPendingOrder() {
        OrderItem item = new OrderItem();
        item.setProductId(10L);
        item.setSkuId(5L);
        item.setQuantity(2);
        when(orderMapper.selectOne(any())).thenReturn(order("NO15", UID, OrderStatus.PENDING_PAYMENT));
        when(orderMapper.update(ArgumentMatchers.<Order>isNull(), any())).thenReturn(1);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));

        orderService.closeTimeout("NO15");

        verify(orderMapper).update(ArgumentMatchers.<Order>isNull(), any());
        verify(inventoryService).restore(10L, 5L, 2);
    }

    @Test
    @DisplayName("超时关单幂等：已支付订单不关单、不回补库存")
    void closeTimeoutSkipsPaidOrder() {
        when(orderMapper.selectOne(any())).thenReturn(order("NO16", UID, OrderStatus.PENDING_SHIPMENT));
        when(orderMapper.update(ArgumentMatchers.<Order>isNull(), any())).thenReturn(0);

        orderService.closeTimeout("NO16");

        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("超时关单幂等：订单不存在时静默跳过")
    void closeTimeoutSkipsUnknownOrder() {
        when(orderMapper.selectOne(any())).thenReturn(null);

        orderService.closeTimeout("NO_MISSING");

        verify(orderMapper, never()).update(any(), any());
        verifyNoInteractions(inventoryService);
    }

    // ---------------- 查询 ----------------

    @Test
    @DisplayName("查看他人订单按不存在处理")
    void detailOfForeignOrder() {
        when(orderMapper.selectOne(any())).thenReturn(order("NO6", 999L, OrderStatus.PENDING_PAYMENT));

        assertThatThrownBy(() -> orderService.detail(UID, "NO6"))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不存在");
    }

    @Test
    @DisplayName("订单详情返回收货快照与明细")
    void detailReturnsSnapshot() {
        when(orderMapper.selectOne(any())).thenReturn(order("NO7", UID, OrderStatus.PENDING_PAYMENT));
        OrderItem item = new OrderItem();
        item.setProductName("商品10");
        item.setQuantity(2);
        item.setSubtotal(new BigDecimal("198.00"));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));

        OrderDetailVO detail = orderService.detail(UID, "NO7");

        assertThat(detail.getOrderNo()).isEqualTo("NO7");
        assertThat(detail.getStatusLabel()).isEqualTo("待付款");
        assertThat(detail.getItems()).hasSize(1);
        assertThat(detail.getItems().get(0).getProductName()).isEqualTo("商品10");
    }

    @Test
    @DisplayName("订单列表按状态筛选并附带明细")
    void pageFiltersByStatus() {
        // 真实运行时 total 由 MyBatis-Plus 分页插件回填；单测里直接填充传入的 Page 对象
        when(orderMapper.selectPage(ArgumentMatchers.<IPage<Order>>any(), any())).thenAnswer(inv -> {
            IPage<Order> p = inv.getArgument(0);
            p.setRecords(List.of(order("NO8", UID, OrderStatus.PENDING_SHIPMENT)));
            p.setTotal(1);
            return p;
        });
        when(orderItemMapper.selectList(any())).thenReturn(List.of());

        OrderQuery query = new OrderQuery();
        query.setStatus(OrderStatus.PENDING_SHIPMENT.getCode());

        var result = orderService.page(UID, query);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getStatusLabel()).isEqualTo("待发货");
    }

    // ---------------- 后台：发货 ----------------

    @Test
    @DisplayName("发货：待发货订单置为待收货并写入发货时间")
    void shipAdvancesStatus() {
        when(orderMapper.selectOne(any())).thenReturn(order("NO20", UID, OrderStatus.PENDING_SHIPMENT));
        when(orderMapper.update(ArgumentMatchers.<Order>isNull(), any())).thenReturn(1);

        orderService.ship("NO20");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<Order>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(orderMapper).update(ArgumentMatchers.<Order>isNull(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains(OrderStatus.PENDING_RECEIPT.getCode())
                .anyMatch(value -> value instanceof LocalDateTime);
    }

    @Test
    @DisplayName("未付款订单不能发货")
    void shipRejectedWhenNotPaid() {
        when(orderMapper.selectOne(any())).thenReturn(order("NO21", UID, OrderStatus.PENDING_PAYMENT));

        assertThatThrownBy(() -> orderService.ship("NO21"))
                .isInstanceOf(BizException.class)
                .hasMessage("订单当前为「待付款」，无法发货");
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("已取消订单不能发货")
    void shipRejectedForCancelledOrder() {
        when(orderMapper.selectOne(any())).thenReturn(order("NO22", UID, OrderStatus.CANCELLED));

        assertThatThrownBy(() -> orderService.ship("NO22"))
                .isInstanceOf(BizException.class)
                .hasMessage("订单当前为「已取消」，无法发货");
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("并发下状态被抢先变更时发货失败")
    void shipRejectedWhenConcurrentUpdateLoses() {
        when(orderMapper.selectOne(any())).thenReturn(order("NO23", UID, OrderStatus.PENDING_SHIPMENT));
        when(orderMapper.update(ArgumentMatchers.<Order>isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> orderService.ship("NO23"))
                .isInstanceOf(BizException.class)
                .hasMessage("订单状态已变更，请刷新后重试");
    }

    // ---------------- 后台：查询 ----------------

    @Test
    @DisplayName("后台详情不校验归属，可查看他人订单")
    void adminDetailSkipsOwnershipCheck() {
        when(orderMapper.selectOne(any())).thenReturn(order("NO24", 999L, OrderStatus.PENDING_SHIPMENT));
        when(orderItemMapper.selectList(any())).thenReturn(List.of());

        OrderDetailVO detail = orderService.adminDetail("NO24");

        assertThat(detail.getOrderNo()).isEqualTo("NO24");
        assertThat(detail.getStatusLabel()).isEqualTo("待发货");
        assertThat(detail.getReceiverPhone()).isEqualTo("13800138000");
    }

    @Test
    @DisplayName("后台列表带出买家信息与商品件数")
    void adminPageMapsBuyerInfo() {
        when(orderMapper.selectPage(ArgumentMatchers.<IPage<Order>>any(), any())).thenAnswer(inv -> {
            IPage<Order> p = inv.getArgument(0);
            p.setRecords(List.of(order("NO25", UID, OrderStatus.PENDING_SHIPMENT)));
            p.setTotal(1);
            return p;
        });
        OrderItem item = new OrderItem();
        item.setQuantity(3);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));

        var result = orderService.adminPage(new AdminOrderQuery());

        assertThat(result.getTotal()).isEqualTo(1);
        AdminOrderListItemVO vo = result.getRecords().get(0);
        assertThat(vo.getOrderNo()).isEqualTo("NO25");
        assertThat(vo.getUserId()).isEqualTo(UID);
        assertThat(vo.getReceiver()).isEqualTo("张三");
        assertThat(vo.getReceiverPhone()).isEqualTo("13800138000");
        assertThat(vo.getTotalQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("后台列表关键字去空格后按 LIKE 查询")
    void adminPagePassesTrimmedKeyword() {
        when(orderMapper.selectPage(ArgumentMatchers.<IPage<Order>>any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        AdminOrderQuery query = new AdminOrderQuery();
        query.setKeyword("  NO25  ");
        orderService.adminPage(query);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Order>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(orderMapper).selectPage(ArgumentMatchers.<IPage<Order>>any(), captor.capture());
        LambdaQueryWrapper<Order> wrapper = captor.getValue();
        // like 的参数值是惰性写入的：先取 SQL 片段把占位符实体化，再断言参数
        assertThat(wrapper.getSqlSegment()).contains("order_no", "receiver_phone");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("%NO25%");
    }

    @Test
    @DisplayName("后台列表关键字为空白时不加模糊条件")
    void adminPageSkipsBlankKeyword() {
        when(orderMapper.selectPage(ArgumentMatchers.<IPage<Order>>any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        AdminOrderQuery query = new AdminOrderQuery();
        query.setKeyword("   ");
        orderService.adminPage(query);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Order>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(orderMapper).selectPage(ArgumentMatchers.<IPage<Order>>any(), captor.capture());
        // 空白关键字不产生任何 like 条件
        assertThat(captor.getValue().getSqlSegment()).doesNotContain("order_no");
        assertThat(captor.getValue().getParamNameValuePairs()).isEmpty();
    }
}
