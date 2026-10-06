package com.tickcart.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tickcart.cart.service.CartService;
import com.tickcart.cart.vo.CartItemVO;
import com.tickcart.cart.vo.CartSummary;
import com.tickcart.common.api.PageResult;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.mq.OrderMessageProducer;
import com.tickcart.member.entity.UserAddress;
import com.tickcart.member.service.AddressService;
import com.tickcart.trade.dto.OrderCreateRequest;
import com.tickcart.trade.dto.OrderQuery;
import com.tickcart.trade.entity.Order;
import com.tickcart.trade.entity.OrderItem;
import com.tickcart.trade.enums.OrderStatus;
import com.tickcart.trade.mapper.OrderItemMapper;
import com.tickcart.trade.mapper.OrderMapper;
import com.tickcart.trade.vo.OrderDetailVO;
import com.tickcart.trade.vo.OrderItemVO;
import com.tickcart.trade.vo.OrderListItemVO;
import com.tickcart.trade.vo.OrderVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 订单服务：下单、查询、取消、确认收货。
 *
 * <p>状态流转一律经 {@link OrderStatus} 状态机校验，非法跳转直接抛业务异常。</p>
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** 普通订单号前缀（秒杀订单为 SK） */
    private static final String ORDER_NO_PREFIX = "NO";

    /** 普通订单渠道标识 */
    private static final int CHANNEL_NORMAL = 0;

    /** 无规格商品的 SKU 占位值 */
    private static final long NO_SKU = 0L;

    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final AddressService addressService;
    private final CartService cartService;
    private final InventoryService inventoryService;
    private final OrderMessageProducer orderMessageProducer;

    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        AddressService addressService,
                        CartService cartService,
                        InventoryService inventoryService,
                        OrderMessageProducer orderMessageProducer) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.addressService = addressService;
        this.cartService = cartService;
        this.inventoryService = inventoryService;
        this.orderMessageProducer = orderMessageProducer;
    }

    /**
     * 结算下单：把购物车中「已勾选且有效」的商品生成订单。
     *
     * <p>整体在一个事务内完成：扣库存 → 写订单 → 写明细 → 清空已结算的购物车项。
     * 任一步失败（典型如库存不足）整体回滚，不会留下扣了库存却没有订单的脏数据。</p>
     *
     * @throws BizException 地址不属于当前用户、无勾选商品、库存不足
     */
    @Transactional
    public OrderVO create(Long userId, OrderCreateRequest req) {
        UserAddress address = addressService.getOwned(userId, req.getAddressId());

        CartSummary cart = cartService.list(userId);
        List<CartItemVO> checkedItems = cart.getItems().stream()
                .filter(item -> Integer.valueOf(1).equals(item.getChecked()))
                .collect(Collectors.toList());
        if (checkedItems.isEmpty()) {
            throw BizException.badRequest("请先选择要结算的商品");
        }
        List<CartItemVO> checked = checkedItems.stream()
                .filter(CartItemVO::isValid)
                .collect(Collectors.toList());
        if (checked.isEmpty()) {
            // 区分「没勾选」与「勾选了但都已失效」，便于前端给出准确引导
            throw BizException.badRequest("勾选的商品已失效，请重新选择");
        }

        // 先扣库存：库存不足会在此抛出并回滚整个事务
        for (CartItemVO item : checked) {
            inventoryService.deduct(item.getProductId(), item.getSkuId(), item.getQuantity());
        }

        BigDecimal total = checked.stream()
                .map(CartItemVO::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setChannel(CHANNEL_NORMAL);
        order.setTotalAmount(total);
        order.setPayAmount(total);
        order.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        // 收货信息写快照，用户之后改地址不影响本单
        order.setReceiver(address.getReceiver());
        order.setReceiverPhone(address.getPhone());
        order.setReceiverAddr(address.fullAddress());
        order.setRemark(req.getRemark());
        orderMapper.insert(order);

        for (CartItemVO item : checked) {
            orderItemMapper.insert(buildOrderItem(order, item));
        }

        cartService.deleteChecked(userId);

        // 投递超时延迟消息：消息在 MQ 里滞留 TTL 后转入死信队列，触发自动关单。
        // 投递失败只记日志（见 OrderMessageProducer），不影响本次下单结果。
        orderMessageProducer.sendOrderTimeout(order.getOrderNo());

        return toOrderVO(order);
    }

    /** 当前用户的订单分页列表，可按状态筛选。 */
    public PageResult<OrderListItemVO> page(Long userId, OrderQuery query) {
        Page<Order> page = new Page<>(query.getPage(), query.getSize());
        orderMapper.selectPage(page, new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(query.getStatus() != null, Order::getStatus, query.getStatus())
                .orderByDesc(Order::getId));

        List<OrderListItemVO> records = new ArrayList<>(page.getRecords().size());
        for (Order order : page.getRecords()) {
            records.add(toListItemVO(order));
        }
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    /** 订单详情（含收货快照与明细）。 */
    public OrderDetailVO detail(Long userId, String orderNo) {
        Order order = getOwned(userId, orderNo);
        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusLabel(OrderStatus.of(order.getStatus()).getLabel());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setReceiver(order.getReceiver());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddr(order.getReceiverAddr());
        vo.setRemark(order.getRemark());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setPayTime(order.getPayTime());
        vo.setShipTime(order.getShipTime());
        vo.setFinishTime(order.getFinishTime());
        vo.setCancelTime(order.getCancelTime());
        vo.setItems(loadItems(orderNo));
        return vo;
    }

    /** 取消订单：仅待付款可取消，取消时回补库存。 */
    @Transactional
    public void cancel(Long userId, String orderNo) {
        Order order = getOwned(userId, orderNo);
        OrderStatus.of(order.getStatus()).assertTransitionTo(OrderStatus.CANCELLED, "取消");

        restoreStock(orderNo);

        order.setStatus(OrderStatus.CANCELLED.getCode());
        order.setCancelTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    /** 确认收货：仅待收货可确认。 */
    @Transactional
    public void confirm(Long userId, String orderNo) {
        Order order = getOwned(userId, orderNo);
        OrderStatus.of(order.getStatus()).assertTransitionTo(OrderStatus.COMPLETED, "确认收货");

        order.setStatus(OrderStatus.COMPLETED.getCode());
        order.setFinishTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    /**
     * 支付成功后推进订单：待付款 → 待发货，并记录支付时间。
     *
     * <p>由支付回调触发，因此调用方没有用户上下文，这里只按订单号定位。
     * 落库用「带状态条件的 UPDATE」，与超时关单并发时只有一个能成功，
     * 避免已关闭的订单被重新推成待发货。</p>
     *
     * @throws BizException 订单不存在、订单当前状态不允许支付、并发下状态已被他人变更
     */
    @Transactional
    public void markPaid(String orderNo) {
        Order order = loadByOrderNo(orderNo);
        OrderStatus status = OrderStatus.of(order.getStatus());
        if (!status.canTransitionTo(OrderStatus.PENDING_SHIPMENT)) {
            throw BizException.conflict("订单当前为「" + status.getLabel() + "」，无法支付");
        }

        int affected = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT.getCode())
                .set(Order::getStatus, OrderStatus.PENDING_SHIPMENT.getCode())
                .set(Order::getPayTime, LocalDateTime.now()));
        if (affected == 0) {
            throw BizException.conflict("订单状态已变更，请刷新后重试");
        }
    }

    /**
     * 超时关单：待付款订单超过支付时限后关闭并回补库存。
     *
     * <p>由 MQ 死信消费者调用，必须幂等：订单不存在、或已不是待付款（已支付/已取消/已关闭）
     * 都直接跳过，不抛异常。落库同样使用「带状态条件的 UPDATE」抢占，
     * 与支付回调并发时只有一个能改成功，因此不会出现「已付款订单被关单并回补库存」。</p>
     *
     * @param orderNo 订单号
     */
    @Transactional
    public void closeTimeout(String orderNo) {
        if (orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)) == null) {
            log.info("超时关单：订单 {} 不存在，跳过", orderNo);
            return;
        }

        int affected = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT.getCode())
                .set(Order::getStatus, OrderStatus.TIMEOUT_CLOSED.getCode())
                .set(Order::getCancelTime, LocalDateTime.now()));
        if (affected == 0) {
            log.info("超时关单：订单 {} 当前已非待付款，跳过", orderNo);
            return;
        }

        // 只有成功抢到状态流转才回补库存，保证不会重复回补
        restoreStock(orderNo);
        log.info("超时关单成功：{}", orderNo);
    }

    /** 按订单号取订单并校验归属；他人的订单按不存在处理。 */
    public Order getOwned(Long userId, String orderNo) {
        Order order = loadByOrderNo(orderNo);
        if (!userId.equals(order.getUserId())) {
            throw BizException.notFound("订单不存在");
        }
        return order;
    }

    /** 按订单号取订单；不存在则抛 404。 */
    private Order loadByOrderNo(String orderNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw BizException.notFound("订单不存在");
        }
        return order;
    }

    /** 按明细逐条回补库存。 */
    private void restoreStock(String orderNo) {
        for (OrderItem item : loadRawItems(orderNo)) {
            inventoryService.restore(item.getProductId(), item.getSkuId(), item.getQuantity());
        }
    }

    private OrderItem buildOrderItem(Order order, CartItemVO cartItem) {
        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setOrderNo(order.getOrderNo());
        item.setProductId(cartItem.getProductId());
        item.setSkuId(cartItem.getSkuId() == null ? NO_SKU : cartItem.getSkuId());
        item.setProductName(cartItem.getProductName());
        item.setProductImage(cartItem.getProductImage());
        item.setSpecValue(cartItem.getSpecValue());
        item.setPrice(cartItem.getPrice());
        item.setQuantity(cartItem.getQuantity());
        item.setSubtotal(cartItem.getSubtotal());
        return item;
    }

    private List<OrderItem> loadRawItems(String orderNo) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderNo, orderNo)
                .orderByAsc(OrderItem::getId));
    }

    private List<OrderItemVO> loadItems(String orderNo) {
        return loadRawItems(orderNo).stream().map(this::toItemVO).collect(Collectors.toList());
    }

    private OrderItemVO toItemVO(OrderItem item) {
        OrderItemVO vo = new OrderItemVO();
        vo.setProductId(item.getProductId());
        vo.setSkuId(item.getSkuId());
        vo.setProductName(item.getProductName());
        vo.setProductImage(item.getProductImage());
        vo.setSpecValue(item.getSpecValue());
        vo.setPrice(item.getPrice());
        vo.setQuantity(item.getQuantity());
        vo.setSubtotal(item.getSubtotal());
        return vo;
    }

    private OrderVO toOrderVO(Order order) {
        OrderVO vo = new OrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setCreatedAt(order.getCreatedAt());
        return vo;
    }

    private OrderListItemVO toListItemVO(Order order) {
        OrderListItemVO vo = new OrderListItemVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusLabel(OrderStatus.of(order.getStatus()).getLabel());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setCreatedAt(order.getCreatedAt());

        List<OrderItemVO> items = loadItems(order.getOrderNo());
        vo.setItems(items);
        vo.setTotalQuantity(items.stream().mapToInt(OrderItemVO::getQuantity).sum());
        return vo;
    }

    /**
     * 生成订单号：{@code NO} + 17 位时间戳 + 4 位随机数。
     *
     * <p>时间戳精确到毫秒，叠加随机数后重复概率极低；数据库仍有 {@code uk_order_no} 兜底。</p>
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(ORDER_NO_TIME);
        String random = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        return ORDER_NO_PREFIX + timestamp + random;
    }
}
