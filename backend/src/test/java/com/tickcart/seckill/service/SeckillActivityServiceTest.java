package com.tickcart.seckill.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.entity.Product;
import com.tickcart.product.entity.ProductSku;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.product.mapper.ProductSkuMapper;
import com.tickcart.seckill.dto.SeckillActivityQuery;
import com.tickcart.seckill.dto.SeckillActivitySaveRequest;
import com.tickcart.seckill.entity.SeckillActivity;
import com.tickcart.seckill.mapper.SeckillActivityMapper;
import com.tickcart.seckill.service.SeckillStockService;
import com.tickcart.seckill.vo.SeckillActivityVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SeckillActivityService} 单元测试。
 *
 * <p>覆盖：创建校验（商品/价格/时间/规格）、已开始禁改价改库存、有成交禁删、
 * 上下线、状态推导落到列表。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("秒杀活动服务")
class SeckillActivityServiceTest {

    private static final Long ACTIVITY_ID = 7L;
    private static final Long PRODUCT_ID = 302L;
    private static final Long SKU_ID = 490L;

    @Mock
    private SeckillActivityMapper activityMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductSkuMapper productSkuMapper;
    @Mock
    private SeckillStockService stockService;

    private SeckillActivityService service;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 会立即解析列名，纯单测需先初始化 TableInfo 缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SeckillActivity.class);
    }

    @BeforeEach
    void setUp() {
        service = new SeckillActivityService(
                activityMapper, productMapper, productSkuMapper, stockService);
        // Mockito 对包装类型返回 0（不是 null），必须显式打桩成 null，
        // 否则 currentStock 会被当成「Redis 里剩 0 件」，把 DB 口径覆盖掉
        lenient().when(stockService.currentStock(any())).thenReturn(null);
        lenient().when(productMapper.selectById(PRODUCT_ID)).thenReturn(product("99.00", 1));
        lenient().when(productSkuMapper.selectById(SKU_ID)).thenReturn(sku("88.00"));
    }

    // ---------------- 创建 ----------------

    @Test
    @DisplayName("创建成功：已售初始为 0、默认上线、skuId 归一为 0")
    void createSuccess() {
        when(activityMapper.insert(any(SeckillActivity.class))).thenReturn(1);

        service.create(req(100, 2, SKU_ID, "9.90"));

        ArgumentCaptor<SeckillActivity> captor = ArgumentCaptor.forClass(SeckillActivity.class);
        verify(activityMapper).insert(captor.capture());
        SeckillActivity saved = captor.getValue();
        assertThat(saved.getSoldCount()).isZero();
        assertThat(saved.getEnabled()).isEqualTo(1);
        assertThat(saved.getSkuId()).isEqualTo(SKU_ID);
        assertThat(saved.getSeckillPrice()).isEqualByComparingTo("9.90");
    }

    @Test
    @DisplayName("未传 skuId 时归一为 0（无规格商品）")
    void createNormalizesSkuId() {
        when(activityMapper.insert(any(SeckillActivity.class))).thenReturn(1);

        service.create(req(100, 2, null, "9.90"));

        ArgumentCaptor<SeckillActivity> captor = ArgumentCaptor.forClass(SeckillActivity.class);
        verify(activityMapper).insert(captor.capture());
        assertThat(captor.getValue().getSkuId()).isZero();
    }

    @Test
    @DisplayName("商品不存在或已下架：拒绝")
    void createRejectsOfflineProduct() {
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product("99.00", 0));

        assertThatThrownBy(() -> service.create(req(100, 2, SKU_ID, "9.90")))
                .isInstanceOf(BizException.class)
                .hasMessage("商品不存在或已下架");
    }

    @Test
    @DisplayName("秒杀价不低于原价：拒绝（防止「秒杀」比原价还贵）")
    void createRejectsPriceNotCheaper() {
        assertThatThrownBy(() -> service.create(req(100, 2, SKU_ID, "88.00")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("秒杀价必须低于商品原价");
    }

    @Test
    @DisplayName("开始时间不早于结束时间：拒绝")
    void createRejectsBadTimeRange() {
        SeckillActivitySaveRequest req = req(100, 2, SKU_ID, "9.90");
        req.setEndTime(req.getStartTime());

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BizException.class)
                .hasMessage("开始时间必须早于结束时间");
    }

    @Test
    @DisplayName("SKU 不属于该商品：拒绝")
    void createRejectsForeignSku() {
        ProductSku other = sku("88.00");
        other.setProductId(999L);
        when(productSkuMapper.selectById(SKU_ID)).thenReturn(other);

        assertThatThrownBy(() -> service.create(req(100, 2, SKU_ID, "9.90")))
                .isInstanceOf(BizException.class)
                .hasMessage("商品规格不存在");
    }

    // ---------------- 编辑 ----------------

    @Test
    @DisplayName("活动已开始：禁止改秒杀价")
    void updateRejectsPriceChangeWhenStarted() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity());

        assertThatThrownBy(() -> service.update(ACTIVITY_ID, req(100, 2, SKU_ID, "8.80")))
                .isInstanceOf(BizException.class)
                .hasMessage("活动已开始，不能修改秒杀价");
    }

    @Test
    @DisplayName("活动已开始：禁止改秒杀库存")
    void updateRejectsStockChangeWhenStarted() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity());

        assertThatThrownBy(() -> service.update(ACTIVITY_ID, req(200, 2, SKU_ID, "9.90")))
                .isInstanceOf(BizException.class)
                .hasMessage("活动已开始，不能修改秒杀库存");
    }

    @Test
    @DisplayName("活动已开始：改标题与限购仍然允许")
    void updateAllowsTitleChangeWhenStarted() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoingActivity());
        when(activityMapper.updateById(any(SeckillActivity.class))).thenReturn(1);

        SeckillActivitySaveRequest req = req(100, 3, SKU_ID, "9.90");
        req.setTitle("改个标题");

        service.update(ACTIVITY_ID, req);

        ArgumentCaptor<SeckillActivity> captor = ArgumentCaptor.forClass(SeckillActivity.class);
        verify(activityMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("改个标题");
        assertThat(captor.getValue().getLimitPerUser()).isEqualTo(3);
    }

    @Test
    @DisplayName("新库存小于已成交数量：拒绝（否则剩余库存为负）")
    void updateRejectsStockBelowSold() {
        SeckillActivity activity = futureActivity();
        activity.setSoldCount(50);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);

        assertThatThrownBy(() -> service.update(ACTIVITY_ID, req(40, 2, SKU_ID, "9.90")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能小于已成交数量");
    }

    // ---------------- 上下线与删除 ----------------

    @Test
    @DisplayName("上下线：非法值拒绝")
    void changeEnabledRejectsIllegalValue() {
        assertThatThrownBy(() -> service.changeEnabled(ACTIVITY_ID, 2))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("enabled");
    }

    @Test
    @DisplayName("下线：写入 enabled=0")
    void changeEnabledWritesFlag() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(futureActivity());
        when(activityMapper.updateById(any(SeckillActivity.class))).thenReturn(1);

        service.changeEnabled(ACTIVITY_ID, 0);

        ArgumentCaptor<SeckillActivity> captor = ArgumentCaptor.forClass(SeckillActivity.class);
        verify(activityMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEnabled()).isZero();
    }

    @Test
    @DisplayName("有成交记录：禁止删除")
    void deleteRejectsWhenSold() {
        SeckillActivity activity = futureActivity();
        activity.setSoldCount(1);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);

        assertThatThrownBy(() -> service.delete(ACTIVITY_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能删除");
    }

    @Test
    @DisplayName("无成交记录：允许删除")
    void deleteWithoutSold() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(futureActivity());
        when(activityMapper.deleteById(ACTIVITY_ID)).thenReturn(1);

        service.delete(ACTIVITY_ID);

        verify(activityMapper).deleteById(ACTIVITY_ID);
    }

    // ---------------- 列表 ----------------

    @Test
    @DisplayName("后台列表：状态由时间推导，不是读字段")
    void pageDerivesStatus() {
        SeckillActivity ended = futureActivity();
        ended.setStartTime(LocalDateTime.now().minusDays(2));
        ended.setEndTime(LocalDateTime.now().minusDays(1));
        when(activityMapper.selectPage(ArgumentMatchers.<IPage<SeckillActivity>>any(), any()))
                .thenAnswer(inv -> {
                    IPage<SeckillActivity> page = inv.getArgument(0);
                    page.setRecords(List.of(ended));
                    page.setTotal(1);
                    return page;
                });
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(product("99.00", 1)));
        when(productSkuMapper.selectBatchIds(any())).thenReturn(List.of(sku("88.00")));

        var result = service.page(new SeckillActivityQuery());

        assertThat(result.getRecords()).hasSize(1);
        SeckillActivityVO vo = result.getRecords().get(0);
        assertThat(vo.getStatusLabel()).isEqualTo("已结束");
        // Redis 未预热 → 回退 DB 口径（5 件里已售 0，库存 100 来自测试数据）
        assertThat(vo.getRemainingStock()).isEqualTo(100);
        assertThat(vo.getProductName()).isEqualTo("商品302");
    }

    @Test
    @DisplayName("列表剩余库存以 Redis 为准（DB 的 sold_count 会滞后）")
    void pageUsesRedisRemainingStock() {
        SeckillActivity ongoing = futureActivity();
        when(activityMapper.selectPage(ArgumentMatchers.<IPage<SeckillActivity>>any(), any()))
                .thenAnswer(inv -> {
                    IPage<SeckillActivity> page = inv.getArgument(0);
                    page.setRecords(List.of(ongoing));
                    page.setTotal(1);
                    return page;
                });
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(product("99.00", 1)));
        when(productSkuMapper.selectBatchIds(any())).thenReturn(List.of(sku("88.00")));
        // DB 口径是 100，Redis 只剩 2 → 必须展示 2
        when(stockService.currentStock(ACTIVITY_ID)).thenReturn(2);

        var result = service.page(new SeckillActivityQuery());

        assertThat(result.getRecords().get(0).getRemainingStock()).isEqualTo(2);
    }

    @Test
    @DisplayName("前台详情：已下线按不存在处理")
    void publicDetailRejectsDisabled() {
        SeckillActivity activity = futureActivity();
        activity.setEnabled(0);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);

        assertThatThrownBy(() -> service.publicDetail(ACTIVITY_ID))
                .isInstanceOf(BizException.class)
                .hasMessage("秒杀活动不存在");
    }

    @Test
    @DisplayName("剩余库存不会为负（对账异常时也不把负数给前端）")
    void remainingStockNeverNegative() {
        SeckillActivity activity = futureActivity();
        activity.setSoldCount(120);   // 故意超过库存
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);

        SeckillActivityVO vo = service.detail(ACTIVITY_ID);

        assertThat(vo.getRemainingStock()).isZero();
    }

    // ---------------- 辅助 ----------------

    private SeckillActivitySaveRequest req(int stock, int limit, Long skuId, String seckillPrice) {
        SeckillActivitySaveRequest req = new SeckillActivitySaveRequest();
        req.setTitle("秒杀活动");
        req.setProductId(PRODUCT_ID);
        req.setSkuId(skuId);
        req.setSeckillPrice(new BigDecimal(seckillPrice));
        req.setSeckillStock(stock);
        req.setLimitPerUser(limit);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusHours(3));
        return req;
    }

    private SeckillActivity futureActivity() {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(ACTIVITY_ID);
        activity.setTitle("秒杀活动");
        activity.setProductId(PRODUCT_ID);
        activity.setSkuId(SKU_ID);
        activity.setSeckillPrice(new BigDecimal("9.90"));
        activity.setSeckillStock(100);
        activity.setSoldCount(0);
        activity.setLimitPerUser(2);
        activity.setStartTime(LocalDateTime.now().plusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(3));
        activity.setEnabled(1);
        return activity;
    }

    private SeckillActivity ongoingActivity() {
        SeckillActivity activity = futureActivity();
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        return activity;
    }

    private Product product(String price, int status) {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("商品302");
        product.setMainImage("/img/302.png");
        product.setPrice(new BigDecimal(price));
        product.setStatus(status);
        return product;
    }

    private ProductSku sku(String price) {
        ProductSku sku = new ProductSku();
        sku.setId(SKU_ID);
        sku.setProductId(PRODUCT_ID);
        sku.setSpecValue("大果");
        sku.setSkuImage("/img/sku-490.png");
        sku.setPrice(new BigDecimal(price));
        sku.setStock(50);
        return sku;
    }
}
