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
import com.tickcart.trade.enums.OrderStatus;
import com.tickcart.trade.mapper.OrderItemMapper;
import com.tickcart.trade.mapper.OrderMapper;
import com.tickcart.trade.vo.AdminOrderListItemVO;
import com.tickcart.trade.vo.OrderDetailVO;
import com.tickcart.trade.vo.OrderItemVO;
import com.tickcart.trade.vo.OrderListItemVO;
import com.tickcart.trade.vo.OrderVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductService productService;

    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        AddressService addressService,
                        CartService cartService,
                        InventoryService inventoryService,
                        OrderMessageProducer orderMessageProducer,
                        ProductMapper productMapper,
                        ProductSkuMapper productSkuMapper,
                        ProductService productService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.addressService = addressService;
        this.cartService = cartService;
        this.inventoryService = inventoryService;
        this.orderMessageProducer = orderMessageProducer;
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.productService = productService;
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

        List<OrderItem> drafts = checked.stream().map(this::toOrderItemDraft).collect(Collectors.toList());
        Order order = saveOrder(userId, address, req.getRemark(), total, drafts);

        cartService.deleteChecked(userId);

        // 投递超时延迟消息：消息在 MQ 里滞留 TTL 后转入死信队列，触发自动关单。
        // 投递失败只记日志（见 OrderMessageProducer），不影响本次下单结果。
        orderMessageProducer.sendOrderTimeout(order.getOrderNo());

        return toOrderVO(order);
    }

    /**
     * 立即购买下单：按客户端指定的商品与规格直接生成订单，**不经过购物车**。
     *
     * <p>与 {@link #create} 的区别只在「明细从哪来」：这里不读也不清购物车，
     * 商品、规格、数量都取自请求参数；落库与超时关单完全复用同一条链路。</p>
     *
     * @throws BizException 地址不属于当前用户、商品下架、规格不存在或不属于该商品、库存不足
     */
    @Transactional
    public OrderVO createBuyNow(Long userId, OrderBuyNowRequest req) {
        UserAddress address = addressService.getOwned(userId, req.getAddressId());

        OrderItem draft = buildBuyNowItem(req);

        // 与结算下单一致：先扣库存，库存不足直接抛异常回滚，不会留下脏订单
        inventoryService.deduct(draft.getProductId(), draft.getSkuId(), draft.getQuantity());

        Order order = saveOrder(userId, address, req.getRemark(), draft.getSubtotal(), List.of(draft));

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

    /**
     * 后台订单分页列表：不限用户，可按状态与关键字筛选。
     *
     * <p>关键字对「订单号 / 收货人 / 收货人手机号」三者做 OR 模糊匹配，
     * 便于客服按任一信息定位订单。</p>
     */
    public PageResult<AdminOrderListItemVO> adminPage(AdminOrderQuery query) {
        String keyword = query.getKeyword() == null ? null : query.getKeyword().trim();
        boolean hasKeyword = StringUtils.hasText(keyword);

        Page<Order> page = new Page<>(query.getPage(), query.getSize());
        orderMapper.selectPage(page, new LambdaQueryWrapper<Order>()
                .eq(query.getStatus() != null, Order::getStatus, query.getStatus())
                .and(hasKeyword, w -> w
                        .like(Order::getOrderNo, keyword)
                        .or().like(Order::getReceiver, keyword)
                        .or().like(Order::getReceiverPhone, keyword))
                .orderByDesc(Order::getId));

        List<AdminOrderListItemVO> records = new ArrayList<>(page.getRecords().size());
        for (Order order : page.getRecords()) {
            records.add(toAdminListItemVO(order));
        }
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    /** 订单详情（含收货快照与明细）。 */
    public OrderDetailVO detail(Long userId, String orderNo) {
        return buildDetail(getOwned(userId, orderNo));
    }

    /**
     * 后台订单详情：不做归属校验，管理员可查看任意用户的订单。
     */
    public OrderDetailVO adminDetail(String orderNo) {
        return buildDetail(loadByOrderNo(orderNo));
    }

    /** 组装订单详情 VO（前台与后台共用，避免两处字段拼装重复）。 */
    private OrderDetailVO buildDetail(Order order) {
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
        vo.setItems(loadItems(order.getOrderNo()));
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
     * 发货：待发货 → 待收货，并记录发货时间。
     *
     * <p>由后台管理员触发。落库用「带状态条件的 UPDATE」，与其它状态流转并发时
     * 只有一个能成功，避免重复发货或把已取消订单推成待收货。</p>
     *
     * @throws BizException 订单不存在、当前状态不允许发货、并发下状态已被他人变更
     */
    @Transactional
    public void ship(String orderNo) {
        Order order = loadByOrderNo(orderNo);
        OrderStatus status = OrderStatus.of(order.getStatus());
        status.assertTransitionTo(OrderStatus.PENDING_RECEIPT, "发货");

        int affected = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, OrderStatus.PENDING_SHIPMENT.getCode())
                .set(Order::getStatus, OrderStatus.PENDING_RECEIPT.getCode())
                .set(Order::getShipTime, LocalDateTime.now()));
        if (affected == 0) {
            throw BizException.conflict("订单状态已变更，请刷新后重试");
        }
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

        // 抢到状态流转之后才累加销量：回调重复到达时上面的 UPDATE 影响 0 行、
        // 走不到这里，所以不需要额外的幂等标记。
        accumulateSales(orderNo);
    }

    /**
     * 累加商品销量：按订单明细的购买数量聚合，同一商品的多行合并成一次 UPDATE。
     *
     * <p>用 {@code sales = sales + n} 的原子写法而非「先查后写」，
     * 避免多笔订单同时支付时互相覆盖。</p>
     *
     * <p>只在 {@link #markPaid(String)} 抢到状态流转后调用，因此：
     * 支付回调重复到达不会重复累加；超时关单（订单从未支付）也不会误加。</p>
     */
    private void accumulateSales(String orderNo) {
        Map<Long, Integer> quantityByProduct = new LinkedHashMap<>();
        for (OrderItem item : loadRawItems(orderNo)) {
            if (item.getProductId() == null || item.getQuantity() == null) {
                continue;
            }
            quantityByProduct.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        quantityByProduct.forEach((productId, quantity) -> productMapper.update(null,
                new LambdaUpdateWrapper<Product>()
                        .eq(Product::getId, productId)
                        .setSql("sales = sales + {0}", quantity)));
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

    /**
     * 落库订单主表与明细，并返回已带上 ID / 订单号的订单。
     *
     * <p>结算下单与立即购买共用：两者只有「明细从哪来」不同，
     * 收货快照、金额、初始状态与明细关联的写法必须完全一致。</p>
     *
     * @param drafts 未关联订单的明细草稿，由本方法回填 {@code orderId} 与 {@code orderNo}
     */
    private Order saveOrder(Long userId, UserAddress address, String remark,
                            BigDecimal total, List<OrderItem> drafts) {
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
        order.setRemark(remark);
        orderMapper.insert(order);

        for (OrderItem draft : drafts) {
            draft.setOrderId(order.getId());
            draft.setOrderNo(order.getOrderNo());
            orderItemMapper.insert(draft);
        }
        return order;
    }

    /** 购物车项 → 订单明细草稿（订单 ID / 订单号由 {@link #saveOrder} 回填）。 */
    private OrderItem toOrderItemDraft(CartItemVO cartItem) {
        OrderItem item = new OrderItem();
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

    /**
     * 立即购买：校验商品/规格并组装订单明细草稿（不落库、不扣库存）。
     *
     * <p>成交价与主图的口径必须与 {@code CartService#buildItemVo} 保持一致 ——
     * 有规格就取规格自身的价格与图片，规格价为空时才回退商品价。
     * 两处口径一旦分叉，会出现「结算页显示 A 价、立即购买扣 B 价」。</p>
     *
     * @throws BizException 商品不存在或已下架、规格不存在或不属于该商品、未选规格、数量非法、库存不足
     */
    private OrderItem buildBuyNowItem(OrderBuyNowRequest req) {
        Product product = productMapper.selectById(req.getProductId());
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            throw BizException.badRequest("商品不存在或已下架");
        }

        long skuId = req.getSkuId() == null ? NO_SKU : req.getSkuId();
        ProductSku sku = null;
        if (skuId > NO_SKU) {
            sku = productSkuMapper.selectById(skuId);
            if (sku == null || !product.getId().equals(sku.getProductId())) {
                throw BizException.badRequest("商品规格不存在");
            }
        } else if (productService.hasRealSpec(product.getId())) {
            // 配置了规格却不传 skuId 会退化成按商品级库存扣减，与 SKU 库存各算一套账
            throw BizException.badRequest("请选择商品规格");
        }

        int quantity = req.getQuantity() == null ? 1 : req.getQuantity();
        if (quantity <= 0) {
            throw BizException.badRequest("购买数量必须大于 0");
        }

        int available = sku != null ? nullToZero(sku.getStock()) : nullToZero(product.getStock());
        if (available < quantity) {
            throw BizException.badRequest("库存不足");
        }

        BigDecimal price = product.getPrice();
        String image = product.getMainImage();
        String specValue = null;
        if (sku != null) {
            price = sku.getPrice();
            specValue = sku.getSpecValue();
            if (StringUtils.hasText(sku.getSkuImage())) {
                image = sku.getSkuImage();
            }
        }

        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setSkuId(skuId);
        item.setProductName(product.getName());
        item.setProductImage(image);
        item.setSpecValue(specValue);
        item.setPrice(price);
        item.setQuantity(quantity);
        item.setSubtotal(price.multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
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

    private AdminOrderListItemVO toAdminListItemVO(Order order) {
        AdminOrderListItemVO vo = new AdminOrderListItemVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setReceiver(order.getReceiver());
        vo.setReceiverPhone(order.getReceiverPhone());
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
