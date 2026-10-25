package com.tickcart.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tickcart.cart.service.CartService;
import com.tickcart.cart.vo.CartItemVO;
import com.tickcart.cart.vo.CartSummary;
import com.tickcart.common.api.PageResult;
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
import com.tickcart.trade.enums.PaymentStatus;
import com.tickcart.trade.mapper.OrderItemMapper;
import com.tickcart.trade.mapper.OrderMapper;
import com.tickcart.trade.mapper.PaymentRecordMapper;
import com.tickcart.trade.vo.AdminOrderDetailVO;
import com.tickcart.trade.vo.AdminOrderListItemVO;
import com.tickcart.trade.vo.BatchShipResultVO;
import com.tickcart.trade.vo.OrderCountVO;
import com.tickcart.trade.vo.OrderDetailVO;
import com.tickcart.trade.vo.OrderItemVO;
import com.tickcart.trade.vo.OrderListItemVO;
import com.tickcart.trade.vo.OrderOverviewVO;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    /** 秒杀订单渠道标识 */
    private static final int CHANNEL_SECKILL = 1;

    /** 秒杀订单号前缀（与普通 NO 区分，便于人工识别与对账） */
    private static final String ORDER_NO_PREFIX_SECKILL = "SK";

    /** 无规格商品的 SKU 占位值 */
    private static final long NO_SKU = 0L;

    /** 批量发货单次上限（与商品批量接口保持同一量级） */
    private static final int BATCH_MAX_SIZE = 100;

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
    private final PaymentRecordMapper paymentRecordMapper;
    private final IdempotentService idempotentService;
    /** 秒杀库存记账扩展点（由 seckill 模块实现，见该接口注释） */
    private final SeckillStockAccounting seckillStockAccounting;

    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        AddressService addressService,
                        CartService cartService,
                        InventoryService inventoryService,
                        OrderMessageProducer orderMessageProducer,
                        ProductMapper productMapper,
                        ProductSkuMapper productSkuMapper,
                        ProductService productService,
                        PaymentRecordMapper paymentRecordMapper,
                        IdempotentService idempotentService,
                        SeckillStockAccounting seckillStockAccounting) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.addressService = addressService;
        this.cartService = cartService;
        this.inventoryService = inventoryService;
        this.orderMessageProducer = orderMessageProducer;
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.productService = productService;
        this.paymentRecordMapper = paymentRecordMapper;
        this.idempotentService = idempotentService;
        this.seckillStockAccounting = seckillStockAccounting;
    }

    /**
     * 结算下单：把购物车中「已勾选且有效」的商品生成订单。
     *
     * <p>整体在一个事务内完成：幂等占位 → 校验地址 → 结算抢占 → 扣库存 → 写订单 → 写明细
     * → 回填幂等单号。任一步失败整体回滚，不会留下扣了库存却没有订单的脏数据。</p>
     *
     * <p><b>幂等</b>：同一 {@code idemKey} 重复提交只会成一次单，后续请求回放首次的订单号。
     * 未携带幂等键时退化为无保护（兼容旧调用方），并打 WARN 日志。</p>
     *
     * @param idemKey 客户端生成的幂等键，可为 null
     * @throws BizException 地址不属于当前用户、无勾选商品、库存不足、购物车已被并发结算
     */
    @Transactional
    public OrderVO create(Long userId, OrderCreateRequest req, String idemKey) {
        // 幂等占位放在最前面：重复请求在这里就被挡掉，不必先查地址、读购物车
        String replayedOrderNo = idempotentService.claim(
                userId, IdempotentService.SCENE_ORDER_CREATE, idemKey);
        if (replayedOrderNo != null) {
            return replay(userId, replayedOrderNo);
        }

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

        claimCartItems(userId, checked, checkedItems);

        // 先扣库存：库存不足会在此抛出并回滚整个事务（含上面的抢占，购物车项会恢复）
        for (CartItemVO item : checked) {
            inventoryService.deduct(item.getProductId(), item.getSkuId(), item.getQuantity());
        }

        BigDecimal total = checked.stream()
                .map(CartItemVO::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<OrderItem> drafts = checked.stream().map(this::toOrderItemDraft).collect(Collectors.toList());
        Order order = saveOrder(userId, address, req.getRemark(), total, drafts);

        idempotentService.complete(userId, IdempotentService.SCENE_ORDER_CREATE, idemKey, order.getOrderNo());

        // 投递超时延迟消息：消息在 MQ 里滞留 TTL 后转入死信队列，触发自动关单。
        // 投递失败只记日志（见 OrderMessageProducer），不影响本次下单结果。
        orderMessageProducer.sendOrderTimeout(order.getOrderNo());

        return toOrderVO(order);
    }

    /**
     * 结算抢占：把本次要结算的购物车项标记删除，并清理本次读到的失效勾选项。
     *
     * <p>抢占是「同一批购物车只能被结算一次」的唯一闸门：并发下只有一个请求能影响全部有效项，
     * 其余影响 0 行 → 抛 409。这替代了原先的 {@code cartService.deleteChecked(userId)}——
     * 后者按「当前 checked = 1」删，会误删并发期间用户新勾选的其他商品。</p>
     *
     * @param checked      本次要结算的有效项
     * @param checkedItems 本次读到的全部勾选项（含失效），失效项下单后一并清理以保持原行为
     */
    private void claimCartItems(Long userId, List<CartItemVO> checked, List<CartItemVO> checkedItems) {
        List<Long> validIds = checked.stream().map(CartItemVO::getId).toList();
        int claimed = cartService.claimForCheckout(userId, validIds);
        if (claimed != validIds.size()) {
            throw BizException.conflict("购物车已被其他请求结算，请刷新后重试");
        }

        List<Long> invalidIds = checkedItems.stream()
                .filter(item -> !item.isValid())
                .map(CartItemVO::getId)
                .toList();
        cartService.removeItems(userId, invalidIds);
    }

    /**
     * 立即购买下单：按客户端指定的商品与规格直接生成订单，**不经过购物车**。
     *
     * <p>与 {@link #create} 的区别只在「明细从哪来」：这里不读也不清购物车，
     * 商品、规格、数量都取自请求参数；落库、幂等与超时关单完全复用同一条链路。</p>
     *
     * @throws BizException 地址不属于当前用户、商品下架、规格不存在或不属于该商品、库存不足
     */
    @Transactional
    public OrderVO createBuyNow(Long userId, OrderBuyNowRequest req, String idemKey) {
        // 与结算下单同理：占位放在最前面，重复请求尽早被挡掉
        String replayedOrderNo = idempotentService.claim(
                userId, IdempotentService.SCENE_ORDER_BUY_NOW, idemKey);
        if (replayedOrderNo != null) {
            return replay(userId, replayedOrderNo);
        }

        UserAddress address = addressService.getOwned(userId, req.getAddressId());

        OrderItem draft = buildBuyNowItem(req);

        // 与结算下单一致：先扣库存，库存不足直接抛异常回滚，不会留下脏订单
        inventoryService.deduct(draft.getProductId(), draft.getSkuId(), draft.getQuantity());

        Order order = saveOrder(userId, address, req.getRemark(), draft.getSubtotal(), List.of(draft));

        idempotentService.complete(userId, IdempotentService.SCENE_ORDER_BUY_NOW, idemKey, order.getOrderNo());

        orderMessageProducer.sendOrderTimeout(order.getOrderNo());

        return toOrderVO(order);
    }

    /**
     * 秒杀建单：由 MQ 消费者调用，不读购物车、**不扣 SKU 库存**。
     *
     * <p>秒杀库存在抢购时已由 Redis Lua 扣减（B2），这里再扣一次就是双扣。
     * 建单只负责「把抢到的名额变成订单」，其余（超时关单、支付、发货）与普通订单完全一致。</p>
     *
     * <p>幂等键用抢购时生成的 {@code requestId}：消息重复投递会命中同一键并回放首次订单号。</p>
     *
     * @param addressId    抢购时用户选定的收货地址（必须属于该用户）
     * @param seckillPrice 活动价快照（来自消息，不回查活动）
     * @throws BizException 地址不属于该用户、商品已不存在、幂等键冲突
     */
    @Transactional
    public OrderVO createSeckillOrder(Long userId, Long activityId, Long productId, Long skuId,
                                      Long addressId, int quantity, BigDecimal seckillPrice,
                                      String requestId) {
        String replayedOrderNo = idempotentService.claim(
                userId, IdempotentService.SCENE_SECKILL_BUY, requestId);
        if (replayedOrderNo != null) {
            log.info("秒杀建单幂等命中：requestId={}, orderNo={}", requestId, replayedOrderNo);
            return replay(userId, replayedOrderNo);
        }

        // 地址由用户在抢购时选定；这里再校验一次归属 —— 抢到后把地址删了也会落到这里，
        // 属于业务拒绝（重试无意义），消费者会回补库存
        UserAddress address = addressService.getOwned(userId, addressId);

        OrderItem draft = buildSeckillItem(productId, skuId, quantity, seckillPrice);
        Order order = saveOrder(userId, address, null, draft.getSubtotal(), List.of(draft),
                CHANNEL_SECKILL, activityId, generateOrderNo(ORDER_NO_PREFIX_SECKILL));

        // 已售数只在「真正新建」这条路径上累加：幂等回放直接 return，不会重复计数
        seckillStockAccounting.increaseSold(activityId, quantity);

        idempotentService.complete(userId, IdempotentService.SCENE_SECKILL_BUY, requestId, order.getOrderNo());
        orderMessageProducer.sendOrderTimeout(order.getOrderNo());
        return toOrderVO(order);
    }

    /**
     * 组装秒杀订单明细。
     *
     * <p>商品必须存在（要取名称与主图做快照），但**不校验上下架状态** ——
     * 活动创建时已校验过，用户也已经抢到了，活动期间被下架不该让用户白抢。
     * 规格被删时同样不阻断：价格与数量来自消息，图片回退商品主图。</p>
     */
    private OrderItem buildSeckillItem(Long productId, Long skuId, int quantity, BigDecimal seckillPrice) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw BizException.badRequest("商品不存在");
        }
        if (seckillPrice == null) {
            throw BizException.badRequest("秒杀价缺失");
        }
        long normalizedSkuId = skuId == null ? NO_SKU : skuId;
        ProductSku sku = normalizedSkuId > NO_SKU ? productSkuMapper.selectById(normalizedSkuId) : null;

        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setSkuId(normalizedSkuId);
        item.setProductName(product.getName());
        item.setProductImage(sku != null && StringUtils.hasText(sku.getSkuImage())
                ? sku.getSkuImage() : product.getMainImage());
        item.setSpecValue(sku == null ? null : sku.getSpecValue());
        item.setPrice(seckillPrice);
        item.setQuantity(quantity);
        item.setSubtotal(seckillPrice.multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    /**
     * 幂等命中：按首次成功的订单号回放结果。
     *
     * <p>幂等键本身按 {@code (scene, user_id)} 隔离，能命中就说明是本人重试；
     * 这里再带一次 {@code user_id} 条件，是防止幂等键被清理后撞到他人订单的兜底。</p>
     *
     * @throws BizException 订单已不存在（理论上不可达，出现即需人工排查）
     */
    private OrderVO replay(Long userId, String orderNo) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId));
        if (order == null) {
            throw BizException.conflict("请求已完成，但对应订单不存在，请到订单中心查看");
        }
        return toOrderVO(order);
    }

    /** 当前用户的订单分页列表，可按状态筛选。 */
    public PageResult<OrderListItemVO> page(Long userId, OrderQuery query) {
        Page<Order> page = new Page<>(query.getPage(), query.getSize());
        orderMapper.selectPage(page, new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(query.getStatus() != null, Order::getStatus, query.getStatus())
                .orderByDesc(Order::getId));

        // 一次 IN 查回本页所有明细再分组，避免「每单一条 SQL」的 N+1
        Map<String, List<OrderItemVO>> itemsByOrder = loadItemsGrouped(page.getRecords());
        List<OrderListItemVO> records = new ArrayList<>(page.getRecords().size());
        for (Order order : page.getRecords()) {
            records.add(toListItemVO(order, itemsByOrder.get(order.getOrderNo())));
        }
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    /**
     * 统计当前用户各状态订单数量，供「我的」页订单宫格展示角标。
     *
     * <p>一次 GROUP BY 拿全，避免为四个角标各发一次分页查询。数量为 0 的状态
     * 不会出现在结果集里，VO 上保持默认 0。</p>
     *
     * <p>只认宫格上的四个状态（待付款 / 待发货 / 待收货 / 已完成）；
     * 已取消、已超时关闭是终态、不需要提醒，直接忽略。</p>
     */
    public OrderCountVO countByStatus(Long userId) {
        List<Map<String, Object>> rows = orderMapper.selectMaps(new QueryWrapper<Order>()
                .select("status", "COUNT(*) AS cnt")
                .eq("user_id", userId)
                .groupBy("status"));

        OrderCountVO vo = new OrderCountVO();
        for (Map<String, Object> row : rows) {
            int status = (int) numberValue(row, "status");
            long count = numberValue(row, "cnt");
            if (status == OrderStatus.PENDING_PAYMENT.getCode()) {
                vo.setPendingPayment(count);
            } else if (status == OrderStatus.PENDING_SHIPMENT.getCode()) {
                vo.setPendingShipment(count);
            } else if (status == OrderStatus.PENDING_RECEIPT.getCode()) {
                vo.setPendingReceipt(count);
            } else if (status == OrderStatus.COMPLETED.getCode()) {
                vo.setCompleted(count);
            }
        }
        return vo;
    }

    /**
     * 从 {@code selectMaps} 的一行里取数值列。
     *
     * <p>列名按大小写无关匹配：结果集的 key 由驱动按别名原样给出，
     * 不同驱动/配置下大小写可能不一致，写死小写会静默取到 0。</p>
     */
    private static long numberValue(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value == null) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(column)) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /** 从 {@code selectMaps} 的一行里取金额列（SUM 结果为 BigDecimal，空值为 null）。 */
    private static BigDecimal decimalValue(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value == null) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(column)) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value instanceof Number number
                ? BigDecimal.valueOf(number.doubleValue())
                : BigDecimal.ZERO;
    }

    /**
     * 从 {@code selectMaps} 的一行里取时间列（MIN(pay_time)）。
     *
     * <p>MySQL 驱动对 DATETIME 列默认返回 {@link LocalDateTime}，但聚合函数的结果
     * 在部分配置下会退化成 {@link java.sql.Timestamp}，这里两种都接。</p>
     */
    private static LocalDateTime dateTimeValue(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value == null) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(column)) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        return value instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime() : null;
    }

    /**
     * 后台订单分页列表：不限用户，可按状态、关键字、下单日期筛选并指定排序。
     *
     * <p>关键字对「订单号 / 收货人 / 收货人手机号 / 商品名」四者做 OR 模糊匹配，
     * 便于客服按任一信息定位订单。</p>
     *
     * <p><b>★ 排序为什么重要</b>：待发货订单里最早的可能已经等了十几天，
     * 默认按 id 倒序会让「最该处理的那单」沉在最后一页。传
     * {@code sortBy=payTime&order=asc} 才能做到「等最久优先」。</p>
     */
    public PageResult<AdminOrderListItemVO> adminPage(AdminOrderQuery query) {
        String keyword = query.getKeyword() == null ? null : query.getKeyword().trim();
        boolean hasKeyword = StringUtils.hasText(keyword);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(query.getStatus() != null, Order::getStatus, query.getStatus());
        // 日期范围按「下单时间」闭区间：起日 00:00 起，止日次日 00:00 前
        if (query.getBeginDate() != null) {
            wrapper.ge(Order::getCreatedAt, query.getBeginDate().atStartOfDay());
        }
        if (query.getEndDate() != null) {
            wrapper.lt(Order::getCreatedAt, query.getEndDate().plusDays(1).atStartOfDay());
        }
        wrapper.and(hasKeyword, w -> w
                .like(Order::getOrderNo, keyword)
                .or().like(Order::getReceiver, keyword)
                .or().like(Order::getReceiverPhone, keyword)
                // 按商品名找单：本项目没有 Mapper XML，只能用子查询；{0} 是参数绑定，
                // 不要改成 inSql 拼字符串（那样有注入风险）
                .or().apply("order_no IN (SELECT order_no FROM order_item WHERE product_name LIKE {0})",
                        "%" + keyword + "%"));
        applyAdminSort(wrapper, query.getSortBy(), query.getOrder());

        Page<Order> page = new Page<>(query.getPage(), query.getSize());
        orderMapper.selectPage(page, wrapper);

        Map<String, PaymentRecord> latestPayments = loadLatestPayments(page.getRecords());
        Map<String, List<OrderItemVO>> itemsByOrder = loadItemsGrouped(page.getRecords());
        List<AdminOrderListItemVO> records = new ArrayList<>(page.getRecords().size());
        for (Order order : page.getRecords()) {
            records.add(toAdminListItemVO(order, latestPayments.get(order.getOrderNo()),
                    itemsByOrder.get(order.getOrderNo())));
        }
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    /**
     * 后台列表排序：白名单映射，绝不把前端字符串拼进 ORDER BY。
     *
     * <p>只认 {@code createdAt}（默认）与 {@code payTime}；未知字段回落到下单时间。
     * 末尾补主键兜底：排序键不唯一时（演示数据里有大量同一秒下的单）MySQL 不保证行序稳定，
     * 翻页会重复/漏项。</p>
     *
     * <p>★ {@code payTime} 升序时，未支付订单的 {@code pay_time} 为 NULL，
     * MySQL 会把它们排在最前 —— 对「待发货」筛选无影响（该状态必有付款时间），
     * 但在「全部」下会先看到待付款单，这是可接受的语义（它们同样需要处理）。</p>
     */
    private void applyAdminSort(LambdaQueryWrapper<Order> wrapper, String sortBy, String order) {
        boolean asc = "asc".equalsIgnoreCase(order);
        String field = sortBy == null ? "createdAt" : sortBy;
        switch (field) {
            case "payTime" -> wrapper.orderBy(true, asc, Order::getPayTime);
            default -> wrapper.orderBy(true, asc, Order::getCreatedAt);
        }
        wrapper.orderBy(true, asc, Order::getId);
    }

    /**
     * 后台订单概览：一次 {@code GROUP BY status} 算出全部计数与成交额。
     *
     * <p>★ 不要拆成 6 次 {@code count(*)}：这里每个状态一行，行内顺带带上
     * {@code SUM(pay_amount)} 与 {@code MIN(pay_time)}，一次往返就够。</p>
     */
    public OrderOverviewVO adminOverview() {
        List<Map<String, Object>> rows = orderMapper.selectMaps(new QueryWrapper<Order>()
                .select("status", "COUNT(*) AS cnt", "SUM(pay_amount) AS amt", "MIN(pay_time) AS min_pay")
                .groupBy("status"));

        OrderOverviewVO vo = new OrderOverviewVO();
        BigDecimal paidAmount = BigDecimal.ZERO;
        long paidOrderCount = 0;
        for (Map<String, Object> row : rows) {
            int status = (int) numberValue(row, "status");
            long count = numberValue(row, "cnt");
            vo.setTotal(vo.getTotal() + count);
            if (status == OrderStatus.PENDING_PAYMENT.getCode()) {
                vo.setPendingPayment(count);
            } else if (status == OrderStatus.PENDING_SHIPMENT.getCode()) {
                vo.setPendingShipment(count);
                vo.setOldestPendingShipmentTime(dateTimeValue(row, "min_pay"));
            } else if (status == OrderStatus.PENDING_RECEIPT.getCode()) {
                vo.setPendingReceipt(count);
            } else if (status == OrderStatus.COMPLETED.getCode()) {
                vo.setCompleted(count);
            } else if (status == OrderStatus.CANCELLED.getCode()) {
                vo.setCancelled(count);
            } else if (status == OrderStatus.TIMEOUT_CLOSED.getCode()) {
                vo.setTimeoutClosed(count);
            }
            // 成交口径 = 收到钱的单（待发货 / 待收货 / 已完成），已取消与超时不进
            if (status == OrderStatus.PENDING_SHIPMENT.getCode()
                    || status == OrderStatus.PENDING_RECEIPT.getCode()
                    || status == OrderStatus.COMPLETED.getCode()) {
                paidOrderCount += count;
                paidAmount = paidAmount.add(decimalValue(row, "amt"));
            }
        }
        vo.setPaidOrderCount(paidOrderCount);
        vo.setPaidAmount(paidAmount);
        return vo;
    }

    /**
     * 批量发货：逐单推进「待发货 → 待收货」。
     *
     * <p><b>★ 允许部分成功</b>，与商品批量接口「整批拒绝」刻意不同：发货对应的是
     * 已经发生的物理动作，不能因为某一单状态被他人改过，就把其余几十单一起回滚。
     * 失败项带原因返回，由前端列出来让管理员单独处理。</p>
     *
     * <p>每单仍用「带状态条件的 UPDATE」收口，与支付回调/超时关单并发时只有一方能成功。</p>
     */
    @Transactional
    public BatchShipResultVO batchShip(List<String> orderNos) {
        List<String> targets = normalizeBatchOrderNos(orderNos);

        // 一次 IN 查回全部目标订单：既能区分「不存在」与「状态不对」，也避免逐单回查
        Map<String, Order> byNo = new HashMap<>();
        for (Order order : orderMapper.selectList(
                new LambdaQueryWrapper<Order>().in(Order::getOrderNo, targets))) {
            byNo.put(order.getOrderNo(), order);
        }

        BatchShipResultVO result = new BatchShipResultVO();
        for (String orderNo : targets) {
            Order order = byNo.get(orderNo);
            if (order == null) {
                result.getFailed().add(new BatchShipResultVO.Failure(orderNo, "订单不存在"));
                continue;
            }
            OrderStatus status = OrderStatus.of(order.getStatus());
            if (!status.canTransitionTo(OrderStatus.PENDING_RECEIPT)) {
                result.getFailed().add(new BatchShipResultVO.Failure(
                        orderNo, "订单当前为「" + status.getLabel() + "」，无法发货"));
                continue;
            }
            int affected = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getOrderNo, orderNo)
                    .eq(Order::getStatus, OrderStatus.PENDING_SHIPMENT.getCode())
                    .set(Order::getStatus, OrderStatus.PENDING_RECEIPT.getCode())
                    .set(Order::getShipTime, LocalDateTime.now()));
            if (affected == 0) {
                result.getFailed().add(new BatchShipResultVO.Failure(
                        orderNo, "订单状态已变更，请刷新后重试"));
            } else {
                result.getSuccess().add(orderNo);
            }
        }
        log.info("批量发货：成功 {} 单，失败 {} 单", result.getSuccess().size(), result.getFailed().size());
        return result;
    }

    /** 批量订单号去空、去重并校验上限。 */
    private List<String> normalizeBatchOrderNos(List<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            throw BizException.badRequest("请先选择要发货的订单");
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String orderNo : orderNos) {
            if (StringUtils.hasText(orderNo)) {
                distinct.add(orderNo.trim());
            }
        }
        if (distinct.isEmpty()) {
            throw BizException.badRequest("请先选择要发货的订单");
        }
        if (distinct.size() > BATCH_MAX_SIZE) {
            throw BizException.badRequest("单次最多处理 " + BATCH_MAX_SIZE + " 单");
        }
        return new ArrayList<>(distinct);
    }

    /**
     * 批量取本页订单的「代表性」支付流水（一次 IN 查询，避免逐单查询造成 N+1）。
     *
     * <p><b>★ 优先取支付成功的那条，没有成功记录才取 id 最大的那条。</b></p>
     *
     * <p>为什么不是「只取 id 最大」：同一订单可能有多条流水（支付失败/用户取消后重试都会新落一条），
     * 最新那条完全可能是「待支付」或「已关闭」。只按 id 取，会让**已取消的订单**
     * 也展示出一个流水号，后台读起来像「已付款后又取消了」，是纯粹的误导。</p>
     *
     * <p>优先成功的正确性：订单一旦支付成功就不会再发起新支付，所以成功那条是唯一的；
     * 没有成功记录时（待付款/已取消/已超时）退回最新一条，用于展示「最后尝试的渠道」。</p>
     */
    private Map<String, PaymentRecord> loadLatestPayments(List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        List<String> orderNos = orders.stream().map(Order::getOrderNo).toList();
        List<PaymentRecord> payments = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>().in(PaymentRecord::getOrderNo, orderNos));

        Map<String, PaymentRecord> picked = new LinkedHashMap<>();
        for (PaymentRecord payment : payments) {
            PaymentRecord exist = picked.get(payment.getOrderNo());
            if (exist == null || preferPayment(payment, exist)) {
                picked.put(payment.getOrderNo(), payment);
            }
        }
        return picked;
    }

    /** 候选流水是否比现有那条更值得展示：先看是否支付成功，再比 id。 */
    private static boolean preferPayment(PaymentRecord candidate, PaymentRecord current) {
        boolean candidateSuccess = Integer.valueOf(PaymentRecord.STATUS_SUCCESS).equals(candidate.getStatus());
        boolean currentSuccess = Integer.valueOf(PaymentRecord.STATUS_SUCCESS).equals(current.getStatus());
        if (candidateSuccess != currentSuccess) {
            return candidateSuccess;
        }
        return candidate.getId() != null && current.getId() != null && candidate.getId() > current.getId();
    }

    /** 订单详情（含收货快照与明细）。 */
    public OrderDetailVO detail(Long userId, String orderNo) {
        return buildDetail(getOwned(userId, orderNo), new OrderDetailVO());
    }

    /**
     * 后台订单详情：不做归属校验，管理员可查看任意用户的订单。
     *
     * <p>比前台详情多一组「支付语义」字段（状态 / 渠道 / 流水号）。
     * ★ 判断「有没有收到钱」只看 {@code payStatus}，不要看流水号是否为空 ——
     * 已取消的订单也可能挂着一条「待支付」流水。</p>
     */
    public AdminOrderDetailVO adminDetail(String orderNo) {
        Order order = loadByOrderNo(orderNo);
        AdminOrderDetailVO vo = buildDetail(order, new AdminOrderDetailVO());
        vo.setUserId(order.getUserId());

        PaymentRecord payment = loadLatestPayments(List.of(order)).get(orderNo);
        if (payment != null) {
            vo.setPayStatus(payment.getStatus());
            vo.setPayStatusLabel(PaymentStatus.of(payment.getStatus()).getLabel());
            vo.setPayChannel(payment.getChannel());
            vo.setPayNo(payment.getPayNo());
        }
        return vo;
    }

    /**
     * 组装订单详情 VO（前台与后台共用，避免两处字段拼装重复）。
     *
     * <p>泛型是为了让后台在共用字段之上再挂自己的支付字段，而不是复制一遍 13 个 set。</p>
     */
    private <T extends OrderDetailVO> T buildDetail(Order order, T vo) {
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

    /**
     * 取消订单：仅待付款可取消，取消时回补库存。
     *
     * <p>用户取消与后台关闭走同一个私有实现 {@link #doCancel(Order)}：
     * 库存回补（含秒杀按渠道分流）的口径必须只有一份。</p>
     */
    @Transactional
    public void cancel(Long userId, String orderNo) {
        doCancel(getOwned(userId, orderNo));
    }

    /**
     * 后台关闭订单：仅待付款可关闭，复用用户取消的库存回补逻辑。
     *
     * <p>用途是「用户打电话要求取消」这类场景 —— 在此之前后台只能干等 15 分钟超时。
     * 与超时关单并发时靠状态机校验 + 单条 UPDATE 收口，不会重复回补库存。</p>
     */
    @Transactional
    public void adminClose(String orderNo) {
        doCancel(loadByOrderNo(orderNo));
    }

    /** 取消/关闭的共同实现：校验状态 → 回补库存 → 落库。 */
    private void doCancel(Order order) {
        OrderStatus.of(order.getStatus()).assertTransitionTo(OrderStatus.CANCELLED, "取消");

        restoreStock(order);

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
        restoreStock(orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)));
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

    /**
     * 按明细回补库存。
     *
     * <p><b>秒杀订单与普通订单的回补目标不同</b>：普通订单回补 {@code product_sku.stock}；
     * 秒杀订单的库存在 Redis 计数与活动已售数里（B2 的 Lua 扣的），回补到 SKU 上等于
     * 凭空多出普通库存。所以必须按渠道分流。</p>
     */
    private void restoreStock(Order order) {
        if (order == null) {
            return;
        }
        List<OrderItem> items = loadRawItems(order.getOrderNo());
        if (Integer.valueOf(CHANNEL_SECKILL).equals(order.getChannel())
                && order.getActivityId() != null && order.getActivityId() > NO_SKU) {
            int totalQuantity = items.stream().mapToInt(OrderItem::getQuantity).sum();
            seckillStockAccounting.restore(order.getActivityId(), order.getUserId(), totalQuantity);
            return;
        }
        for (OrderItem item : items) {
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
        return saveOrder(userId, address, remark, total, drafts,
                CHANNEL_NORMAL, NO_SKU, generateOrderNo());
    }

    /**
     * 落库订单（带渠道与活动信息）。
     *
     * <p>秒杀订单与普通订单共用这一段：只有「渠道、活动 ID、订单号前缀」不同。
     * 分成两个方法写会出现「一边改了快照口径、另一边忘了」的经典分叉。</p>
     */
    private Order saveOrder(Long userId, UserAddress address, String remark,
                            BigDecimal total, List<OrderItem> drafts,
                            int channel, long activityId, String orderNo) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setChannel(channel);
        order.setActivityId(activityId);
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

    /**
     * 批量取一页订单的明细并按订单号分组（一次 IN 查询）。
     *
     * <p>列表页原本对每条订单各查一次明细，10 条就是 11 条 SQL；改成本方法后固定为 2 条
     * （1 次分页 + 1 次明细）。明细顺序仍由 {@code orderByAsc(id)} 保证，与单查时一致。</p>
     *
     * <p>没有明细的订单不会出现在返回的 map 里，调用方按 {@code null} 处理即可。</p>
     */
    private Map<String, List<OrderItemVO>> loadItemsGrouped(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return Map.of();
        }
        List<String> orderNos = orders.stream().map(Order::getOrderNo).toList();
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .in(OrderItem::getOrderNo, orderNos)
                .orderByAsc(OrderItem::getId));

        Map<String, List<OrderItemVO>> grouped = new LinkedHashMap<>();
        for (OrderItem item : items) {
            grouped.computeIfAbsent(item.getOrderNo(), key -> new ArrayList<>()).add(toItemVO(item));
        }
        return grouped;
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

    private OrderListItemVO toListItemVO(Order order, List<OrderItemVO> items) {
        OrderListItemVO vo = new OrderListItemVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusLabel(OrderStatus.of(order.getStatus()).getLabel());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setCreatedAt(order.getCreatedAt());

        List<OrderItemVO> safeItems = items == null ? List.of() : items;
        vo.setItems(safeItems);
        vo.setTotalQuantity(safeItems.stream().mapToInt(OrderItemVO::getQuantity).sum());
        return vo;
    }

    /**
     * 组装后台订单列表项。
     *
     * @param order   订单
     * @param payment 该订单最新一条支付流水，可为 null（用户尚未发起过支付）
     * @param items   该订单明细，由调用方批量查回后传入（避免逐单查询）
     */
    private AdminOrderListItemVO toAdminListItemVO(Order order, PaymentRecord payment,
                                                  List<OrderItemVO> items) {
        AdminOrderListItemVO vo = new AdminOrderListItemVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setReceiver(order.getReceiver());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddr(order.getReceiverAddr());
        vo.setStatus(order.getStatus());
        vo.setStatusLabel(OrderStatus.of(order.getStatus()).getLabel());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setPayTime(order.getPayTime());
        vo.setShipTime(order.getShipTime());
        vo.setCancelTime(order.getCancelTime());
        // 秒杀订单：订单来源渠道（0 普通 / 1 秒杀），与支付渠道无关
        vo.setSeckill(Integer.valueOf(CHANNEL_SECKILL).equals(order.getChannel()));
        if (payment != null) {
            vo.setPayNo(payment.getPayNo());
            vo.setChannel(payment.getChannel());
        }

        List<OrderItemVO> safeItems = items == null ? List.of() : items;
        vo.setItems(safeItems);
        vo.setTotalQuantity(safeItems.stream().mapToInt(OrderItemVO::getQuantity).sum());
        return vo;
    }

    /**
     * 生成订单号：{@code NO} + 17 位时间戳 + 4 位随机数。
     *
     * <p>时间戳精确到毫秒，叠加随机数后重复概率极低；数据库仍有 {@code uk_order_no} 兜底。</p>
     */
    private String generateOrderNo() {
        return generateOrderNo(ORDER_NO_PREFIX);
    }

    private String generateOrderNo(String prefix) {
        String timestamp = LocalDateTime.now().format(ORDER_NO_TIME);
        String random = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        return prefix + timestamp + random;
    }
}
