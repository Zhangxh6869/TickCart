package com.tickcart.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tickcart.common.api.PageResult;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.dto.ProductQuery;
import com.tickcart.product.dto.ProductSaveRequest;
import com.tickcart.product.entity.Category;
import com.tickcart.product.entity.Product;
import com.tickcart.product.entity.ProductImage;
import com.tickcart.product.entity.ProductSku;
import com.tickcart.product.mapper.CategoryMapper;
import com.tickcart.product.mapper.ProductImageMapper;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.product.mapper.ProductSkuMapper;
import com.tickcart.product.vo.ProductDetail;
import com.tickcart.product.vo.ProductListItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品服务：CRUD、上下架、改库存、列表/详情查询。
 */
@Service
public class ProductService {

    /** 状态：上架 / 下架 */
    private static final int STATUS_ON = 1;
    private static final int STATUS_OFF = 0;

    /** 随机分页时最多参与打乱的商品数，避免数据量变大后一次拉回整表 id。 */
    private static final int RANDOM_POOL_LIMIT = 2000;

    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductImageMapper imageMapper;
    private final CategoryMapper categoryMapper;

    public ProductService(ProductMapper productMapper,
                          ProductSkuMapper skuMapper,
                          ProductImageMapper imageMapper,
                          CategoryMapper categoryMapper) {
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.imageMapper = imageMapper;
        this.categoryMapper = categoryMapper;
    }

    /**
     * 商品分页列表。
     *
     * <p>查询条件带 {@code seed} 时走随机分页（见 {@link #randomPage}），否则按常规排序分页。</p>
     *
     * @param onlyOnSale 前台调用置 true，强制只查上架商品
     */
    public PageResult<ProductListItem> page(ProductQuery query, boolean onlyOnSale) {
        if (query.getSeed() != null) {
            return randomPage(query, onlyOnSale);
        }

        LambdaQueryWrapper<Product> wrapper = filterWrapper(query, onlyOnSale);
        applySort(wrapper, query.getSortBy(), query.getOrder());

        Page<Product> page = productMapper.selectPage(
                Page.of(query.getPage(), query.getSize()), wrapper);

        List<ProductListItem> items = page.getRecords().stream()
                .map(ProductListItem::from)
                .toList();
        return new PageResult<>(items, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    /**
     * 随机分页：同一 seed 下顺序稳定，因此翻页既不重复也不漏项。
     *
     * <p>为什么不在数据库里随机：{@code ORDER BY RAND()} 每页各自随机，翻页必然重复/漏项；
     * {@code ORDER BY RAND(seed)} 虽然带种子，但取值依赖行的读取顺序，执行计划一变顺序就变，
     * 同样不能保证翻页一致。所以这里取回候选 id、在应用层用 seed 做确定性洗牌，再切出当前页。</p>
     *
     * <p>代价说明：会取回符合条件的**全部** id（上限 {@link #RANDOM_POOL_LIMIT}），
     * 适合演示数据量（几十~几千条）。若商品量级上到十万级，应改为落库一个随机权重列并建索引，
     * 用「按权重排序 + 游标翻页」替代本实现。</p>
     */
    private PageResult<ProductListItem> randomPage(ProductQuery query, boolean onlyOnSale) {
        List<Long> ids = productMapper.selectList(
                        filterWrapper(query, onlyOnSale)
                                .select(Product::getId)
                                .orderByAsc(Product::getId)
                                .last("LIMIT " + RANDOM_POOL_LIMIT))
                .stream()
                .map(Product::getId)
                .toList();

        List<Long> shuffled = new ArrayList<>(ids);
        Collections.shuffle(shuffled, new Random(query.getSeed()));

        int size = query.getSize();
        int from = Math.min((query.getPage() - 1) * size, shuffled.size());
        int to = Math.min(from + size, shuffled.size());
        List<Long> window = shuffled.subList(from, to);
        long pages = (shuffled.size() + size - 1L) / size;
        if (window.isEmpty()) {
            return new PageResult<>(List.of(), shuffled.size(), query.getPage(), size, pages);
        }

        // selectBatchIds 不保证返回顺序，按洗牌后的窗口顺序重排，翻页顺序才稳定
        Map<Long, Product> byId = productMapper.selectBatchIds(window).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<ProductListItem> items = window.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(ProductListItem::from)
                .toList();

        return new PageResult<>(items, shuffled.size(), query.getPage(), size, pages);
    }

    /** 组装筛选条件（关键字 / 分类 / 状态），常规分页与随机分页共用。 */
    private LambdaQueryWrapper<Product> filterWrapper(ProductQuery query, boolean onlyOnSale) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .like(StringUtils.hasText(query.getKeyword()), Product::getName, query.getKeyword())
                .eq(query.getCategoryId() != null, Product::getCategoryId, query.getCategoryId());

        if (onlyOnSale) {
            wrapper.eq(Product::getStatus, STATUS_ON);
        } else if (query.getStatus() != null) {
            wrapper.eq(Product::getStatus, query.getStatus());
        }
        return wrapper;
    }

    /** 商品详情（含 SKU、图集、分类名）。 */
    public ProductDetail detail(Long id) {
        Product product = requireProduct(id);
        List<ProductSku> skus = skuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id));
        List<ProductImage> images = imageMapper.selectList(
                new LambdaQueryWrapper<ProductImage>().eq(ProductImage::getProductId, id)
                        .orderByAsc(ProductImage::getSort));
        ProductDetail detail = ProductDetail.from(product, skus, images);
        Category category = categoryMapper.selectById(product.getCategoryId());
        if (category != null) {
            detail.setCategoryName(category.getName());
        }
        return detail;
    }

    /** 新增商品（含 SKU 与图集）。 */
    @Transactional
    public Long create(ProductSaveRequest req) {
        requireCategory(req.getCategoryId());

        Product product = new Product();
        product.setCategoryId(req.getCategoryId());
        product.setName(req.getName());
        product.setSubtitle(req.getSubtitle());
        product.setMainImage(req.getMainImage());
        product.setDetail(req.getDetail());
        product.setPrice(req.getPrice());
        product.setSales(0);
        product.setStatus(req.getStatus() == null ? STATUS_OFF : req.getStatus());
        product.setIsSeckill(req.getIsSeckill() == null ? 0 : req.getIsSeckill());
        // 总库存 = SKU 库存之和（无 SKU 时为 0）
        product.setStock(sumSkuStock(req.getSkus()));
        productMapper.insert(product);

        saveSkus(product.getId(), req.getSkus(), product.getPrice());
        saveImages(product.getId(), req.getImages());
        return product.getId();
    }

    /** 编辑商品。 */
    @Transactional
    public void update(Long id, ProductSaveRequest req) {
        Product product = requireProduct(id);
        requireCategory(req.getCategoryId());

        product.setCategoryId(req.getCategoryId());
        product.setName(req.getName());
        product.setSubtitle(req.getSubtitle());
        product.setMainImage(req.getMainImage());
        product.setDetail(req.getDetail());
        product.setPrice(req.getPrice());
        if (req.getStatus() != null) {
            product.setStatus(req.getStatus());
        }
        if (req.getIsSeckill() != null) {
            product.setIsSeckill(req.getIsSeckill());
        }
        if (req.getSkus() != null) {
            // 全量重建 SKU：先逻辑删除旧的，再插入新的
            skuMapper.delete(new LambdaQueryWrapper<ProductSku>()
                    .eq(ProductSku::getProductId, id));
            saveSkus(id, req.getSkus(), product.getPrice());
            product.setStock(sumSkuStock(req.getSkus()));
        }
        productMapper.updateById(product);

        if (req.getImages() != null) {
            imageMapper.delete(new LambdaQueryWrapper<ProductImage>()
                    .eq(ProductImage::getProductId, id));
            saveImages(id, req.getImages());
        }
    }

    /** 上架/下架。 */
    @Transactional
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != STATUS_ON && status != STATUS_OFF)) {
            throw BizException.badRequest("状态值不合法");
        }
        Product product = requireProduct(id);
        product.setStatus(status);
        productMapper.updateById(product);
    }

    /** 删除商品（连同 SKU、图集）。 */
    @Transactional
    public void delete(Long id) {
        requireProduct(id);
        skuMapper.delete(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id));
        imageMapper.delete(new LambdaQueryWrapper<ProductImage>().eq(ProductImage::getProductId, id));
        productMapper.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // 库存（供 trade/seckill 阶段复用的原子更新入口）
    // -------------------------------------------------------------------------

    /** 直接设置 SKU 库存，并回写商品总库存。 */
    @Transactional
    public void updateSkuStock(Long skuId, Integer stock) {
        if (stock == null || stock < 0) {
            throw BizException.badRequest("库存不能为负");
        }
        ProductSku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw BizException.notFound("SKU 不存在");
        }
        sku.setStock(stock);
        skuMapper.updateById(sku);
        refreshProductStock(sku.getProductId());
    }

    /** 依据 SKU 汇总刷新商品总库存。 */
    private void refreshProductStock(Long productId) {
        List<ProductSku> skus = skuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, productId));
        int total = skus.stream().mapToInt(s -> s.getStock() == null ? 0 : s.getStock()).sum();
        Product product = productMapper.selectById(productId);
        if (product != null) {
            product.setStock(total);
            productMapper.updateById(product);
        }
    }

    // -------------------------------------------------------------------------
    // 内部方法
    // -------------------------------------------------------------------------

    private Product requireProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw BizException.notFound("商品不存在");
        }
        return product;
    }

    private void requireCategory(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw BizException.badRequest("分类不存在");
        }
    }

    private int sumSkuStock(List<ProductSaveRequest.SkuItem> skus) {
        if (skus == null || skus.isEmpty()) {
            return 0;
        }
        return skus.stream().mapToInt(s -> s.getStock() == null ? 0 : s.getStock()).sum();
    }

    private void saveSkus(Long productId, List<ProductSaveRequest.SkuItem> skus, BigDecimal productPrice) {
        if (skus == null || skus.isEmpty()) {
            return;
        }
        for (ProductSaveRequest.SkuItem item : skus) {
            ProductSku sku = new ProductSku();
            sku.setProductId(productId);
            sku.setSpecName(item.getSpecName());
            sku.setSpecValue(item.getSpecValue());
            sku.setSkuImage(item.getSkuImage());
            sku.setPrice(resolveSkuPrice(item, productPrice));
            sku.setStock(item.getStock() == null ? 0 : item.getStock());
            skuMapper.insert(sku);
        }
    }

    /**
     * 计算落库的 SKU 价格。
     *
     * <p>后台新建商品表单会预置一行「规格名 / 规格值 / 价格都为空」的 SKU，开发者往往只填库存，
     * 于是这行会以 price=0 落库；而商城详情页会自动选中第一个有库存的 SKU 并以它的价格成交，
     * 结果是整件商品显示并可按 ¥0.00 下单（商品 6/7 就踩了这个坑）。
     *
     * <p>所以价格未填（&lt;=0）的 SKU 一律继承商品价格 —— 价格没填不等于免费；
     * 规格名为空的行同理，它代表商品默认规格，本就不该有独立价格。
     * 有规格且填了正价的行完全不受影响。
     */
    private BigDecimal resolveSkuPrice(ProductSaveRequest.SkuItem item, BigDecimal productPrice) {
        boolean blankSpec = !StringUtils.hasText(item.getSpecName())
                && !StringUtils.hasText(item.getSpecValue());
        BigDecimal price = item.getPrice();
        if (!blankSpec && price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            return price;
        }
        return productPrice == null ? BigDecimal.ZERO : productPrice;
    }

    private void saveImages(Long productId, List<String> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        int sort = 0;
        for (String url : images) {
            if (!StringUtils.hasText(url)) {
                continue;
            }
            ProductImage image = new ProductImage();
            image.setProductId(productId);
            image.setUrl(url);
            image.setSort(sort++);
            imageMapper.insert(image);
        }
    }

    /** 排序：白名单字段，防 SQL 注入。 */
    private void applySort(LambdaQueryWrapper<Product> wrapper, String sortBy, String order) {
        boolean asc = !"desc".equalsIgnoreCase(order);
        String field = sortBy == null ? "createdAt" : sortBy;
        switch (field) {
            case "price" -> wrapper.orderBy(true, asc, Product::getPrice);
            case "sales" -> wrapper.orderBy(true, asc, Product::getSales);
            default -> wrapper.orderBy(true, asc, Product::getCreatedAt);
        }
        // 主键兜底次序：排序键不唯一时（如批量导入的演示商品 created_at 完全相同），
        // MySQL 不保证两次查询的行序一致，翻页会重复/漏项。补一个唯一键收口。
        wrapper.orderBy(true, asc, Product::getId);
    }
}
