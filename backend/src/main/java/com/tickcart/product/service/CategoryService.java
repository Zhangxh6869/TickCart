package com.tickcart.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tickcart.common.cache.CacheKeys;
import com.tickcart.common.cache.CacheProperties;
import com.tickcart.common.cache.CacheService;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.dto.CategorySaveRequest;
import com.tickcart.product.entity.Category;
import com.tickcart.product.mapper.CategoryMapper;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.product.entity.Product;
import com.tickcart.product.vo.CategoryOverview;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 分类服务：CRUD。
 */
@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final CacheService cacheService;
    private final CacheProperties cacheProperties;
    /** 阈值继承规则只在这里实现一份，分类总览复用同一个解析结果 */
    private final StockWarnService stockWarnService;

    public CategoryService(CategoryMapper categoryMapper,
                           ProductMapper productMapper,
                           CacheService cacheService,
                           CacheProperties cacheProperties,
                           StockWarnService stockWarnService) {
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
        this.cacheService = cacheService;
        this.cacheProperties = cacheProperties;
        this.stockWarnService = stockWarnService;
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
        // 新分类追加到末尾：拖拽排序后 sort 是 1..N 的连续值，新建若默认 0 会插到最前面
        category.setSort(req.getSort() == null ? nextSort() : req.getSort());
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

    // -------------------------------------------------------------------------
    // 列表页用的窄接口
    //
    // 为什么不复用 update()：它要求 name / parentId 必填（@NotBlank / @NotNull），
    // 行内改一个阈值却得先把整个对象读出来再整体提交；并发下还可能把别人刚改的
    // 名字覆盖回去。行内操作就该有只改一列的入口。
    // -------------------------------------------------------------------------

    /**
     * 分类总览：分类本身 + 该分类下商品数与告急数。
     *
     * <p>商品数与告急数在内存里算：一次取出全部商品的 {@code (category_id, stock)}
     * （当前 162 行），用 {@link StockWarnService} 的阈值判断是否告急。
     * 不写 GROUP BY + 阈值条件，是因为阈值要按分类继承，SQL 里再实现一遍会分叉。</p>
     */
    public List<CategoryOverview> overview() {
        List<Category> categories = queryList(false);
        if (categories.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> thresholds = stockWarnService.effectiveThresholds(categories);

        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .select(Product::getCategoryId, Product::getStock));
        // [0] = 商品总数，[1] = 告急数
        Map<Long, int[]> counts = new HashMap<>();
        for (Product product : products) {
            int[] bucket = counts.computeIfAbsent(product.getCategoryId(), k -> new int[2]);
            bucket[0]++;
            if (stockWarnService.isLow(product.getStock(), product.getCategoryId(), thresholds)) {
                bucket[1]++;
            }
        }

        return categories.stream().map(category -> {
            CategoryOverview vo = new CategoryOverview();
            vo.setId(category.getId());
            vo.setName(category.getName());
            vo.setParentId(category.getParentId());
            vo.setSort(category.getSort());
            vo.setStatus(category.getStatus());
            vo.setStockWarnThreshold(category.getStockWarnThreshold());
            vo.setEffectiveThreshold(stockWarnService.thresholdOf(category.getId(), thresholds));
            int[] bucket = counts.getOrDefault(category.getId(), new int[2]);
            vo.setProductCount(bucket[0]);
            vo.setLowStockCount(bucket[1]);
            return vo;
        }).toList();
    }

    /**
     * 只改库存预警线；{@code value == null} 表示改回继承。
     *
     * <p>用 {@code LambdaUpdateWrapper.set(...)} 而不是 {@code updateById}：
     * MP 默认策略会把 null 字段从 SET 里剔掉，导致「清空」请求返回成功但库里没变
     * （实测过）。{@code set} 显式写列，null 才会真的落库。</p>
     */
    @Transactional
    public void updateStockThreshold(Long id, Integer value) {
        getById(id);
        if (value != null && value < 0) {
            throw BizException.badRequest("库存预警线不能为负");
        }
        categoryMapper.update(null, new LambdaUpdateWrapper<Category>()
                .eq(Category::getId, id)
                .set(Category::getStockWarnThreshold, value));
        cacheService.evictAfterCommit(CacheKeys.categoryListEnabled());
    }

    /** 只改启用状态。 */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw BizException.badRequest("状态值不合法");
        }
        getById(id);
        categoryMapper.update(null, new LambdaUpdateWrapper<Category>()
                .eq(Category::getId, id)
                .set(Category::getStatus, status));
        cacheService.evictAfterCommit(CacheKeys.categoryListEnabled());
    }

    /**
     * 按传入顺序重写 sort（从 1 开始）。
     *
     * <p>要求传**全部分类**的 id，不接受只传一部分：拖拽时前端拿到的是完整列表，
     * 只重排子集会让未提交的分类 sort 与新区间撞车，前台顺序就乱了。</p>
     */
    @Transactional
    public void reorder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw BizException.badRequest("排序列表不能为空");
        }
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.size() != ids.size()) {
            throw BizException.badRequest("排序列表里有重复的分类");
        }
        Long total = categoryMapper.selectCount(new LambdaQueryWrapper<Category>());
        if (total == null || total != distinct.size()) {
            throw BizException.notFound("分类列表已变化，请刷新后重试");
        }
        for (int i = 0; i < distinct.size(); i++) {
            categoryMapper.update(null, new LambdaUpdateWrapper<Category>()
                    .eq(Category::getId, distinct.get(i))
                    .set(Category::getSort, i + 1));
        }
        cacheService.evictAfterCommit(CacheKeys.categoryListEnabled());
    }

    /**
     * 新分类的排序值 = 当前最大 sort + 1。
     *
     * <p>拖拽排序会把 sort 重写成 1..N 的连续值，如果新建时默认 0，
     * 新分类会跑到最前面 —— 而运营的预期是「加到最后一个」。</p>
     */
    private int nextSort() {
        return queryList(false).stream()
                .map(Category::getSort)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    /** 由父分类推导层级：顶级为 1，否则父级 +1。 */    private int resolveLevel(Long parentId) {
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
