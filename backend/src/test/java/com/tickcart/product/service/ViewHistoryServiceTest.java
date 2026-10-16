package com.tickcart.product.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tickcart.common.api.ResultCode;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.dto.ViewHistoryQuery;
import com.tickcart.product.entity.Product;
import com.tickcart.product.entity.UserViewHistory;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.product.mapper.UserViewHistoryMapper;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ViewHistoryService} 单元测试。
 *
 * <p>重点：浏览上报是幂等 upsert（不先查后插）、分页按浏览时间倒序并过滤已删除商品、
 * 删除与清空都严格限定在当前用户内。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("浏览记录服务")
class ViewHistoryServiceTest {

    private static final Long UID = 1001L;

    @Mock
    private UserViewHistoryMapper viewHistoryMapper;
    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ViewHistoryService viewHistoryService;

    /**
     * 纯单元测试没有 Spring 容器，而 {@code LambdaQueryWrapper} 解析列名依赖实体的
     * TableInfo 缓存，需先手动初始化，否则报 "can not find lambda cache for this entity"。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserViewHistory.class);
    }

    private Product product(Long id, String name) {
        Product p = new Product();
        p.setId(id);
        p.setCategoryId(1L);
        p.setName(name);
        p.setSubtitle("副标题" + id);
        p.setMainImage("/img/" + id + ".jpg");
        p.setPrice(new BigDecimal("99.00"));
        p.setStock(10);
        p.setSales(3);
        p.setStatus(1);
        p.setIsSeckill(0);
        return p;
    }

    /** 一条浏览记录：id 取 productId*10 保证与商品一一对应，viewTime 依次递减以便断言顺序 */
    private UserViewHistory history(Long productId, int minutesAgo) {
        UserViewHistory h = new UserViewHistory();
        h.setId(productId * 10);
        h.setUserId(UID);
        h.setProductId(productId);
        h.setViewTime(LocalDateTime.of(2026, 10, 16, 12, 0).minusMinutes(minutesAgo));
        return h;
    }

    // ---------------- 记录浏览 ----------------

    @Test
    @DisplayName("记录浏览：走唯一键 upsert，写入当前用户与商品ID")
    void recordUpsertsRow() {
        when(productMapper.selectById(10L)).thenReturn(product(10L, "商品10"));
        when(viewHistoryMapper.upsert(UID, 10L)).thenReturn(1);

        viewHistoryService.record(UID, 10L);

        verify(viewHistoryMapper).upsert(UID, 10L);
    }

    @Test
    @DisplayName("记录浏览：不做「先查后插」，重复上报交给唯一键收敛")
    void recordDoesNotCheckExistenceFirst() {
        when(productMapper.selectById(10L)).thenReturn(product(10L, "商品10"));
        when(viewHistoryMapper.upsert(anyLong(), anyLong())).thenReturn(1);

        // 同一商品连续上报两次：两次都应直接 upsert，不额外查一次存在性
        viewHistoryService.record(UID, 10L);
        viewHistoryService.record(UID, 10L);

        verify(viewHistoryMapper, org.mockito.Mockito.times(2)).upsert(UID, 10L);
        verify(viewHistoryMapper, never()).insert(any(UserViewHistory.class));
    }

    @Test
    @DisplayName("记录浏览：商品不存在时拒绝，不落表")
    void recordRejectsMissingProduct() {
        when(productMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> viewHistoryService.record(UID, 999L));

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.NOT_FOUND);
        verify(viewHistoryMapper, never()).upsert(anyLong(), anyLong());
    }

    // ---------------- 删除单条 ----------------

    @Test
    @DisplayName("删除单条：按用户与商品物理删除")
    void removeDeletesRow() {
        viewHistoryService.remove(UID, 10L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<UserViewHistory>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(viewHistoryMapper).delete(captor.capture());
        // 条件值在 SQL 片段生成时才写入参数表，先实体化再断言
        assertThat(captor.getValue().getSqlSegment()).contains("user_id", "product_id");
        assertThat(captor.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder(UID, 10L);
    }

    @Test
    @DisplayName("删除单条幂等：没浏览过也视为成功")
    void removeIsIdempotent() {
        when(viewHistoryMapper.delete(any())).thenReturn(0);

        viewHistoryService.remove(UID, 10L);

        verify(viewHistoryMapper).delete(any());
    }

    // ---------------- 清空 ----------------

    @Test
    @DisplayName("清空：条件里必须带 userId，只删本人记录")
    void clearDeletesOnlyCurrentUser() {
        when(viewHistoryMapper.delete(any())).thenReturn(3);

        viewHistoryService.clear(UID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<UserViewHistory>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(viewHistoryMapper).delete(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("user_id");
        assertThat(captor.getValue().getParamNameValuePairs().values()).containsExactly(UID);
    }

    // ---------------- 浏览记录列表 ----------------

    @Test
    @DisplayName("列表：按浏览时间倒序返回商品，并过滤已删除商品")
    void pageMapsProductsInViewOrder() {
        // 浏览顺序：11 最近、10 次之；12 的商品已被删除
        when(viewHistoryMapper.selectPage(ArgumentMatchers.<IPage<UserViewHistory>>any(), any()))
                .thenAnswer(inv -> {
                    IPage<UserViewHistory> p = inv.getArgument(0);
                    p.setRecords(List.of(history(11L, 0), history(12L, 5), history(10L, 30)));
                    p.setTotal(3);
                    return p;
                });
        when(productMapper.selectBatchIds(any()))
                .thenReturn(List.of(product(10L, "商品10"), product(11L, "商品11")));

        var result = viewHistoryService.page(UID, new ViewHistoryQuery());

        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0).getId()).isEqualTo(11L);
        assertThat(result.getRecords().get(1).getId()).isEqualTo(10L);
        assertThat(result.getRecords().get(0).getName()).isEqualTo("商品11");
        // total 仍按浏览记录数统计，避免翻页错乱
        assertThat(result.getTotal()).isEqualTo(3);
    }

    @Test
    @DisplayName("列表：每项都带上浏览时间（前端按它做今天/昨天/日期分组）")
    void pageCarriesViewTime() {
        LocalDateTime viewedAt = LocalDateTime.of(2026, 10, 16, 12, 0);
        when(viewHistoryMapper.selectPage(ArgumentMatchers.<IPage<UserViewHistory>>any(), any()))
                .thenAnswer(inv -> {
                    IPage<UserViewHistory> p = inv.getArgument(0);
                    UserViewHistory h = history(10L, 0);
                    h.setViewTime(viewedAt);
                    p.setRecords(List.of(h));
                    p.setTotal(1);
                    return p;
                });
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(product(10L, "商品10")));

        var result = viewHistoryService.page(UID, new ViewHistoryQuery());

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getViewTime()).isEqualTo(viewedAt);
    }

    @Test
    @DisplayName("列表：没有浏览记录时直接返回空，不查商品")
    void pageWithNoRecords() {
        when(viewHistoryMapper.selectPage(ArgumentMatchers.<IPage<UserViewHistory>>any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = viewHistoryService.page(UID, new ViewHistoryQuery());

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
        verify(productMapper, never()).selectBatchIds(any());
    }

    @Test
    @DisplayName("列表：分页参数被规范化（页码下限 1、每页上限 50）")
    void pageClampsPaging() {
        when(viewHistoryMapper.selectPage(ArgumentMatchers.<IPage<UserViewHistory>>any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        ViewHistoryQuery query = new ViewHistoryQuery();
        query.setPage(0);
        query.setSize(999);
        viewHistoryService.page(UID, query);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<IPage<UserViewHistory>> captor = ArgumentCaptor.forClass(IPage.class);
        verify(viewHistoryMapper).selectPage(captor.capture(), any());
        assertThat(captor.getValue().getCurrent()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(50);
    }
}
