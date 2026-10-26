package com.tickcart.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tickcart.common.api.PageResult;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.entity.Product;
import com.tickcart.product.entity.ProductSku;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.product.mapper.ProductSkuMapper;
import com.tickcart.seckill.dto.SeckillActivityQuery;
import com.tickcart.seckill.dto.SeckillActivitySaveRequest;
import com.tickcart.seckill.entity.SeckillActivity;
import com.tickcart.seckill.enums.SeckillActivityStatus;
import com.tickcart.seckill.mapper.SeckillActivityMapper;
import com.tickcart.seckill.vo.SeckillActivityVO;
import com.tickcart.seckill.vo.SeckillOverviewVO;
import com.tickcart.trade.entity.Order;
import com.tickcart.trade.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 秒杀活动服务：后台 CRUD 与前台列表。
 *
 * <p><b>状态不落库</b>：全部由 {@link SeckillActivityStatus#of} 按时间推导，
 * 因此本类里任何「状态」判断都必须用推导值，不能读某个字段。</p>
 *
 * <p><b>库存两套账</b>：这里的 {@code seckillStock / soldCount} 只用于后台配置与展示，
 * 真正的扣减发生在 Redis（B2）。本类不碰 {@code product_sku.stock}。</p>
 */
@Service
public class SeckillActivityService {

    private static final Logger log = LoggerFactory.getLogger(SeckillActivityService.class);

    private static final int ENABLED_OFF = 0;
    private static final int ENABLED_ON = 1;
    private static final long NO_SKU = 0L;
    /** 批量操作上限：与商品批量接口保持一致 */
    private static final int BATCH_LIMIT = 100;
    /** 活动标题最长 64 字（与 DDL 一致） */
    private static final int TITLE_MAX = 64;
    private static final DateTimeFormatter TIME_TEXT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /** 排序白名单：key 是前端传的字段名，value 是数据库列名。 */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "startTime", "start_time",
            "endTime", "end_time",
            "id", "id");

    /**
     * 算「成交」的订单状态：待发货 / 待收货 / 已完成。
     *
     * <p>待付款（0）不算成交；已取消（4）与已超时（5）的钱不能算收入。
     * 与订单页概览的口径一致。</p>
     */
    private static final List<Integer> PAID_ORDER_STATUSES = List.of(1, 2, 3);

    private final SeckillActivityMapper activityMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final SeckillStockService stockService;
    private final OrderMapper orderMapper;

    public SeckillActivityService(SeckillActivityMapper activityMapper,
                                  ProductMapper productMapper,
                                  ProductSkuMapper productSkuMapper,
                                  SeckillStockService stockService,
                                  OrderMapper orderMapper) {
        this.activityMapper = activityMapper;
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.stockService = stockService;
        this.orderMapper = orderMapper;
    }

    // -------------------------------------------------------------------------
    // 查询
    // -------------------------------------------------------------------------

    /**
     * 后台分页列表。
     *
     * <p>{@code status} 是推导值，这里把它翻译成 {@code enabled} + 时间区间的组合条件；
     * 一旦指定了 status，就以它为准、忽略 {@code enabled} 参数（否则两个条件会互相打架）。</p>
     *
     * <p><b>默认排序不是 id 倒序</b>：按「进行中 → 即将开始 → 已结束 → 已下线」沉底。
     * 管理员打开页面要看的是「现在有什么场」，而不是「最后建的是哪一场」——
     * 已结束的活动会一直堆在库里（不能删、也改不了价），按 id 排会挤满第一屏。</p>
     */
    public PageResult<SeckillActivityVO> page(SeckillActivityQuery query) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<SeckillActivity> wrapper = new LambdaQueryWrapper<>();

        if (query.getStatus() != null) {
            applyStatusFilter(wrapper, query.getStatus(), now);
        } else if (Boolean.TRUE.equals(query.getActiveOnly())) {
            // 在场 = 已上线且未结束（进行中 + 即将开始）
            wrapper.eq(SeckillActivity::getEnabled, ENABLED_ON)
                    .gt(SeckillActivity::getEndTime, now);
        } else if (query.getEnabled() != null) {
            wrapper.eq(SeckillActivity::getEnabled, query.getEnabled());
        }
        applyKeyword(wrapper, query.getKeyword());
        applyDateRange(wrapper, query);
        applySort(wrapper, query);

        Page<SeckillActivity> page = activityMapper.selectPage(
                Page.of(query.getPage(), query.getSize()), wrapper);
        List<SeckillActivityVO> records = toVOs(page.getRecords(), now);
        fillSales(records);
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    /**
     * 页头概览。
     *
     * <p>活动量在百级，全量查回内存分类比写 4 条聚合 SQL 更简单也更准 ——
     * 状态是推导的，SQL 里拼时间条件容易和 {@link SeckillActivityStatus#of} 的口径漂移。</p>
     */
    public SeckillOverviewVO overview() {
        LocalDateTime now = LocalDateTime.now();
        SeckillOverviewVO vo = new SeckillOverviewVO();

        List<SeckillActivity> all = activityMapper.selectList(null);
        LocalDateTime nextStart = null;
        LocalDateTime ongoingEnd = null;
        long soldQuantity = 0;

        for (SeckillActivity activity : all) {
            SeckillActivityStatus status = SeckillActivityStatus.of(activity, now);
            soldQuantity += nullToZero(activity.getSoldCount());
            switch (status) {
                case ONGOING -> {
                    vo.setOngoing(vo.getOngoing() + 1);
                    if (ongoingEnd == null || activity.getEndTime().isBefore(ongoingEnd)) {
                        ongoingEnd = activity.getEndTime();
                    }
                }
                case PENDING -> {
                    vo.setPending(vo.getPending() + 1);
                    if (nextStart == null || activity.getStartTime().isBefore(nextStart)) {
                        nextStart = activity.getStartTime();
                    }
                }
                case ENDED -> {
                    vo.setEnded(vo.getEnded() + 1);
                    if (Integer.valueOf(ENABLED_ON).equals(activity.getEnabled())) {
                        vo.setEndedButEnabled(vo.getEndedButEnabled() + 1);
                    }
                }
                case DISABLED -> vo.setDisabled(vo.getDisabled() + 1);
            }
        }

        vo.setTotal(all.size());
        vo.setSoldQuantity(soldQuantity);
        vo.setNextStartTime(nextStart);
        vo.setOngoingEndTime(ongoingEnd);
        fillOverviewSales(vo);
        return vo;
    }

    /** 前台列表：已上线且未结束（进行中 + 即将开始），按开始时间升序。 */
    public List<SeckillActivityVO> listActive() {
        LocalDateTime now = LocalDateTime.now();
        List<SeckillActivity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getEnabled, ENABLED_ON)
                        .gt(SeckillActivity::getEndTime, now)
                        .orderByAsc(SeckillActivity::getStartTime)
                        .orderByAsc(SeckillActivity::getId));
        return toVOs(activities, now);
    }

    /** 活动详情（后台用，不做上线/时间过滤）。 */
    public SeckillActivityVO detail(Long id) {
        return toVO(requireActivity(id), LocalDateTime.now());
    }

    /**
     * 活动详情（前台用）。
     *
     * <p>已下线的活动按不存在处理 —— 否则用户能从收藏/分享链接继续访问一场已撤销的活动。
     * 已结束的活动仍可查看（页面展示「已结束」比 404 更友好）。</p>
     */
    public SeckillActivityVO publicDetail(Long id) {
        SeckillActivity activity = requireActivity(id);
        if (!Integer.valueOf(ENABLED_ON).equals(activity.getEnabled())) {
            throw BizException.notFound("秒杀活动不存在");
        }
        return toVO(activity, LocalDateTime.now());
    }

    /** 按 ID 取活动；不存在抛 404。B2/B3 抢购链路也复用本方法。 */
    public SeckillActivity requireActivity(Long id) {
        SeckillActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw BizException.notFound("秒杀活动不存在");
        }
        return activity;
    }

    // -------------------------------------------------------------------------
    // 写入
    // -------------------------------------------------------------------------

    /**
     * 新建活动，<b>一律建成草稿（enabled=0）</b>。
     *
     * <p>旧实现默认上线并立即预热 —— 实测一场 2027 年的活动保存后立刻出现在前台，
     * 日期填错一次就暴露一次。现在「上线」是一个显式动作（{@link #changeEnabled}），
     * 它同时负责预热与二次校验。</p>
     */
    @Transactional
    public Long create(SeckillActivitySaveRequest req) {
        long skuId = normalizeSkuId(req.getSkuId());
        BigDecimal originalPrice = validateProduct(req.getProductId(), skuId);
        validatePrice(req.getSeckillPrice(), originalPrice);
        validateTimeRange(req.getStartTime(), req.getEndTime());
        validateNoOverlap(req.getProductId(), skuId, req.getStartTime(), req.getEndTime(), null);

        SeckillActivity activity = new SeckillActivity();
        activity.setTitle(req.getTitle().trim());
        activity.setProductId(req.getProductId());
        activity.setSkuId(skuId);
        activity.setSeckillPrice(req.getSeckillPrice());
        activity.setSeckillStock(req.getSeckillStock());
        activity.setSoldCount(0);
        activity.setLimitPerUser(req.getLimitPerUser());
        activity.setStartTime(req.getStartTime());
        activity.setEndTime(req.getEndTime());
        activity.setEnabled(ENABLED_OFF);
        activityMapper.insert(activity);
        return activity.getId();
    }

    /**
     * 编辑活动。
     *
     * <p><b>已开始的活动禁止改秒杀价与库存</b>：改价会让已下单用户的价格与展示不符；
     * 改库存会让 Redis（B2 之后才是权威）与 DB 的账对不上。标题与限购不受限。</p>
     */
    @Transactional
    public void update(Long id, SeckillActivitySaveRequest req) {
        SeckillActivity activity = requireActivity(id);
        long skuId = normalizeSkuId(req.getSkuId());
        BigDecimal originalPrice = validateProduct(req.getProductId(), skuId);
        validatePrice(req.getSeckillPrice(), originalPrice);
        validateTimeRange(req.getStartTime(), req.getEndTime());

        validateNoOverlap(req.getProductId(), skuId, req.getStartTime(), req.getEndTime(), id);

        SeckillActivityStatus status = SeckillActivityStatus.of(activity, LocalDateTime.now());
        if (status.isStarted()) {
            if (req.getSeckillPrice().compareTo(activity.getSeckillPrice()) != 0) {
                throw BizException.conflict("活动已开始，不能修改秒杀价");
            }
            if (!req.getSeckillStock().equals(activity.getSeckillStock())) {
                throw BizException.conflict("活动已开始，不能修改秒杀库存");
            }
        }
        // 已售超过新库存会让剩余变负；即使在未开始阶段也要拦住
        if (req.getSeckillStock() < nullToZero(activity.getSoldCount())) {
            throw BizException.badRequest("秒杀库存不能小于已成交数量（" + nullToZero(activity.getSoldCount()) + "）");
        }

        activity.setTitle(req.getTitle().trim());
        activity.setProductId(req.getProductId());
        activity.setSkuId(skuId);
        activity.setSeckillPrice(req.getSeckillPrice());
        activity.setSeckillStock(req.getSeckillStock());
        activity.setLimitPerUser(req.getLimitPerUser());
        activity.setStartTime(req.getStartTime());
        activity.setEndTime(req.getEndTime());
        activityMapper.updateById(activity);
        // 改了时间或限购必须刷新快照：否则抢购接口仍按旧时间判断「进行中」
        syncCacheAfterCommit(() -> stockService.refreshSnapshot(activity));
    }

    /**
     * 上线 / 下线。
     *
     * <p>下线只是让活动不再出现在前台、抢购接口拒绝；已成交订单不受影响。</p>
     *
     * <p>上线会<b>同步预热库存</b>（写 Redis 快照与剩余库存），否则用户点抢购会拿到
     * 「活动尚未就绪」。预热放在<b>事务提交之后</b>执行：若先写 Redis 再回滚，
     * 会出现「活动还是下线状态、Redis 里却能抢」的幽灵活动。</p>
     */
    @Transactional
    public void changeEnabled(Long id, Integer enabled) {
        if (enabled == null || (enabled != ENABLED_ON && enabled != ENABLED_OFF)) {
            throw BizException.badRequest("enabled 只能是 0 或 1");
        }
        SeckillActivity activity = requireActivity(id);
        if (enabled == ENABLED_ON) {
            // 上线前二次校验：草稿期间商品可能已下架、别处可能已排了重叠的场
            validateProduct(activity.getProductId(), activity.getSkuId());
            validateNoOverlap(activity.getProductId(), activity.getSkuId(),
                    activity.getStartTime(), activity.getEndTime(), id);
        }
        activity.setEnabled(enabled);
        activityMapper.updateById(activity);
        syncCacheAfterCommit(enabled == ENABLED_ON
                ? () -> stockService.preload(activity)
                : () -> stockService.refreshSnapshot(activity));
        log.info("秒杀活动 {} 已{}", id, enabled == ENABLED_ON ? "上线" : "下线");
    }

    /**
     * 批量上线 / 下线（整批成功或整批失败）。
     *
     * <p>语义与商品批量接口一致：id 必须全部存在、重复先去重、上限 100。
     * 不做半成功 —— 运营点的是「把这 3 场都下线」，成功 2 场会让人以为都成了。</p>
     *
     * <p>批量上线时逐条校验重叠：同一批里两场互相重叠时，第一场上线后第二场就会被拦下，
     * 整批回滚。</p>
     */
    @Transactional
    public int batchEnabled(List<Long> ids, Integer enabled) {
        if (enabled == null || (enabled != ENABLED_ON && enabled != ENABLED_OFF)) {
            throw BizException.badRequest("enabled 只能是 0 或 1");
        }
        if (ids == null || ids.isEmpty()) {
            throw BizException.badRequest("请选择要操作的活动");
        }
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            throw BizException.badRequest("请选择要操作的活动");
        }
        if (distinct.size() > BATCH_LIMIT) {
            throw BizException.badRequest("单次最多操作 " + BATCH_LIMIT + " 场活动");
        }
        List<SeckillActivity> activities = activityMapper.selectBatchIds(distinct);
        if (activities.size() != distinct.size()) {
            throw BizException.badRequest("部分活动不存在，请刷新后重试");
        }
        for (SeckillActivity activity : activities) {
            if (enabled == ENABLED_ON) {
                validateProduct(activity.getProductId(), activity.getSkuId());
                validateNoOverlap(activity.getProductId(), activity.getSkuId(),
                        activity.getStartTime(), activity.getEndTime(), activity.getId());
            }
            activity.setEnabled(enabled);
            activityMapper.updateById(activity);
            syncCacheAfterCommit(enabled == ENABLED_ON
                    ? () -> stockService.preload(activity)
                    : () -> stockService.refreshSnapshot(activity));
        }
        log.info("秒杀活动批量{}：{} 场", enabled == ENABLED_ON ? "上线" : "下线", activities.size());
        return activities.size();
    }

    /**
     * 复制为新活动。
     *
     * <p>旧活动「改不了价（已开始 409）、删不掉（有成交 409）、也没有复制」，
     * 复用只能手填一遍 —— 这个接口就是补这条路的。</p>
     *
     * <p>复制出来的<b>一律是草稿</b>，且 {@code soldCount} 必须显式归零：
     * 直接把实体拷过去会带着「已售 1/100」开局。</p>
     *
     * <p>时间平移到「原结束时间的次日同一时刻」而不是「明天同一时段」：
     * 跨天场（如 10-19 ~ 10-22）平移一天仍与原活动重叠，会自己撞自己。</p>
     *
     * <p>这里<b>不做重叠校验</b>：复制是「拿个模板」，时间必然要改；
     * 校验放在 {@link #changeEnabled} 上线时。</p>
     */
    @Transactional
    public Long copy(Long id) {
        SeckillActivity source = requireActivity(id);
        if (productMapper.selectById(source.getProductId()) == null) {
            throw BizException.badRequest("商品已删除，无法复制");
        }
        Duration duration = Duration.between(source.getStartTime(), source.getEndTime());
        LocalDateTime newStart = source.getEndTime().plusDays(1);
        LocalDateTime newEnd = newStart.plus(duration);

        SeckillActivity copy = new SeckillActivity();
        copy.setTitle(truncateTitle(source.getTitle() + " 副本"));
        copy.setProductId(source.getProductId());
        copy.setSkuId(source.getSkuId());
        copy.setSeckillPrice(source.getSeckillPrice());
        copy.setSeckillStock(source.getSeckillStock());
        copy.setSoldCount(0);
        copy.setLimitPerUser(source.getLimitPerUser());
        copy.setStartTime(newStart);
        copy.setEndTime(newEnd);
        copy.setEnabled(ENABLED_OFF);
        activityMapper.insert(copy);
        log.info("秒杀活动 {} 已复制为 {}（草稿）", id, copy.getId());
        return copy.getId();
    }

    /**
     * 提交后再同步 Redis。
     *
     * <p><b>必须等提交</b>：先写 Redis 再回滚，会出现「活动还是下线状态、Redis 里却能抢」
     * 的幽灵活动 —— 比「晚 30 秒生效」严重得多。</p>
     *
     * <p>同步失败只记 ERROR：定时任务每 30 秒会重扫「已上线且未结束」的活动补预热，
     * 最终会自愈，不必让管理员的操作失败。</p>
     */
    private void syncCacheAfterCommit(Runnable action) {
        Runnable guarded = () -> {
            try {
                action.run();
            } catch (Exception e) {
                log.error("秒杀活动 Redis 同步失败，定时任务会在 30 秒内重试", e);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            guarded.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                guarded.run();
            }
        });
    }

    /** 删除活动；有成交记录时拒绝（保留对账依据）。 */
    @Transactional
    public void delete(Long id) {
        SeckillActivity activity = requireActivity(id);
        if (nullToZero(activity.getSoldCount()) > 0) {
            throw BizException.conflict("该活动已有成交记录，不能删除；如需停止请下线");
        }
        activityMapper.deleteById(id);
        syncCacheAfterCommit(() -> stockService.evict(id));
    }

    // -------------------------------------------------------------------------
    // 内部
    // -------------------------------------------------------------------------

    /** 把推导状态翻译成 SQL 条件。 */
    private void applyStatusFilter(LambdaQueryWrapper<SeckillActivity> wrapper, int status, LocalDateTime now) {
        switch (status) {
            case 0 -> wrapper.eq(SeckillActivity::getEnabled, ENABLED_OFF);
            case 1 -> wrapper.eq(SeckillActivity::getEnabled, ENABLED_ON)
                    .gt(SeckillActivity::getStartTime, now);
            case 2 -> wrapper.eq(SeckillActivity::getEnabled, ENABLED_ON)
                    .le(SeckillActivity::getStartTime, now)
                    .gt(SeckillActivity::getEndTime, now);
            case 3 -> wrapper.eq(SeckillActivity::getEnabled, ENABLED_ON)
                    .le(SeckillActivity::getEndTime, now);
            default -> throw BizException.badRequest("状态值不合法：" + status);
        }
    }

    /**
     * 关键字同时匹配活动标题与商品名。
     *
     * <p>运营记得住「牛油果那场」，记不住活动标题叫「测试」—— 只搜标题等于搜不到。</p>
     *
     * <p>商品名走子查询而不是 JOIN：本项目的 Mapper 全是 BaseMapper + QueryWrapper，
     * 没有 XML，引 JOIN 得先造一份 XML 出来，代价大于收益。{@code {0}} 是参数绑定，
     * 不要拼字符串。</p>
     */
    private void applyKeyword(LambdaQueryWrapper<SeckillActivity> wrapper, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String like = "%" + keyword.trim() + "%";
        wrapper.and(w -> w.like(SeckillActivity::getTitle, keyword.trim())
                .or()
                .apply("product_id IN (SELECT id FROM product WHERE name LIKE {0} AND deleted = 0)", like));
    }

    /**
     * 按活动时间筛选（闭区间）。
     *
     * <p>用「开始时间 &gt;= beginDate 00:00」与「结束时间 &lt; endDate+1 00:00」，
     * 这样「今天」能命中所有与今天有交集的活动，而不是只命中今天开始的。</p>
     */
    private void applyDateRange(LambdaQueryWrapper<SeckillActivity> wrapper, SeckillActivityQuery query) {
        if (query.getBeginDate() != null) {
            wrapper.ge(SeckillActivity::getStartTime, query.getBeginDate().atStartOfDay());
        }
        if (query.getEndDate() != null) {
            wrapper.lt(SeckillActivity::getEndTime, query.getEndDate().plusDays(1).atStartOfDay());
        }
    }

    /**
     * 排序：白名单映射，未知字段回落「沉底排序」。
     *
     * <p>沉底排序用 SQL 表达式而不是 Java 排序 —— 分页在 DB 层，Java 里排只能排当前页，
     * 会把「进行中」的活动排到第 2 页去。</p>
     *
     * <p>{@code NOW()} 用数据库时间而不是拼接 Java 时间：省掉格式化，也没有注入面。
     * 数据库与本服务的时区都是 Asia/Shanghai，毫秒级差异不影响排序。</p>
     */
    private void applySort(LambdaQueryWrapper<SeckillActivity> wrapper, SeckillActivityQuery query) {
        String column = query.getSortBy() == null ? null : SORT_COLUMNS.get(query.getSortBy());
        if (column == null) {
            wrapper.last("ORDER BY CASE WHEN enabled = 0 THEN 4 "
                    + "WHEN start_time <= NOW() AND end_time > NOW() THEN 1 "
                    + "WHEN start_time > NOW() THEN 2 ELSE 3 END ASC, start_time DESC, id DESC");
            return;
        }
        boolean asc = "asc".equalsIgnoreCase(query.getOrder());
        wrapper.last("ORDER BY " + column + (asc ? " ASC" : " DESC") + ", id DESC");
    }

    private long normalizeSkuId(Long skuId) {
        return skuId == null ? NO_SKU : skuId;
    }

    /**
     * 商品必须存在且在售；有 SKU 时必须属于该商品。
     *
     * @return 该商品（或规格）的原价，供「秒杀价必须更低」校验使用
     */
    private BigDecimal validateProduct(Long productId, long skuId) {
        Product product = productMapper.selectById(productId);
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            throw BizException.badRequest("商品不存在或已下架");
        }
        BigDecimal originalPrice = product.getPrice();
        if (skuId > NO_SKU) {
            ProductSku sku = productSkuMapper.selectById(skuId);
            if (sku == null || !productId.equals(sku.getProductId())) {
                throw BizException.badRequest("商品规格不存在");
            }
            if (sku.getPrice() != null) {
                originalPrice = sku.getPrice();
            }
        }
        return originalPrice;
    }

    /**
     * 秒杀价必须低于原价。
     *
     * <p>不校验的话很容易建出「秒杀价比原价还贵」的活动 —— 用户看到「秒杀」两个字
     * 反而多付钱，是最伤的体验事故。</p>
     */
    private void validatePrice(BigDecimal seckillPrice, BigDecimal originalPrice) {
        if (originalPrice != null && seckillPrice.compareTo(originalPrice) >= 0) {
            throw BizException.badRequest("秒杀价必须低于商品原价（当前原价 " + originalPrice + "）");
        }
    }

    private void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        if (!start.isBefore(end)) {
            throw BizException.badRequest("开始时间必须早于结束时间");
        }
    }

    /**
     * 同一规格在同一时间只能有一场<b>上线中</b>的活动。
     *
     * <p>不校验的后果是实测过的：同一 SKU 建两场重叠活动都会成功，前台同时出现两张
     * 同商品卡，库存两套账，用户不知道抢哪个。</p>
     *
     * <p>只和 {@code enabled=1} 的活动比：草稿之间可以重叠（先排两个候选方案是正常操作），
     * 但草稿<b>上线</b>时会再校验一次 —— 那时另一场已经上线，就会被拦下。</p>
     *
     * <p>规格维度：{@code skuId > 0} 时与「同规格」和「该商品的商品级活动」都冲突；
     * {@code skuId = 0}（商品级）时与该商品下任何活动都冲突。</p>
     *
     * <p>区间用 {@code [start, end)}：前一场 22:00 结束、后一场 22:00 开始不算重叠。</p>
     */
    private void validateNoOverlap(long productId, long skuId, LocalDateTime start,
                                   LocalDateTime end, Long selfId) {
        LambdaQueryWrapper<SeckillActivity> wrapper = new LambdaQueryWrapper<SeckillActivity>()
                .eq(SeckillActivity::getEnabled, ENABLED_ON)
                .eq(SeckillActivity::getProductId, productId)
                .lt(SeckillActivity::getStartTime, end)
                .gt(SeckillActivity::getEndTime, start);
        if (skuId > NO_SKU) {
            wrapper.and(w -> w.eq(SeckillActivity::getSkuId, skuId)
                    .or()
                    .eq(SeckillActivity::getSkuId, NO_SKU));
        }
        if (selfId != null) {
            wrapper.ne(SeckillActivity::getId, selfId);
        }
        wrapper.orderByAsc(SeckillActivity::getId).last("LIMIT 1");

        List<SeckillActivity> conflicts = activityMapper.selectList(wrapper);
        if (conflicts.isEmpty()) {
            return;
        }
        SeckillActivity conflict = conflicts.get(0);
        throw BizException.conflict("同一规格在 " + TIME_TEXT.format(start) + " ~ " + TIME_TEXT.format(end)
                + " 已有一场上线中的活动（#" + conflict.getId() + "「" + conflict.getTitle()
                + "」），请调整时间或先下线那场");
    }

    /** 标题超长时截断（复制时加的「 副本」后缀可能把 64 字撑爆）。 */
    private static String truncateTitle(String title) {
        return title.length() <= TITLE_MAX ? title : title.substring(0, TITLE_MAX);
    }

    /** 批量组装 VO：商品信息一次 IN 查回，避免列表页 N+1。 */
    private List<SeckillActivityVO> toVOs(List<SeckillActivity> activities, LocalDateTime now) {
        if (activities.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> products = loadProducts(activities);
        Map<Long, ProductSku> skus = loadSkus(activities);

        List<SeckillActivityVO> vos = new ArrayList<>(activities.size());
        for (SeckillActivity activity : activities) {
            SeckillActivityVO vo = SeckillActivityVO.from(activity, now);
            fillProductSnapshot(vo, products.get(activity.getProductId()), skus.get(activity.getSkuId()));
            vo.setPreloaded(overrideRemainingFromRedis(vo));
            vos.add(vo);
        }
        return vos;
    }

    private SeckillActivityVO toVO(SeckillActivity activity, LocalDateTime now) {
        SeckillActivityVO vo = SeckillActivityVO.from(activity, now);
        fillProductSnapshot(vo, productMapper.selectById(activity.getProductId()),
                activity.getSkuId() > NO_SKU ? productSkuMapper.selectById(activity.getSkuId()) : null);
        vo.setPreloaded(overrideRemainingFromRedis(vo));
        return vo;
    }

    /**
     * 剩余库存以 Redis 为准（B2 的 Lua 扣的是它），DB 的 {@code sold_count} 会滞后。
     * 未预热时保留 DB 口径（VO 里已算好）。
     *
     * @return Redis 里是否有库存 key（顺带当「是否已预热」用，省一次 Redis 调用）
     */
    private boolean overrideRemainingFromRedis(SeckillActivityVO vo) {
        Integer redisStock = stockService.currentStock(vo.getId());
        if (redisStock == null) {
            return false;
        }
        vo.setRemainingStock(redisStock);
        return true;
    }

    /**
     * 批量填成交额与成交单数（列表页）。
     *
     * <p>一次 {@code GROUP BY activity_id} 拿全页，避免每行查一次订单表。</p>
     */
    private void fillSales(List<SeckillActivityVO> vos) {
        if (vos.isEmpty()) {
            return;
        }
        List<Long> ids = vos.stream().map(SeckillActivityVO::getId).toList();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        for (Map<String, Object> row : selectSales(new QueryWrapper<Order>()
                .select("activity_id", "COUNT(*) AS cnt", "SUM(pay_amount) AS amt")
                .in("activity_id", ids)
                .in("status", PAID_ORDER_STATUSES)
                .groupBy("activity_id"))) {
            Long activityId = longOf(row.get("activity_id"));
            if (activityId == null) {
                continue;
            }
            Long cnt = longOf(row.get("cnt"));
            counts.put(activityId, cnt == null ? 0 : cnt.intValue());
            amounts.put(activityId, decimalOf(row.get("amt")));
        }
        for (SeckillActivityVO vo : vos) {
            vo.setPaidOrderCount(counts.getOrDefault(vo.getId(), 0));
            vo.setPaidAmount(amounts.getOrDefault(vo.getId(), BigDecimal.ZERO));
        }
    }

    /** 全局成交额与成交单数（概览用）。 */
    private void fillOverviewSales(SeckillOverviewVO vo) {
        for (Map<String, Object> row : selectSales(new QueryWrapper<Order>()
                .select("COUNT(*) AS cnt", "SUM(pay_amount) AS amt")
                .gt("activity_id", 0)
                .in("status", PAID_ORDER_STATUSES))) {
            Long cnt = longOf(row.get("cnt"));
            vo.setPaidOrderCount(cnt == null ? 0 : cnt);
            vo.setPaidAmount(decimalOf(row.get("amt")));
        }
    }

    private List<Map<String, Object>> selectSales(QueryWrapper<Order> wrapper) {
        return orderMapper.selectMaps(wrapper);
    }

    /** 聚合列取值：COUNT 在不同驱动下可能是 Long / BigInteger，统一收敛成 Long。 */
    private static Long longOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    /** SUM 可能是 BigDecimal / Double，统一收敛成 BigDecimal。 */
    private static BigDecimal decimalOf(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    /**
     * 填商品快照：有规格取规格自身的价格与图片，规格价为空时回退商品价 ——
     * 与购物车、立即购买的口径保持一致，避免三处各算一套。
     */
    private void fillProductSnapshot(SeckillActivityVO vo, Product product, ProductSku sku) {
        if (product == null) {
            vo.setProductName("商品已删除");
            return;
        }
        vo.setProductName(product.getName());
        vo.setProductImage(product.getMainImage());
        vo.setOriginalPrice(product.getPrice());
        if (sku != null) {
            vo.setSpecValue(sku.getSpecValue());
            if (sku.getPrice() != null) {
                vo.setOriginalPrice(sku.getPrice());
            }
            if (StringUtils.hasText(sku.getSkuImage())) {
                vo.setProductImage(sku.getSkuImage());
            }
        }
    }

    private Map<Long, Product> loadProducts(List<SeckillActivity> activities) {
        List<Long> ids = activities.stream()
                .map(SeckillActivity::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, ProductSku> loadSkus(List<SeckillActivity> activities) {
        List<Long> ids = activities.stream()
                .map(SeckillActivity::getSkuId)
                .filter(id -> id != null && id > NO_SKU)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productSkuMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ProductSku::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
