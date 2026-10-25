package com.tickcart.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tickcart.common.config.StockProperties;
import com.tickcart.product.entity.Category;
import com.tickcart.product.mapper.CategoryMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 库存预警线解析：把「分类级配置 + 全局兜底」摊平成 {@code categoryId → 有效阈值}。
 *
 * <p><b>取值优先级</b>：分类自身的 {@code stock_warn_threshold} → 沿 {@code parent_id}
 * 上溯最近一个配了值的祖先 → 全局配置 {@code tickcart.stock.warn-threshold}。
 * 商品级/ SKU 级阈值刻意没做：156 个商品、300+ 个 SKU 没人配得动，
 * 加了只会得到一个「字段全空」的表。详见 {@code docs/商品管理界面重设计方案.md} §9。</p>
 *
 * <p><b>为什么不复用 {@code category:list:enabled} 那份 Redis 缓存</b>：那份是前台用的
 * 启用分类列表，后台改完分类后可能还没失效。如果预警线跟着它走，会出现「分类管理页
 * 刚把阈值改成 30，商品列表还是按 10 标红」的一拍延迟 —— 运营会当成 bug。
 * 分类表只有 20 行，每次列表请求多查一次比修这个不一致划算。</p>
 */
@Service
public class StockWarnService {

    private final CategoryMapper categoryMapper;
    private final StockProperties stockProperties;

    public StockWarnService(CategoryMapper categoryMapper, StockProperties stockProperties) {
        this.categoryMapper = categoryMapper;
        this.stockProperties = stockProperties;
    }

    /** 全局兜底阈值（分类上都没配时用它）。 */
    public int globalThreshold() {
        return stockProperties.getWarnThreshold();
    }

    /**
     * 全部分类的有效预警线。
     *
     * <p>一次查询取回 id / parent_id / threshold 三列（20 行量级），
     * 在内存里沿父链解析，避免逐商品回查数据库。</p>
     */
    public Map<Long, Integer> effectiveThresholds() {
        return effectiveThresholds(categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .select(Category::getId, Category::getParentId, Category::getStockWarnThreshold)));
    }

    /**
     * 同上，但复用调用方已经查出来的分类列表。
     *
     * <p>分类总览要同时用到分类实体和阈值，传进来可以省一次 20 行的查询；
     * 关键是**继承规则仍然只有这一份实现**，不因为多一个入口就多一套逻辑。</p>
     */
    public Map<Long, Integer> effectiveThresholds(List<Category> all) {
        Map<Long, Category> byId = new HashMap<>();
        for (Category c : all) {
            byId.put(c.getId(), c);
        }

        int global = globalThreshold();
        Map<Long, Integer> result = new HashMap<>(all.size());
        for (Category c : all) {
            result.put(c.getId(), resolve(c, byId, global));
        }
        return result;
    }

    /**
     * 某分类的有效阈值；分类不存在（如商品挂在已删分类上）时退回全局。
     */
    public int thresholdOf(Long categoryId, Map<Long, Integer> thresholds) {
        if (categoryId == null) {
            return globalThreshold();
        }
        Integer value = thresholds.get(categoryId);
        return value == null ? globalThreshold() : value;
    }

    /**
     * 一组「阈值相同」的分类。
     *
     * <p>用于把 Java 侧算好的阈值翻译成 SQL 条件：{@code category_id IN (ids) AND stock < threshold}。</p>
     */
    public record ThresholdGroup(int threshold, List<Long> categoryIds) {
    }

    /**
     * 按阈值取值归并分类。
     *
     * <p>这是「库存告急筛选」能在一条 SQL 里表达的关键：阈值的<b>取值种类</b>很少
     * （演示数据 4 种），所以 OR 分支数是 4~5 个，而不是 20 个分类各来一条。
     * 纯函数，便于单测。</p>
     */
    public static List<ThresholdGroup> groupByThreshold(Map<Long, Integer> thresholds) {
        Map<Integer, List<Long>> grouped = new LinkedHashMap<>();
        thresholds.forEach((categoryId, threshold) ->
                grouped.computeIfAbsent(threshold, k -> new ArrayList<>()).add(categoryId));
        return grouped.entrySet().stream()
                .map(e -> new ThresholdGroup(e.getKey(), List.copyOf(e.getValue())))
                .toList();
    }

    /**
     * 分类 id → 名称，供补货待办展示。
     *
     * <p>与 {@link #effectiveThresholds()} 各查一次分类表：两件事的查询列不同
     * （一个要 parent_id + 阈值，一个只要名称），为省一次 20 行的查询把它们
     * 硬凑进一个返回值，只会让接口变难懂。</p>
     */
    public Map<Long, String> categoryNameMap() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                        .select(Category::getId, Category::getName))
                .stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }

    /**
     * 库存是否告急（严格小于阈值：库存 10、阈值 10 不算告急）。
     */
    public boolean isLow(Integer stock, Long categoryId, Map<Long, Integer> thresholds) {
        if (stock == null) {
            return false;
        }
        return stock < thresholdOf(categoryId, thresholds);
    }

    /**
     * 沿父链找第一个配了值的分类。
     *
     * <p>{@code seen} 用来兜住脏数据造成的环（A 的父是 B、B 的父是 A）——
     * 没有它这个循环会转到把 CPU 吃光。</p>
     */
    private int resolve(Category self, Map<Long, Category> byId, int global) {
        Set<Long> seen = new HashSet<>();
        Category current = self;
        while (current != null && seen.add(current.getId())) {
            if (current.getStockWarnThreshold() != null) {
                return current.getStockWarnThreshold();
            }
            current = current.getParentId() == null ? null : byId.get(current.getParentId());
        }
        return global;
    }
}
