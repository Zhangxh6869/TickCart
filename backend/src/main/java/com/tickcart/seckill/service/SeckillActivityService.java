package com.tickcart.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    private final SeckillActivityMapper activityMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;

    public SeckillActivityService(SeckillActivityMapper activityMapper,
                                  ProductMapper productMapper,
                                  ProductSkuMapper productSkuMapper) {
        this.activityMapper = activityMapper;
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
    }

    // -------------------------------------------------------------------------
    // 查询
    // -------------------------------------------------------------------------

    /**
     * 后台分页列表。
     *
     * <p>{@code status} 是推导值，这里把它翻译成 {@code enabled} + 时间区间的组合条件；
     * 一旦指定了 status，就以它为准、忽略 {@code enabled} 参数（否则两个条件会互相打架）。</p>
     */
    public PageResult<SeckillActivityVO> page(SeckillActivityQuery query) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<SeckillActivity> wrapper = new LambdaQueryWrapper<>();

        if (query.getStatus() != null) {
            applyStatusFilter(wrapper, query.getStatus(), now);
        } else if (query.getEnabled() != null) {
            wrapper.eq(SeckillActivity::getEnabled, query.getEnabled());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(SeckillActivity::getTitle, query.getKeyword().trim());
        }
        wrapper.orderByDesc(SeckillActivity::getId);

        Page<SeckillActivity> page = activityMapper.selectPage(
                Page.of(query.getPage(), query.getSize()), wrapper);
        return new PageResult<>(toVOs(page.getRecords(), now),
                page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
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

    /** 新建活动。 */
    @Transactional
    public Long create(SeckillActivitySaveRequest req) {
        long skuId = normalizeSkuId(req.getSkuId());
        BigDecimal originalPrice = validateProduct(req.getProductId(), skuId);
        validatePrice(req.getSeckillPrice(), originalPrice);
        validateTimeRange(req.getStartTime(), req.getEndTime());

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
        // 新建默认上线：与「建完就能在后台看到」的直觉一致，需要下架再手动关
        activity.setEnabled(ENABLED_ON);
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
    }

    /**
     * 上线 / 下线。
     *
     * <p>下线只是让活动不再出现在前台、抢购接口拒绝；已成交订单不受影响。</p>
     *
     * <p>B2 接入后：上线成功需触发库存预热（{@code loadStockIntoRedis}），
     * 否则用户点抢购会发现 Redis 里没有库存。</p>
     */
    @Transactional
    public void changeEnabled(Long id, Integer enabled) {
        if (enabled == null || (enabled != ENABLED_ON && enabled != ENABLED_OFF)) {
            throw BizException.badRequest("enabled 只能是 0 或 1");
        }
        SeckillActivity activity = requireActivity(id);
        activity.setEnabled(enabled);
        activityMapper.updateById(activity);
        log.info("秒杀活动 {} 已{}", id, enabled == ENABLED_ON ? "上线" : "下线");
    }

    /** 删除活动；有成交记录时拒绝（保留对账依据）。 */
    @Transactional
    public void delete(Long id) {
        SeckillActivity activity = requireActivity(id);
        if (nullToZero(activity.getSoldCount()) > 0) {
            throw BizException.conflict("该活动已有成交记录，不能删除；如需停止请下线");
        }
        activityMapper.deleteById(id);
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
            vos.add(vo);
        }
        return vos;
    }

    private SeckillActivityVO toVO(SeckillActivity activity, LocalDateTime now) {
        SeckillActivityVO vo = SeckillActivityVO.from(activity, now);
        fillProductSnapshot(vo, productMapper.selectById(activity.getProductId()),
                activity.getSkuId() > NO_SKU ? productSkuMapper.selectById(activity.getSkuId()) : null);
        return vo;
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
