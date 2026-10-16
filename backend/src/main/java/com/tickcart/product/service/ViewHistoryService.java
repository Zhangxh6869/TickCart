package com.tickcart.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tickcart.common.api.PageResult;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.dto.ViewHistoryQuery;
import com.tickcart.product.entity.Product;
import com.tickcart.product.entity.UserViewHistory;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.product.mapper.UserViewHistoryMapper;
import com.tickcart.product.vo.ViewHistoryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 浏览记录服务：记录浏览 / 删除单条 / 清空 / 浏览记录列表。
 *
 * <p>与 {@link FavoriteService} 同构（这也是刻意的：两个功能的接口形状、分页口径、
 * 删除语义都保持一致，前端两个页面因此可以共用一套写法）。
 * 差异只有两点：</p>
 * <ul>
 *   <li>记录浏览是 **upsert**（同一商品重复浏览只刷新时间），收藏是「幂等插入」；</li>
 *   <li>列表项多带一个 {@code viewTime}，供前端按「今天 / 昨天 / 日期」分组。</li>
 * </ul>
 *
 * <p>{@code user_view_history} 上有 {@code uk_user_product} 唯一键且**无 deleted 列**：
 * 记录浏览靠唯一键收敛为一行，删除是物理删除。</p>
 */
@Service
public class ViewHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ViewHistoryService.class);

    private final UserViewHistoryMapper viewHistoryMapper;
    private final ProductMapper productMapper;

    public ViewHistoryService(UserViewHistoryMapper viewHistoryMapper, ProductMapper productMapper) {
        this.viewHistoryMapper = viewHistoryMapper;
        this.productMapper = productMapper;
    }

    /**
     * 记录一次浏览（幂等）：同一用户同一商品只保留一条，重复浏览只刷新浏览时间。
     *
     * <p>由商品详情页在加载成功后上报。同一商品来回进出会重复调用，
     * 靠唯一键 + upsert 天然收敛，前端不需要做防抖抑制。</p>
     *
     * @throws BizException 商品不存在（避免脏 productId 落表）
     */
    @Transactional
    public void record(Long userId, Long productId) {
        if (productMapper.selectById(productId) == null) {
            throw BizException.notFound("商品不存在");
        }
        viewHistoryMapper.upsert(userId, productId);
    }

    /** 删除单条记录（幂等）：没浏览过该商品也视为成功。 */
    @Transactional
    public void remove(Long userId, Long productId) {
        viewHistoryMapper.delete(new LambdaQueryWrapper<UserViewHistory>()
                .eq(UserViewHistory::getUserId, userId)
                .eq(UserViewHistory::getProductId, productId));
    }

    /** 清空当前用户的全部浏览记录（幂等）。只删本人，条件里必须带 userId。 */
    @Transactional
    public void clear(Long userId) {
        int rows = viewHistoryMapper.delete(new LambdaQueryWrapper<UserViewHistory>()
                .eq(UserViewHistory::getUserId, userId));
        log.debug("清空浏览记录：userId={}, rows={}", userId, rows);
    }

    /**
     * 浏览记录列表：按最近浏览时间倒序（同一时间再按自增 id 倒序兜底）。
     *
     * <p>商品若已被删除，会在结果里被过滤掉；分页 {@code total} 仍按浏览记录数统计，
     * 避免「边翻页边消失」导致页码错乱。</p>
     */
    public PageResult<ViewHistoryItem> page(Long userId, ViewHistoryQuery query) {
        Page<UserViewHistory> page = viewHistoryMapper.selectPage(
                Page.of(query.getPage(), query.getSize()),
                new LambdaQueryWrapper<UserViewHistory>()
                        .eq(UserViewHistory::getUserId, userId)
                        .orderByDesc(UserViewHistory::getViewTime, UserViewHistory::getId));

        List<ViewHistoryItem> items = toItems(page.getRecords());
        return new PageResult<>(items, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    /** 按浏览顺序批量取商品并组装成列表项；已删除的商品自动缺项。 */
    private List<ViewHistoryItem> toItems(List<UserViewHistory> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = records.stream()
                .map(UserViewHistory::getProductId)
                .toList();
        Map<Long, Product> byId = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return records.stream()
                .filter(record -> byId.containsKey(record.getProductId()))
                .map(record -> ViewHistoryItem.of(byId.get(record.getProductId()), record.getViewTime()))
                .toList();
    }
}
