package com.tickcart.trade.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tickcart.cart.service.CartService;
import com.tickcart.cart.vo.CartItemVO;
import com.tickcart.cart.vo.CartSummary;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.idempotent.service.IdempotentService;
import com.tickcart.common.mq.OrderMessageProducer;
import com.tickcart.member.entity.UserAddress;
import com.tickcart.member.service.AddressService;
import com.tickcart.product.entity.Product;
import com.tickcart.product.entity.ProductSku;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.product.mapper.ProductSkuMapper;
import com.tickcart.product.service.ProductService;
import com.tickcart.trade.dto.AdminOrderQuery;
import com.tickcart.trade.dto.OrderBuyNowRequest;
import com.tickcart.trade.dto.OrderCreateRequest;
import com.tickcart.trade.dto.OrderQuery;
import com.tickcart.trade.entity.Order;
import com.tickcart.trade.entity.OrderItem;
import com.tickcart.trade.entity.PaymentRecord;
import com.tickcart.trade.enums.OrderStatus;
import com.tickcart.trade.mapper.OrderItemMapper;
import com.tickcart.trade.mapper.OrderMapper;
import com.tickcart.trade.mapper.PaymentRecordMapper;
import com.tickcart.trade.vo.AdminOrderListItemVO;
import com.tickcart.trade.vo.OrderCountVO;
import com.tickcart.trade.vo.OrderDetailVO;
import com.tickcart.trade.vo.OrderVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductSkuMapper productSkuMapper;
    @Mock
    private ProductService productService;
    @Mock
    private PaymentRecordMapper paymentRecordMapper;
    @Mock
    private IdempotentService idempotentService;

    @InjectMocks
    private OrderService orderService;

    /**
     * 结算抢占默认成功：影响行数 = 传入的 id 数。
     *
     * <p>绝大多数用例关心的是下单主链路，不该为「抢占成功」逐条打桩；
     * 用 {@code lenient} 是因为少数用例（立即购买、地址校验失败）根本不会走到抢占，
     * 严格模式下会报 UnnecessaryStubbing。</p>
     */
    @BeforeEach
    void stubCartClaim() {
        lenient().when(cartService.claimForCheckout(any(), any()))
                .thenAnswer(inv -> ((List<?>) inv.getArgument(1)).size());
    }

    /**
     * 纯单元测试没有 Spring 容器，而 {@code LambdaUpdateWrapper.set(SFunction, value)}
     * 会立即解析列名，需先手动初始化实体的 TableInfo 缓存，否则报 "can not find lambda cache"。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Product.class);
    }

    /** 构造一条支付流水，用于后台列表取「最新一条」的断言。 */
    private PaymentRecord payment(Long id, String payNo, String orderNo) {
        PaymentRecord p = new PaymentRecord();
        p.setId(id);
        p.setPayNo(payNo);
        p.setOrderNo(orderNo);
        p.setChannel(2);
        p.setAmount(new BigDecimal("198.00"));
        p.setStatus(PaymentRecord.STATUS_SUCCESS);
        return p;
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

    private OrderItem item(Long productId, Long skuId, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        return item;
    }

    private OrderCreateRequest createReq(Long addressId) {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(addressId);
        return req;
    }

    private Product product(Long id, int status, String price, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName("商品" + id);
        p.setMainImage("/img/" + id + ".png");
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        p.setStatus(status);
        return p;
    }

    private ProductSku sku(Long id, Long productId, String price, int stock) {
        ProductSku s = new ProductSku();
        s.setId(id);
        s.setProductId(productId);
        s.setSpecName("颜色");
        s.setSpecValue("黑色");
        s.setSkuImage("/img/sku-" + id + ".png");
        s.setPrice(new BigDecimal(price));
        s.setStock(stock);
        return s;
    }

    private OrderBuyNowRequest buyNowReq(Long addressId, Long productId, Long skuId, int quantity) {
        OrderBuyNowRequest req = new OrderBuyNowRequest();
        req.setAddressId(addressId);
        req.setProductId(productId);
        req.setSkuId(skuId);
        req.setQuantity(quantity);
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

        OrderVO result = orderService.create(UID, createReq(1L), null);

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
        verify(cartService).claimForCheckout(UID, List.of(1000L));
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

        orderService.create(UID, createReq(1L), null);

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

        assertThatThrownBy(() -> orderService.create(UID, createReq(1L), null))
                .isInstanceOf(BizException.class)
                .hasMessage("请先选择要结算的商品");
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("勾选项全部失效时拒绝下单并提示失效")
    void createWithOnlyInvalidItems() {
        when(addressService.getOwned(UID, 1L)).thenReturn(address());
        when(cartService.list(UID)).thenReturn(summaryOf(cartItemVO(10L, 0L, 1, "99.00", false)));

        assertThatThrownBy(() -> orderService.create(UID, createReq(1L), null))
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

        OrderVO result = orderService.create(UID, createReq(1L), null);

        assertThat(result.getPayAmount()).isEqualByComparingTo("198.00");
        verify(inventoryService).deduct(10L, 0L, 2);
        verify(inventoryService, never()).deduct(11L, 0L, 1);
    }

    @Test
    @DisplayName("库存不足时不写入订单、不投递超时消息")
    void createFailsOnInsufficientStock() {
        when(addressService.getOwned(UID, 1L)).thenReturn(address());
        when(cartService.list(UID)).thenReturn(summaryOf(cartItemVO(10L, 0L, 2, "99.00", true)));
        org.mockito.Mockito.doThrow(BizException.badRequest("库存不足"))
                .when(inventoryService).deduct(10L, 0L, 2);

        assertThatThrownBy(() -> orderService.create(UID, createReq(1L), null))
                .isInstanceOf(BizException.class)
                .hasMessage("库存不足");
        // 抢占发生在扣库存之前（失败更快、不碰库存行），其回滚由数据库事务保证，
        // 单测里无法断言「deleted 回到 0」，只断言不会再往下走到建单与投递。
        verify(cartService).claimForCheckout(UID, List.of(1000L));
        verify(orderMapper, never()).insert(any(Order.class));
        verify(orderMessageProducer, never()).sendOrderTimeout(any());
    }

    @Test
    @DisplayName("地址不属于当前用户时无法下单")
    void createWithForeignAddress() {
        when(addressService.getOwned(UID, 9L))
                .thenThrow(BizException.notFound("收货地址不存在"));

        assertThatThrownBy(() -> orderService.create(UID, createReq(9L), null))
                .isInstanceOf(BizException.class)
                .hasMessage("收货地址不存在");
    }

    // ---------------- 幂等与结算抢占 ----------------

    @Test
    @DisplayName("幂等命中：同一幂等键重复提交直接回放首次订单，不再扣库存与建单")
    void createReplaysWhenIdempotentKeyHit() {
        when(idempotentService.claim(UID, IdempotentService.SCENE_ORDER_CREATE, "key-1"))
                .thenReturn("NO20261018120000001");
        when(orderMapper.selectOne(any())).thenReturn(
                order("NO20261018120000001", UID, OrderStatus.PENDING_PAYMENT));

        OrderVO result = orderService.create(UID, createReq(1L), "key-1");

        assertThat(result.getOrderNo()).isEqualTo("NO20261018120000001");
        // 回放路径不得触碰任何写操作：不查地址、不读购物车、不扣库存、不建单
        verifyNoInteractions(addressService, inventoryService, cartService, orderItemMapper);
        verify(orderMapper, never()).insert(any(Order.class));
        verify(orderMessageProducer, never()).sendOrderTimeout(any());
    }

    @Test
    @DisplayName("购物车已被并发结算时拒绝下单（抢占影响行数不足）")
    void createFailsWhenCartAlreadyClaimed() {
        when(addressService.getOwned(UID, 1L)).thenReturn(address());
        when(cartService.list(UID)).thenReturn(summaryOf(cartItemVO(10L, 0L, 2, "99.00", true)));
        // 用 doReturn 而非 when(...)：后者会真的调用一次方法，命中 @BeforeEach 里
        // 基于参数的 Answer，导致空参 NPE
        doReturn(0).when(cartService).claimForCheckout(any(), any());

        assertThatThrownBy(() -> orderService.create(UID, createReq(1L), null))
                .isInstanceOf(BizException.class)
                .hasMessage("购物车已被其他请求结算，请刷新后重试");
        verify(inventoryService, never()).deduct(any(), any(), any(Integer.class));
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("下单后按精确 id 清理失效勾选项，只抢占有效项")
    void createRemovesInvalidItemsById() {
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

        orderService.create(UID, createReq(1L), null);

        verify(cartService).claimForCheckout(UID, List.of(1000L));
        verify(cartService).removeItems(UID, List.of(1100L));
    }

    // ---------------- 立即购买（不经过购物车） ----------------

    @Test
    @DisplayName("立即购买成功：扣库存、写订单与快照，且完全不触碰购物车")
    void buyNowSuccess() {
        when(addressService.getOwned(UID, 1L)).thenReturn(address());
        when(productMapper.selectById(10L)).thenReturn(product(10L, 1, "99.00", 50));
        when(productSkuMapper.selectById(5L)).thenReturn(sku(5L, 10L, "88.00", 20));
        when(orderMapper.insert(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return 1;
        });
        when(orderItemMapper.insert(any(OrderItem.class))).thenReturn(1);

        OrderVO result = orderService.createBuyNow(UID, buyNowReq(1L, 10L, 5L, 2), null);

        assertThat(result.getOrderNo()).startsWith("NO");
        assertThat(result.getPayAmount()).isEqualByComparingTo("176.00");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT.getCode());

        verify(inventoryService).deduct(10L, 5L, 2);

        ArgumentCaptor<OrderItem> captor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemMapper).insert(captor.capture());
        OrderItem item = captor.getValue();
        assertThat(item.getOrderId()).isEqualTo(100L);
        assertThat(item.getProductId()).isEqualTo(10L);
        assertThat(item.getSkuId()).isEqualTo(5L);
        assertThat(item.getProductName()).isEqualTo("商品10");
        assertThat(item.getSpecValue()).isEqualTo("黑色");
        // 主图与成交价都取规格自身的，口径必须与购物车结算一致
        assertThat(item.getProductImage()).isEqualTo("/img/sku-5.png");
        assertThat(item.getPrice()).isEqualByComparingTo("88.00");
        assertThat(item.getSubtotal()).isEqualByComparingTo("176.00");

        // 「不经过购物车」是本功能的核心语义：既不读购物车，也不清理已勾选项
        verifyNoInteractions(cartService);
        verify(orderMessageProducer).sendOrderTimeout(result.getOrderNo());
    }

    @Test
    @DisplayName("立即购买无规格商品：按商品级价格与库存成交")
    void buyNowWithoutSkuUsesProductPrice() {
        when(addressService.getOwned(UID, 1L)).thenReturn(address());
        when(productMapper.selectById(10L)).thenReturn(product(10L, 1, "99.00", 50));
        when(productService.hasRealSpec(10L)).thenReturn(false);
        when(orderMapper.insert(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return 1;
        });
        when(orderItemMapper.insert(any(OrderItem.class))).thenReturn(1);

        OrderVO result = orderService.createBuyNow(UID, buyNowReq(1L, 10L, 0L, 1), null);

        assertThat(result.getPayAmount()).isEqualByComparingTo("99.00");
        verify(inventoryService).deduct(10L, 0L, 1);
        verifyNoInteractions(cartService);
    }

    @Test
    @DisplayName("立即购买：商品配置了规格却未选规格时拒绝")
    void buyNowWithoutSkuWhenProductHasSpec() {
        when(addressService.getOwned(UID, 1L)).thenReturn(address());
        when(productMapper.selectById(10L)).thenReturn(product(10L, 1, "99.00", 50));
        when(productService.hasRealSpec(10L)).thenReturn(true);

        assertThatThrownBy(() -> orderService.createBuyNow(UID, buyNowReq(1L, 10L, 0L, 1), null))
                .isInstanceOf(BizException.class)
                .hasMessage("请选择商品规格");
        verify(orderMapper, never()).insert(any(Order.class));
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("立即购买：规格不属于该商品时拒绝")
    void buyNowWithForeignSku() {
        when(addressService.getOwned(UID, 1L)).thenReturn(address());
        when(productMapper.selectById(10L)).thenReturn(product(10L, 1, "99.00", 50));
        when(productSkuMapper.selectById(5L)).thenReturn(sku(5L, 11L, "88.00", 20));

        assertThatThrownBy(() -> orderService.createBuyNow(UID, buyNowReq(1L, 10L, 5L, 1), null))
                .isInstanceOf(BizException.class)
                .hasMessage("商品规格不存在");
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("立即购买：商品已下架时拒绝")
    void buyNowRejectsOfflineProduct() {
        when(addressService.getOwned(UID, 1L)).thenReturn(address());
        when(productMapper.selectById(10L)).thenReturn(product(10L, 0, "99.00", 50));

        assertThatThrownBy(() -> orderService.createBuyNow(UID, buyNowReq(1L, 10L, 5L, 1), null))
                .isInstanceOf(BizException.class)
                .hasMessage("商品不存在或已下架");
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("立即购买：规格库存不足时不写入订单")
    void buyNowFailsOnInsufficientSkuStock() {
        when(addressService.getOwned(UID, 1L)).thenReturn(address());
        when(productMapper.selectById(10L)).thenReturn(product(10L, 1, "99.00", 50));
        when(productSkuMapper.selectById(5L)).thenReturn(sku(5L, 10L, "88.00", 1));

        assertThatThrownBy(() -> orderService.createBuyNow(UID, buyNowReq(1L, 10L, 5L, 2), null))
                .isInstanceOf(BizException.class)
                .hasMessage("库存不足");
        verify(orderMapper, never()).insert(any(Order.class));
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("立即购买：地址不属于当前用户时无法下单")
    void buyNowWithForeignAddress() {
        when(addressService.getOwned(UID, 9L))
                .thenThrow(BizException.notFound("收货地址不存在"));

        assertThatThrownBy(() -> orderService.createBuyNow(UID, buyNowReq(9L, 10L, 5L, 1), null))
                .isInstanceOf(BizException.class)
                .hasMessage("收货地址不存在");
        verifyNoInteractions(inventoryService);
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
        // 没抢到状态流转就不能累加销量，否则重复回调会把销量加多次
        verifyNoInteractions(productMapper);
    }

    @Test
    @DisplayName("支付成功：按购买数量累加商品销量，同商品多规格合并成一次 UPDATE")
    void markPaidAccumulatesSalesByQuantity() {
        when(orderMapper.selectOne(any())).thenReturn(order("NO17", UID, OrderStatus.PENDING_PAYMENT));
        when(orderMapper.update(ArgumentMatchers.<Order>isNull(), any())).thenReturn(1);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(
                item(10L, 5L, 2),
                item(10L, 6L, 3),
                item(11L, 0L, 1)));

        orderService.markPaid("NO17");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Product>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(productMapper, times(2)).update(ArgumentMatchers.<Product>isNull(), captor.capture());

        List<Wrapper<Product>> wrappers = captor.getAllValues();
        // 累加语句落在 set 段（getSqlSet），where 段（getSqlSegment）只有 id 条件
        assertThat(wrappers.stream().map(w -> ((LambdaUpdateWrapper<Product>) w).getSqlSet()))
                .allMatch(sqlSet -> sqlSet != null && sqlSet.contains("sales = sales +"));
        // 商品 10 的两行规格合并成 2 + 3 = 5，商品 11 是 1
        assertThat(wrappers.stream()
                .map(w -> ((LambdaUpdateWrapper<Product>) w).getParamNameValuePairs().values())
                .flatMap(values -> values.stream())
                .toList())
                .contains(5, 1);
    }

    @Test
    @DisplayName("支付成功但订单无明细时不触碰商品表")
    void markPaidWithoutItemsSkipsSales() {
        when(orderMapper.selectOne(any())).thenReturn(order("NO18", UID, OrderStatus.PENDING_PAYMENT));
        when(orderMapper.update(ArgumentMatchers.<Order>isNull(), any())).thenReturn(1);
        when(orderItemMapper.selectList(any())).thenReturn(List.of());

        orderService.markPaid("NO18");

        verifyNoInteractions(productMapper);
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
        // 批量分组依赖 orderNo，测试数据必须带上（单查时可以不填）
        item.setOrderNo("NO25");
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
    @DisplayName("后台列表带出最新一笔支付流水号与渠道")
    void adminPageMapsLatestPayment() {
        when(orderMapper.selectPage(ArgumentMatchers.<IPage<Order>>any(), any())).thenAnswer(inv -> {
            IPage<Order> p = inv.getArgument(0);
            p.setRecords(List.of(order("NO26", UID, OrderStatus.PENDING_SHIPMENT)));
            p.setTotal(1);
            return p;
        });
        when(orderItemMapper.selectList(any())).thenReturn(List.of());
        // 同一订单两条流水：应取 id 更大的那条（重试后新落的一条）
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(
                payment(1L, "PAY_OLD", "NO26"),
                payment(2L, "PAY_NEW", "NO26")));

        var result = orderService.adminPage(new AdminOrderQuery());

        AdminOrderListItemVO vo = result.getRecords().get(0);
        assertThat(vo.getPayNo()).isEqualTo("PAY_NEW");
        assertThat(vo.getChannel()).isEqualTo(2);
    }

    @Test
    @DisplayName("后台列表：未发起过支付时流水字段为空")
    void adminPageWithoutPayment() {
        when(orderMapper.selectPage(ArgumentMatchers.<IPage<Order>>any(), any())).thenAnswer(inv -> {
            IPage<Order> p = inv.getArgument(0);
            p.setRecords(List.of(order("NO27", UID, OrderStatus.PENDING_PAYMENT)));
            p.setTotal(1);
            return p;
        });
        when(orderItemMapper.selectList(any())).thenReturn(List.of());
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of());

        var result = orderService.adminPage(new AdminOrderQuery());

        AdminOrderListItemVO vo = result.getRecords().get(0);
        assertThat(vo.getPayNo()).isNull();
        assertThat(vo.getChannel()).isNull();
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

    // ---------------- 各状态订单数量（「我的」页宫格角标） ----------------

    /** 构造一行 GROUP BY 结果：列名与 Service 里的 SQL 别名保持一致。 */
    private Map<String, Object> countRow(OrderStatus status, long count) {
        Map<String, Object> row = new HashMap<>();
        row.put("status", status.getCode());
        row.put("cnt", count);
        return row;
    }

    @Test
    @DisplayName("各状态数量：分组结果映射到四个状态，终态被忽略")
    void countByStatusMapsGroupedRows() {
        when(orderMapper.selectMaps(ArgumentMatchers.<Wrapper<Order>>any())).thenReturn(List.of(
                countRow(OrderStatus.PENDING_PAYMENT, 2),
                countRow(OrderStatus.PENDING_RECEIPT, 1),
                countRow(OrderStatus.COMPLETED, 9),
                countRow(OrderStatus.CANCELLED, 4),
                countRow(OrderStatus.TIMEOUT_CLOSED, 1)));

        OrderCountVO vo = orderService.countByStatus(UID);

        assertThat(vo.getPendingPayment()).isEqualTo(2);
        // 分组结果里没有待发货，VO 上保持 0，前端不会出现 null 角标
        assertThat(vo.getPendingShipment()).isZero();
        assertThat(vo.getPendingReceipt()).isEqualTo(1);
        assertThat(vo.getCompleted()).isEqualTo(9);
    }

    @Test
    @DisplayName("各状态数量：没有订单时四项全为 0")
    void countByStatusReturnsZerosWhenNoOrder() {
        when(orderMapper.selectMaps(ArgumentMatchers.<Wrapper<Order>>any())).thenReturn(List.of());

        OrderCountVO vo = orderService.countByStatus(UID);

        assertThat(vo.getPendingPayment()).isZero();
        assertThat(vo.getPendingShipment()).isZero();
        assertThat(vo.getPendingReceipt()).isZero();
        assertThat(vo.getCompleted()).isZero();
    }

    @Test
    @DisplayName("各状态数量：只统计当前用户，一次分组查询拿全")
    void countByStatusScopesToCurrentUser() {
        when(orderMapper.selectMaps(ArgumentMatchers.<Wrapper<Order>>any())).thenReturn(List.of());

        orderService.countByStatus(UID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<Order>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        // verify 默认就是「恰好一次」：既断言只查了一次，也顺带排除「四个角标各查一次」
        verify(orderMapper).selectMaps(captor.capture());
        QueryWrapper<Order> wrapper = captor.getValue();
        // 先取 SQL 片段把占位符实体化，再断言参数值（eq 的参数是惰性写入的）
        assertThat(wrapper.getSqlSegment()).contains("user_id").contains("GROUP BY");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(UID);
    }

    @Test
    @DisplayName("各状态数量：结果集列名大小写不一致时仍能取到值")
    void countByStatusIsCaseInsensitiveOnColumnNames() {
        Map<String, Object> row = new HashMap<>();
        row.put("STATUS", OrderStatus.PENDING_SHIPMENT.getCode());
        row.put("CNT", 3L);
        when(orderMapper.selectMaps(ArgumentMatchers.<Wrapper<Order>>any())).thenReturn(List.of(row));

        assertThat(orderService.countByStatus(UID).getPendingShipment()).isEqualTo(3);
    }
}
