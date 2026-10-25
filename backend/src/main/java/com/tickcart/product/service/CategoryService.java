package com.tickcart.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tickcart.common.cache.CacheKeys;
import com.tickcart.common.cache.CacheProperties;
import com.tickcart.common.cache.CacheService;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.dto.CategorySaveRequest;
import com.tickcart.product.entity.Category;
import com.tickcart.product.mapper.CategoryMapper;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.product.entity.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 分类服务：CRUD。
 */
@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final CacheService cacheService;
    private final CacheProperties cacheProperties;

    public CategoryService(CategoryMapper categoryMapper,
                           ProductMapper productMapper,
                           CacheService cacheService,
                           CacheProperties cacheProperties) {
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
        this.cacheService = cacheService;
        this.cacheProperties = cacheProperties;
    }

    /**
     * 全部分类（按 sort 升序）；enabledOnly=true 时仅返回启用的。
     *
     * <p>前台只要启用的那一份，整表缓存（分类表很小，整表比按 id 缓存更省事）；
     * 后台要看到停用分类，直接查库，不与前台共用缓存 —— 否则后台改完可能看到旧数据。</p>
     */
    public List<Category> list(boolean enabledOnly) {
        if (!enabledOnly) {
            return queryList(false);
        }
        return cacheService.getList(CacheKeys.categoryListEnabled(),
                cacheProperties.getCategoryListTtlSeconds(),
                Category.class,
                () -> queryList(true));
    }

    private List<Category> queryList(boolean enabledOnly) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<Category>()
                .eq(enabledOnly, Category::getStatus, 1)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId);
        return categoryMapper.selectList(wrapper);
    }

    /** 分类详情。 */
    public Category getById(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw BizException.notFound("分类不存在");
        }
        return category;
    }

    /** 新增分类。 */
    @Transactional
    public Category create(CategorySaveRequest req) {
        Category category = new Category();
        category.setParentId(req.getParentId());
        category.setName(req.getName());
        category.setIcon(req.getIcon());
        category.setLevel(resolveLevel(req.getParentId()));
        category.setSort(req.getSort() == null ? 0 : req.getSort());
        category.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        category.setStockWarnThreshold(req.getStockWarnThreshold());
        categoryMapper.insert(category);
        // 分类列表整表缓存，任一分类变动都要整体失效（提交后执行）
        cacheService.evictAfterCommit(CacheKeys.categoryListEnabled());
        return category;
    }

    /** 编辑分类。 */
    @Transactional
    public Category update(Long id, CategorySaveRequest req) {
        Category category = getById(id);
        if (req.getParentId() != null && req.getParentId().equals(id)) {
            throw BizException.badRequest("父分类不能是自身");
        }
        category.setParentId(req.getParentId());
        category.setName(req.getName());
        category.setIcon(req.getIcon());
        category.setLevel(resolveLevel(req.getParentId()));
        if (req.getSort() != null) {
            category.setSort(req.getSort());
        }
        if (req.getStatus() != null) {
            category.setStatus(req.getStatus());
        }
        // ★无条件覆盖，不做 null 保护：null 是「改回继承」的有效意图，
        //   用 if (req.getX() != null) 会让预警线一旦设过就再也清不掉。
        category.setStockWarnThreshold(req.getStockWarnThreshold());
        categoryMapper.updateById(category);
        cacheService.evictAfterCommit(CacheKeys.categoryListEnabled());
        return category;
    }

    /** 删除分类（存在子分类或关联商品时拒绝）。 */
    @Transactional
    public void delete(Long id) {
        getById(id);
        Long childCount = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>().eq(Category::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw BizException.conflict("该分类下存在子分类，无法删除");
        }
        Long productCount = productMapper.selectCount(
                new LambdaQueryWrapper<Product>().eq(Product::getCategoryId, id));
        if (productCount != null && productCount > 0) {
            throw BizException.conflict("该分类下存在商品，无法删除");
        }
        categoryMapper.deleteById(id);
        cacheService.evictAfterCommit(CacheKeys.categoryListEnabled());
    }

    /** 由父分类推导层级：顶级为 1，否则父级 +1。 */
    private int resolveLevel(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return 1;
        }
        Category parent = categoryMapper.selectById(parentId);
        if (parent == null) {
            throw BizException.badRequest("父分类不存在");
        }
        return (parent.getLevel() == null ? 1 : parent.getLevel()) + 1;
    }
}
