package com.tickcart.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tickcart.common.api.PageResult;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.dto.FavoriteQuery;
import com.tickcart.product.entity.Product;
import com.tickcart.product.entity.UserFavorite;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.product.mapper.UserFavoriteMapper;
import com.tickcart.product.vo.ProductListItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收藏服务：收藏 / 取消收藏 / 是否已收藏 / 收藏列表。
 *
 * <p>{@code user_favorite} 上有 {@code uk_user_product} 唯一键且**无 deleted 列**：
 * 收藏是幂等的插入，取消收藏是物理删除。</p>
 */
@Service
public class FavoriteService {

    private static final Logger log = LoggerFactory.getLogger(FavoriteService.class);

    private final UserFavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;

    public FavoriteService(UserFavoriteMapper favoriteMapper, ProductMapper productMapper) {
        this.favoriteMapper = favoriteMapper;
        this.productMapper = productMapper;
    }

    /**
     * 收藏商品（幂等）：已收藏时直接返回，重复调用不报错。
     *
     * @throws BizException 商品不存在
     */
    @Transactional
    public void add(Long userId, Long productId) {
        if (productMapper.selectById(productId) == null) {
            throw BizException.notFound("商品不存在");
        }
        if (exists(userId, productId)) {
            return;
        }
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            // 并发下同一用户同时收藏同一商品：唯一键兜底，后到的这次当作已收藏处理
            log.debug("收藏已存在，忽略重复插入：userId={}, productId={}", userId, productId);
        }
    }

    /** 取消收藏（幂等）：没收藏过也视为成功。 */
    @Transactional
    public void remove(Long userId, Long productId) {
        favoriteMapper.delete(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getProductId, productId));
    }

    /** 是否已收藏。 */
    public boolean exists(Long userId, Long productId) {
        return favoriteMapper.selectCount(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getProductId, productId)) > 0;
    }

    /**
     * 收藏列表：按收藏时间倒序（等价于按自增 id 倒序）返回商品。
     *
     * <p>商品若已被删除，会在结果里被过滤掉；分页 {@code total} 仍按收藏记录数统计，
     * 避免「边翻页边消失」导致页码错乱。</p>
     */
    public PageResult<ProductListItem> page(Long userId, FavoriteQuery query) {
        Page<UserFavorite> page = favoriteMapper.selectPage(
                Page.of(query.getPage(), query.getSize()),
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getId));

        List<Long> productIds = page.getRecords().stream()
                .map(UserFavorite::getProductId)
                .toList();
        List<ProductListItem> items = productIds.isEmpty()
                ? List.of()
                : loadProducts(productIds);

        return new PageResult<>(items, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    /** 按收藏顺序批量取商品并转成列表项；已删除的商品自动缺项。 */
    private List<ProductListItem> loadProducts(List<Long> productIds) {
        Map<Long, Product> byId = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return productIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(ProductListItem::from)
                .toList();
    }
}
