package com.tickcart.product.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tickcart.common.cache.CacheProperties;
import com.tickcart.common.cache.CacheService;
import com.tickcart.common.api.PageResult;
import com.tickcart.common.config.StockProperties;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.dto.ProductQuery;
import com.tickcart.product.dto.ProductSaveRequest;
import com.tickcart.product.entity.Category;
import com.tickcart.product.entity.Product;
import com.tickcart.product.entity.ProductSku;
import com.tickcart.product.mapper.CategoryMapper;
import com.tickcart.product.mapper.ProductImageMapper;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.search.service.SynonymService;
import com.tickcart.product.mapper.ProductSkuMapper;
import com.tickcart.product.vo.LowStockItem;
import com.tickcart.product.vo.LowStockReport;
import com.tickcart.product.vo.ProductDetail;
import com.tickcart.product.vo.ProductListItem;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Spy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProductService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("商品服务")
class ProductServiceTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductSkuMapper skuMapper;
    @Mock
    private ProductImageMapper imageMapper;
    @Mock
    private CategoryMapper categoryMapper;
    /**
     * 同义词服务。默认（未打桩）返回空列表 → {@code ProductService} 回退成「只按原关键词」，
     * 因此下面的关键字测试在语义上等价于「没有同义词」，与引入同义词前一致。
     */
    @Mock
    private SynonymService synonymService;
    @Mock
    private CacheService cacheService;
    /** 用真实配置对象（默认 TTL），不 mock：这些用例不关心 TTL，只关心缓存被绕过 */
    @Spy
    private CacheProperties cacheProperties = new CacheProperties();

    /** 用真实实现 + mock 的 CategoryMapper：库存告急的判定逻辑要真的跑一遍，不能打桩 */
    private StockWarnService stockWarnService;

    /** 手工构造：{@code randomPoolLimit} 是 {@code @Value} 注入的原始类型，@InjectMocks 无法解析 */
    private ProductService productService;

    /**
     * 纯单元测试没有 Spring 容器，{@code LambdaQueryWrapper} 解析列名时会去查实体的
     * TableInfo 缓存；只要断言里调用了 {@code getSqlSegment()} 把条件实体化，
     * 就必须先手动初始化，否则报 "can not find lambda cache"。
     */
    /** 让缓存在单测里透明：get 直接执行 loader，等价于「每次都未命中」。 */
    @BeforeEach
    void stubCacheTransparent() {
        stockWarnService = new StockWarnService(categoryMapper, new StockProperties());
        productService = new ProductService(productMapper, skuMapper, imageMapper, categoryMapper,
                synonymService, cacheService, cacheProperties, stockWarnService, 2000, 2000);
        lenient().when(cacheService.get(anyString(), anyLong(), eq(ProductDetail.class), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
    }

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProductSku.class);
        // 关键字转义的断言要实体化商品表的 wrapper，Product 也要初始化
        TableInfoHelper.initTableInfo(assistant, Product.class);
        // 库存告急标记会走 CategoryMapper 查分类阈值，Category 同样要初始化
        TableInfoHelper.initTableInfo(assistant, Category.class);
    }

    private void categoryExists() {
        when(categoryMapper.selectById(any())).thenReturn(new Category());
    }

    private ProductSaveRequest.SkuItem sku(int stock) {
        ProductSaveRequest.SkuItem item = new ProductSaveRequest.SkuItem();
        item.setSpecName("颜色");
        item.setSpecValue("黑");
        item.setPrice(new BigDecimal("99.00"));
        item.setStock(stock);
        return item;
    }

    private ProductSaveRequest productReq(List<ProductSaveRequest.SkuItem> skus) {
        ProductSaveRequest req = new ProductSaveRequest();
        req.setCategoryId(1L);
        req.setName("测试商品");
        req.setPrice(new BigDecimal("99.00"));
        req.setSkus(skus);
        return req;
    }

    /** 捕获落库的 SKU，供断言价格用。 */
    private List<ProductSku> captureSkus() {
        List<ProductSku> saved = new ArrayList<>();
        when(skuMapper.insert(any(ProductSku.class))).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return 1;
        });
        return saved;
    }

    /** 规格名与规格值都为空的 SKU（后台新建商品表单预置的那一行）。 */
    private ProductSaveRequest.SkuItem blankSku(BigDecimal price, int stock) {
        ProductSaveRequest.SkuItem item = new ProductSaveRequest.SkuItem();
        item.setSpecName("");
        item.setSpecValue("");
        item.setPrice(price);
        item.setStock(stock);
        return item;
    }

    @Test
    @DisplayName("新增商品时总库存 = 各 SKU 库存之和")
    void createSumsSkuStock() {
        categoryExists();
        when(productMapper.insert(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(1L);
            // 断言在插入前已算好总库存
            assertThat(p.getStock()).isEqualTo(30);
            return 1;
        });
        when(skuMapper.insert(any(ProductSku.class))).thenReturn(1);

        productService.create(productReq(List.of(sku(10), sku(20))));
    }

    @Test
    @DisplayName("空壳 SKU（规格为空、价格未填）继承商品价格")
    void blankSkuWithoutPriceInheritsProductPrice() {
        categoryExists();
        when(productMapper.insert(any(Product.class))).thenReturn(1);
        List<ProductSku> saved = captureSkus();

        productService.create(productReq(List.of(blankSku(BigDecimal.ZERO, 4))));

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getPrice()).isEqualByComparingTo("99.00");
    }

    @Test
    @DisplayName("空壳 SKU 里手填的价格被忽略，仍取商品价")
    void blankSkuIgnoresTypedPrice() {
        categoryExists();
        when(productMapper.insert(any(Product.class))).thenReturn(1);
        List<ProductSku> saved = captureSkus();

        productService.create(productReq(List.of(blankSku(new BigDecimal("4.00"), 15))));

        assertThat(saved.get(0).getPrice()).isEqualByComparingTo("99.00");
    }

    @Test
    @DisplayName("有规格的 SKU 保留自己填的价格")
    void namedSkuKeepsOwnPrice() {
        categoryExists();
        when(productMapper.insert(any(Product.class))).thenReturn(1);
        List<ProductSku> saved = captureSkus();

        ProductSaveRequest.SkuItem item = sku(10);
        item.setPrice(new BigDecimal("88.00"));
        productService.create(productReq(List.of(item)));

        assertThat(saved.get(0).getPrice()).isEqualByComparingTo("88.00");
    }

    @Test
    @DisplayName("有规格但价格未填的 SKU 也继承商品价，不会变成 0 元")
    void namedSkuWithoutPriceInheritsProductPrice() {
        categoryExists();
        when(productMapper.insert(any(Product.class))).thenReturn(1);
        List<ProductSku> saved = captureSkus();

        ProductSaveRequest.SkuItem item = sku(10);
        item.setPrice(BigDecimal.ZERO);
        productService.create(productReq(List.of(item)));

        assertThat(saved.get(0).getPrice()).isEqualByComparingTo("99.00");
    }

    @Test
    @DisplayName("编辑商品时新加的 SKU 同样继承商品价")
    void updateNewSkuInheritsProductPrice() {
        Product product = new Product();
        product.setId(1L);
        product.setPrice(new BigDecimal("99.00"));
        when(productMapper.selectById(1L)).thenReturn(product);
        categoryExists();
        List<ProductSku> saved = captureSkus();

        productService.update(1L, productReq(List.of(blankSku(BigDecimal.ZERO, 4))));

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getPrice()).isEqualByComparingTo("99.00");
    }

    @Test
    @DisplayName("新增商品：销量取请求里的初始值")
    void createUsesRequestedSales() {
        categoryExists();
        ProductSaveRequest req = productReq(List.of(sku(10)));
        req.setSales(128);
        when(productMapper.insert(any(Product.class))).thenAnswer(inv -> {
            assertThat(((Product) inv.getArgument(0)).getSales()).isEqualTo(128);
            return 1;
        });
        when(skuMapper.insert(any(ProductSku.class))).thenReturn(1);

        productService.create(req);
    }

    @Test
    @DisplayName("新增商品：不传销量时默认 0")
    void createDefaultsSalesToZero() {
        categoryExists();
        when(productMapper.insert(any(Product.class))).thenAnswer(inv -> {
            assertThat(((Product) inv.getArgument(0)).getSales()).isZero();
            return 1;
        });
        when(skuMapper.insert(any(ProductSku.class))).thenReturn(1);

        productService.create(productReq(List.of(sku(10))));
    }

    @Test
    @DisplayName("编辑商品：传销量则覆盖为绝对值")
    void updateAppliesRequestedSales() {
        Product product = new Product();
        product.setId(1L);
        product.setPrice(new BigDecimal("99.00"));
        product.setSales(10);
        when(productMapper.selectById(1L)).thenReturn(product);
        categoryExists();
        ProductSaveRequest req = productReq(null);
        req.setSales(50);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        productService.update(1L, req);

        assertThat(product.getSales()).isEqualTo(50);
    }

    @Test
    @DisplayName("编辑商品：不传销量时保留原值，不被清零")
    void updateKeepsSalesWhenAbsent() {
        Product product = new Product();
        product.setId(1L);
        product.setPrice(new BigDecimal("99.00"));
        product.setSales(10);
        when(productMapper.selectById(1L)).thenReturn(product);
        categoryExists();
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        productService.update(1L, productReq(null));

        assertThat(product.getSales()).isEqualTo(10);
    }

    @Test
    @DisplayName("分类不存在时拒绝新增")
    void createWithMissingCategory() {
        when(categoryMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> productService.create(productReq(List.of(sku(10)))))
                .isInstanceOf(BizException.class)
                .hasMessage("分类不存在");
    }

    @Test
    @DisplayName("商品不存在时详情抛 404")
    void detailMissing() {
        when(productMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> productService.detail(9L))
                .isInstanceOf(BizException.class)
                .hasMessage("商品不存在");
    }

    @Test
    @DisplayName("详情聚合 SKU、图集与分类名")
    void detailAggregates() {
        Product p = new Product();
        p.setId(1L);
        p.setCategoryId(1L);
        p.setName("TickPhone");
        when(productMapper.selectById(1L)).thenReturn(p);

        ProductSku s = new ProductSku();
        s.setId(1L);
        s.setProductId(1L);
        when(skuMapper.selectList(any())).thenReturn(List.of(s));
        when(imageMapper.selectList(any())).thenReturn(List.of());

        Category c = new Category();
        c.setId(1L);
        c.setName("数码电子");
        when(categoryMapper.selectById(1L)).thenReturn(c);

        ProductDetail detail = productService.detail(1L);

        assertThat(detail.getSkus()).hasSize(1);
        assertThat(detail.getCategoryName()).isEqualTo("数码电子");
    }

    @Test
    @DisplayName("详情库存现读：缓存里的旧库存被数据库覆盖")
    void detailStockIsReadFresh() {
        Product cachedProduct = new Product();
        cachedProduct.setId(1L);
        cachedProduct.setStock(99);
        ProductSku cachedSku = new ProductSku();
        cachedSku.setId(11L);
        cachedSku.setProductId(1L);
        cachedSku.setStock(99);
        // 缓存命中：返回一份 60 秒前的快照（库存 99）
        when(cacheService.get(anyString(), anyLong(), eq(ProductDetail.class), any()))
                .thenReturn(ProductDetail.from(cachedProduct, List.of(cachedSku), List.of()));

        // 数据库里已经卖掉 94 件
        ProductSku freshSku = new ProductSku();
        freshSku.setId(11L);
        freshSku.setStock(5);
        when(skuMapper.selectList(any())).thenReturn(List.of(freshSku));

        ProductDetail detail = productService.detail(1L);

        assertThat(detail.getSkus().get(0).getStock()).isEqualTo(5);
        // 商品汇总是 SKU 合计，同样不能是缓存里的 99
        assertThat(detail.getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("无规格商品的详情库存也现读商品表")
    void detailStockIsFreshWithoutSku() {
        Product cachedProduct = new Product();
        cachedProduct.setId(2L);
        cachedProduct.setStock(99);
        when(cacheService.get(anyString(), anyLong(), eq(ProductDetail.class), any()))
                .thenReturn(ProductDetail.from(cachedProduct, List.of(), List.of()));

        Product freshProduct = new Product();
        freshProduct.setStock(3);
        when(productMapper.selectOne(any())).thenReturn(freshProduct);

        ProductDetail detail = productService.detail(2L);

        assertThat(detail.getStock()).isEqualTo(3);
    }

    @Test
    @DisplayName("进缓存的那一份不带库存，避免缓存里留着会过期的数字")
    void cachedDetailCarriesNoStock() {
        Product p = new Product();
        p.setId(1L);
        p.setCategoryId(1L);
        p.setStock(99);
        when(productMapper.selectById(1L)).thenReturn(p);
        ProductSku s = new ProductSku();
        s.setId(1L);
        s.setProductId(1L);
        s.setStock(99);
        when(skuMapper.selectList(any())).thenReturn(List.of(s));
        when(imageMapper.selectList(any())).thenReturn(List.of());

        when(cacheService.get(anyString(), anyLong(), eq(ProductDetail.class), any()))
                .thenAnswer(inv -> {
                    ProductDetail toCache = ((Supplier<ProductDetail>) inv.getArgument(3)).get();
                    assertThat(toCache.getStock()).isZero();
                    assertThat(toCache.getSkus()).allSatisfy(sku -> assertThat(sku.getStock()).isZero());
                    return toCache;
                });

        productService.detail(1L);
    }

    @Test
    @DisplayName("非法状态值被拒绝")
    void changeStatusInvalid() {
        assertThatThrownBy(() -> productService.changeStatus(1L, 5))
                .isInstanceOf(BizException.class)
                .hasMessage("状态值不合法");
    }

    @Test
    @DisplayName("修改 SKU 库存为负数被拒绝")
    void updateSkuStockNegative() {
        assertThatThrownBy(() -> productService.updateSkuStock(1L, -1))
                .isInstanceOf(BizException.class)
                .hasMessage("库存不能为负");
    }

    @Test
    @DisplayName("修改 SKU 库存后回写商品总库存")
    void updateSkuStockRefreshesProduct() {
        ProductSku sku = new ProductSku();
        sku.setId(1L);
        sku.setProductId(1L);
        sku.setStock(5);
        when(skuMapper.selectById(1L)).thenReturn(sku);

        ProductSku other = new ProductSku();
        other.setProductId(1L);
        other.setStock(8);
        when(skuMapper.selectList(any())).thenReturn(List.of(sku, other));

        Product product = new Product();
        product.setId(1L);
        when(productMapper.selectById(1L)).thenReturn(product);

        productService.updateSkuStock(1L, 10);

        assertThat(product.getStock()).isEqualTo(18); // 10 + 8
    }

    @Test
    @DisplayName("查询条件越界时页码与每页数被纠正")
    void queryBounds() {
        ProductQuery query = new ProductQuery();
        query.setPage(0);
        query.setSize(1000);

        assertThat(query.getPage()).isEqualTo(1);
        assertThat(query.getSize()).isEqualTo(100);
    }

    // ---------------- 库存告急标记 ----------------

    private Category category(Long id, Long parentId, Integer threshold) {
        return category(id, parentId, threshold, "分类" + id);
    }

    private Category category(Long id, Long parentId, Integer threshold, String name) {
        Category c = new Category();
        c.setId(id);
        c.setParentId(parentId);
        c.setStockWarnThreshold(threshold);
        c.setName(name);
        return c;
    }

    private Product product(long id, long categoryId, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setCategoryId(categoryId);
        p.setName("商品" + id);
        p.setPrice(new BigDecimal("99.00"));
        p.setStock(stock);
        p.setSales(0);
        p.setStatus(1);
        p.setIsSeckill(0);
        return p;
    }

    @Test
    @DisplayName("列表按分类的有效预警线标记库存告急")
    void listMarksStockLowByCategoryThreshold() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                category(1L, 0L, null),   // 没配 → 继承全局 10
                category(5L, 0L, 8)));    // 自身 8

        Page<Product> page = new Page<>(1, 12);
        page.setRecords(List.of(
                product(101L, 1L, 9),    // 9 < 10 告急
                product(102L, 1L, 10),   // 等于阈值不算告急
                product(103L, 5L, 9),    // 9 > 8 不告急
                product(104L, 5L, 7)));  // 7 < 8 告急
        page.setTotal(4);
        when(productMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<ProductListItem> result = productService.page(new ProductQuery(), false);

        assertThat(result.getRecords())
                .extracting(ProductListItem::getId, ProductListItem::getStockLow)
                .containsExactly(
                        tuple(101L, true),
                        tuple(102L, false),
                        tuple(103L, false),
                        tuple(104L, true));
    }

    // ---------------- 批量操作 ----------------

    /** 让 normalizeBatchIds 的存在性校验通过：返回 count 条。 */
    private void productsExist(int count) {
        when(productMapper.selectCount(any())).thenReturn((long) count);
    }

    @Test
    @DisplayName("批量上下架：一次 UPDATE 覆盖全部选中商品")
    void batchChangeStatusUpdatesInOneStatement() {
        productsExist(2);
        when(productMapper.update(any(), any())).thenReturn(2);

        int affected = productService.batchChangeStatus(List.of(1L, 2L), 0);

        assertThat(affected).isEqualTo(2);
        ArgumentCaptor<Product> patch = ArgumentCaptor.forClass(Product.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Product>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productMapper).update(patch.capture(), wrapper.capture());
        assertThat(patch.getValue().getStatus()).isEqualTo(0);
        assertThat(wrapper.getValue().getSqlSegment()).contains("IN");
    }

    @Test
    @DisplayName("批量改分类只写 category_id，不重建 SKU")
    void batchChangeCategoryTouchesOnlyCategoryColumn() {
        categoryExists();
        productsExist(2);
        when(productMapper.update(any(), any())).thenReturn(2);

        productService.batchChangeCategory(List.of(1L, 2L), 5L);

        ArgumentCaptor<Product> patch = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).update(patch.capture(), any());
        assertThat(patch.getValue().getCategoryId()).isEqualTo(5L);
        // 其余字段必须为 null，否则 MyBatis-Plus 会把这些列一起写进 UPDATE，
        // 等于走了一次全量覆盖
        assertThat(patch.getValue().getName()).isNull();
        assertThat(patch.getValue().getPrice()).isNull();
        assertThat(patch.getValue().getStock()).isNull();
        // 关键：SKU 与图集一行都不能动 —— 动了 product_sku.id 就会变，
        // seckill_activity.sku_id 的引用会全部失联
        verify(skuMapper, never()).delete(any());
        verify(imageMapper, never()).delete(any());
    }

    @Test
    @DisplayName("批量删除级联删除 SKU 与图集")
    void batchDeleteCascadesToSkuAndImages() {
        productsExist(2);
        when(productMapper.delete(any())).thenReturn(2);

        int affected = productService.batchDelete(List.of(1L, 2L));

        assertThat(affected).isEqualTo(2);
        verify(skuMapper).delete(any());
        verify(imageMapper).delete(any());
        verify(productMapper).delete(any());
    }

    @Test
    @DisplayName("选中项里有已删除的商品时整批取消，不做半成功")
    void batchRejectsPartiallyMissingIds() {
        when(productMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> productService.batchChangeStatus(List.of(1L, 2L), 0))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已取消");
        verify(productMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("空选择直接拒绝")
    void batchRejectsEmptySelection() {
        assertThatThrownBy(() -> productService.batchDelete(List.of()))
                .isInstanceOf(BizException.class)
                .hasMessage("请先选择商品");
    }

    @Test
    @DisplayName("单次超过 100 条被拒绝")
    void batchRejectsTooManyIds() {
        List<Long> ids = IntStream.rangeClosed(1, 101).mapToObj(Long::valueOf).toList();

        assertThatThrownBy(() -> productService.batchDelete(ids))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("单次最多处理 100");
    }

    @Test
    @DisplayName("重复 id 先去重再校验，不会误判为缺失")
    void batchDedupesIds() {
        productsExist(2);
        when(productMapper.update(any(), any())).thenReturn(2);

        productService.batchChangeStatus(List.of(1L, 2L, 1L), 1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Product>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productMapper).update(any(), wrapper.capture());
        // ★必须先 getSqlSegment() 把条件实体化，参数表才是填充的（MP 是懒解析）
        assertThat(wrapper.getValue().getSqlSegment()).contains("IN");
        assertThat(wrapper.getValue().getParamNameValuePairs()).hasSize(2);
    }

    @Test
    @DisplayName("批量上下架的状态值不合法时拒绝")
    void batchRejectsInvalidStatus() {
        assertThatThrownBy(() -> productService.batchChangeStatus(List.of(1L), 2))
                .isInstanceOf(BizException.class)
                .hasMessage("状态值不合法");
    }

    // ---------------- 补货待办 ----------------

    private ProductSku sku(long id, long productId, String specValue, int stock) {
        ProductSku s = new ProductSku();
        s.setId(id);
        s.setProductId(productId);
        s.setSpecName("尺码");
        s.setSpecValue(specValue);
        s.setStock(stock);
        s.setPrice(new BigDecimal("99.00"));
        return s;
    }

    private void stubLowStockSource(List<Product> products, List<ProductSku> skus,
                                    List<Category> categories) {
        when(productMapper.selectList(any())).thenReturn(products);
        when(skuMapper.selectList(any())).thenReturn(skus);
        when(categoryMapper.selectList(any())).thenReturn(categories);
    }

    @Test
    @DisplayName("补货待办：总库存充足但某个 SKU 断货，也要出现在清单里")
    void lowStockIsSkuLevel() {
        // 商品总库存 120，远高于阈值 8，但 43 码是 0 —— 商品级判断完全看不出问题
        stubLowStockSource(
                List.of(product(126L, 5L, 120)),
                List.of(sku(1L, 126L, "42", 120), sku(2L, 126L, "43", 0)),
                List.of(category(5L, 0L, 8, "鞋类")));

        LowStockReport report = productService.lowStock(100);

        assertThat(report.getTotal()).isEqualTo(1);
        assertThat(report.getItems()).singleElement()
                .satisfies(it -> {
                    assertThat(it.getSkuId()).isEqualTo(2L);
                    assertThat(it.getSpecValue()).isEqualTo("43");
                    assertThat(it.getStock()).isZero();
                    assertThat(it.getThreshold()).isEqualTo(8);
                    assertThat(it.getShortage()).isEqualTo(8);
                    assertThat(it.getRemainRatio()).isZero();
                    assertThat(it.getCategoryName()).isEqualTo("鞋类");
                });
    }

    @Test
    @DisplayName("补货待办：按剩余比例升序，断货排最前（不是按缺口绝对值）")
    void lowStockSortsByRemainRatio() {
        // 生鲜阈值 30 剩 5（缺 25，比例 0.17） vs 家电阈值 3 剩 0（缺 3，比例 0）
        // 按缺口绝对值会误把生鲜排前面，但断货的家电更急
        stubLowStockSource(
                List.of(product(1L, 23L, 5), product(2L, 16L, 0)),
                List.of(sku(11L, 1L, "标准", 5), sku(21L, 2L, "标准", 0)),
                List.of(category(23L, 0L, 30), category(16L, 0L, 3)));

        LowStockReport report = productService.lowStock(100);

        assertThat(report.getItems())
                .extracting(LowStockItem::getProductId)
                .containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("补货待办：total 是全库数量，不受 limit 截断")
    void lowStockTotalIgnoresLimit() {
        stubLowStockSource(
                List.of(product(1L, 5L, 0), product(2L, 5L, 0), product(3L, 5L, 0)),
                List.of(sku(11L, 1L, "A", 0), sku(21L, 2L, "A", 0), sku(31L, 3L, "A", 0)),
                List.of(category(5L, 0L, 8)));

        LowStockReport report = productService.lowStock(2);

        assertThat(report.getTotal()).isEqualTo(3);
        assertThat(report.getItems()).hasSize(2);
        assertThat(report.isTruncated()).isTrue();
        // 断货数是全库统计，不能只数被 limit 截断后的 items
        assertThat(report.getOutOfStock()).isEqualTo(3);
    }

    @Test
    @DisplayName("补货待办：预警线配成 0 表示该分类不告警，不是全部告急")
    void lowStockSkipsZeroThreshold() {
        stubLowStockSource(
                List.of(product(1L, 5L, 0)),
                List.of(sku(11L, 1L, "A", 0)),
                List.of(category(5L, 0L, 0)));

        LowStockReport report = productService.lowStock(100);

        assertThat(report.getTotal()).isZero();
        assertThat(report.getItems()).isEmpty();
    }

    @Test
    @DisplayName("补货待办：没有在售商品时返回空清单，不再查 SKU")
    void lowStockEmptyWhenNoOnSaleProduct() {
        when(productMapper.selectList(any())).thenReturn(List.of());

        LowStockReport report = productService.lowStock(100);

        assertThat(report.getTotal()).isZero();
        assertThat(report.isTruncated()).isFalse();
        verify(skuMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("补货待办：只查在售商品，且带扫描上限")
    void lowStockQueryOnlyOnSale() {
        when(productMapper.selectList(any())).thenReturn(List.of());

        productService.lowStock(100);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Product>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("status").contains("LIMIT");
    }

    // ---------------- 库存告急筛选 ----------------

    @Test
    @DisplayName("库存告急筛选：按阈值取值归并成 OR 分支，不是每个分类一条")
    void stockLowFilterBuildsGroupedBranches() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                category(1L, 0L, null),   // 继承全局 10
                category(6L, 0L, null),   // 继承全局 10（与 1 合并成一组）
                category(5L, 0L, 8),
                category(23L, 0L, 30)));
        when(productMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 12));

        ProductQuery query = new ProductQuery();
        query.setStockLow(true);
        productService.page(query, false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Product>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("stock <").contains("OR");
        // 兜底分支：挂在已删分类上的商品按全局阈值判断，否则会被整个漏掉
        assertThat(sql).contains("NOT IN");
    }

    @Test
    @DisplayName("库存告急筛选对前台不生效（避免暴露动销）")
    void stockLowFilterIgnoredForMall() {
        when(productMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 12));

        ProductQuery query = new ProductQuery();
        query.setStockLow(true);
        // onlyOnSale = true 即前台调用
        productService.page(query, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Product>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productMapper).selectPage(any(), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).doesNotContain("stock <");
    }

    // ---------------- 规格判断（加购 / 下单共用） ----------------

    @Test
    @DisplayName("存在 spec_value 非空的 SKU 时视为配置了真实规格")
    void hasRealSpecWhenSpecValuePresent() {
        when(skuMapper.selectCount(any())).thenReturn(2L);

        assertThat(productService.hasRealSpec(10L)).isTrue();
    }

    @Test
    @DisplayName("只有 spec_value 为空的占位 SKU 时不算配置规格")
    void hasRealSpecIgnoresPlaceholderSku() {
        // 种子数据里无规格商品也带一条 spec_value 为空的占位 SKU，
        // 查询条件把它排除掉，所以这里匹配不到任何行
        when(skuMapper.selectCount(any())).thenReturn(0L);

        assertThat(productService.hasRealSpec(10L)).isFalse();
    }

    @Test
    @DisplayName("规格判断的查询条件排除空 spec_value")
    void hasRealSpecQueryExcludesBlankSpecValue() {
        when(skuMapper.selectCount(any())).thenReturn(1L);

        productService.hasRealSpec(10L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ProductSku>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(skuMapper).selectCount(captor.capture());
        assertThat(captor.getValue().getSqlSegment())
                .contains("spec_value")
                .contains("IS NOT NULL")
                .contains("<>");
    }

    // -------------------------------------------------------------------------
    // 关键字搜索：通配符转义
    // -------------------------------------------------------------------------

    /** 跑一次列表查询，返回真正传给 mapper 的 wrapper */
    private LambdaQueryWrapper<Product> captureListWrapper(String keyword) {
        when(productMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));

        ProductQuery query = new ProductQuery();
        query.setKeyword(keyword);
        productService.page(query, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Product>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productMapper).selectPage(any(), captor.capture());
        return captor.getValue();
    }

    /**
     * 取 wrapper 里绑定的参数值。
     *
     * <p>★ 必须先调一次 {@code getSqlSegment()} 把条件实体化：{@code apply(...)} 的参数是
     * **惰性**写入 {@code paramNameValuePairs} 的（内部存的是个 lambda，只有真正拼 SQL 时
     * 才格式化并登记参数）。少了这一步，读到的永远是空表，测试会假失败。</p>
     *
     * <p>结果里除关键字外还会带上 {@code status} 条件绑定的 1（前台固定只查上架），
     * 所以断言用 {@code contains} 而不是 {@code containsExactly}。</p>
     */
    private List<Object> capturedParams(LambdaQueryWrapper<Product> wrapper) {
        wrapper.getSqlSegment();
        return new ArrayList<>(wrapper.getParamNameValuePairs().values());
    }

    @Test
    @DisplayName("关键字走 LIKE 且显式声明转义字符")
    void keywordUsesLikeWithEscape() {
        LambdaQueryWrapper<Product> wrapper = captureListWrapper("手机");

        assertThat(wrapper.getSqlSegment())
                .contains("name LIKE")
                .contains("ESCAPE '!'");
        assertThat(capturedParams(wrapper)).contains("%手机%");
    }

    @Test
    @DisplayName("商品名、副标题、搜索关键词三个字段都参与匹配")
    void subtitleAndSearchKeywordsAreAlsoSearched() {
        LambdaQueryWrapper<Product> wrapper = captureListWrapper("亲肤");

        assertThat(wrapper.getSqlSegment())
                .contains("name LIKE")
                .contains("subtitle LIKE")
                .contains("search_keywords LIKE")
                // 三个字段都要显式 ESCAPE，别只给 name 加
                .contains("ESCAPE '!'");
        assertThat(wrapper.getSqlSegment()).contains(" OR ");

        // 三个字段共用同一份 pattern（三个占位符，值相同）
        assertThat(capturedParams(wrapper).stream().filter("%亲肤%"::equals).count())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("副标题与搜索关键词同样受通配符转义保护")
    void subtitleAndKeywordsAreAlsoEscaped() {
        LambdaQueryWrapper<Product> wrapper = captureListWrapper("%");

        // name / subtitle / search_keywords 三个占位符都必须是转义后的 pattern，不能有一个漏掉
        assertThat(capturedParams(wrapper).stream().filter("%!%%"::equals).count())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("品类词召回：搜索关键词列让「裤子」这类词能命中商品")
    void searchKeywordsColumnParticipatesInRecall() {
        // 「裤子」在库里没有任何商品名/副标题含这两个字（只有「高腰直筒牛仔裤」），
        // 全靠 product.search_keywords 里人工维护的「裤子」召回（见 V15 迁移）
        LambdaQueryWrapper<Product> wrapper = captureListWrapper("裤子");

        assertThat(wrapper.getSqlSegment()).contains("search_keywords LIKE");
        assertThat(capturedParams(wrapper)).contains("%裤子%");
    }

    @Test
    @DisplayName("关键字里的 % 被转义，不再当成通配符")
    void keywordEscapesPercent() {
        // 不转义时 keyword=% 会命中全库，这是修复前实测到的行为
        LambdaQueryWrapper<Product> wrapper = captureListWrapper("%");

        assertThat(capturedParams(wrapper)).contains("%!%%");
    }

    @Test
    @DisplayName("关键字里的 _ 被转义，不再当成单字符通配符")
    void keywordEscapesUnderscore() {
        LambdaQueryWrapper<Product> wrapper = captureListWrapper("a_b");

        assertThat(capturedParams(wrapper)).contains("%a!_b%");
    }

    @Test
    @DisplayName("转义字符自身也被转义，防止绕过")
    void keywordEscapesEscapeCharItself() {
        // 输入 "!%" 若不转义 ! 本身，会被解析成「转义符 + 通配符」= 字面量 %，
        // 用户就又能用 !% 注入通配符了
        LambdaQueryWrapper<Product> wrapper = captureListWrapper("!%");

        assertThat(capturedParams(wrapper)).contains("%!!!%%");
    }

    @Test
    @DisplayName("关键字两侧空白被裁掉")
    void keywordIsTrimmed() {
        LambdaQueryWrapper<Product> wrapper = captureListWrapper("  手机  ");

        assertThat(capturedParams(wrapper)).contains("%手机%");
    }

    @Test
    @DisplayName("空白关键字不产生 LIKE 条件")
    void blankKeywordProducesNoLikeCondition() {
        LambdaQueryWrapper<Product> wrapper = captureListWrapper("   ");

        assertThat(wrapper.getSqlSegment()).doesNotContain("LIKE");
        assertThat(capturedParams(wrapper)).noneMatch(String.class::isInstance);
    }

    // -------------------------------------------------------------------------
    // 关键字搜索：同义词展开
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("命中同义词时，连同义词一起召回")
    void synonymExpandsKeyword() {
        when(synonymService.expand("苹果手机")).thenReturn(List.of("苹果手机", "iPhone"));

        LambdaQueryWrapper<Product> wrapper = captureListWrapper("苹果手机");

        // 两个展开词 → 两段 (name LIKE … OR subtitle LIKE …)
        assertThat(wrapper.getSqlSegment()).contains("name LIKE").contains("subtitle LIKE");
        assertThat(capturedParams(wrapper)).contains("%苹果手机%").contains("%iPhone%");
    }

    @Test
    @DisplayName("无同义词时只按原关键词匹配（与引入同义词前一致）")
    void noSynonymKeepsOriginalBehaviour() {
        when(synonymService.expand("手机")).thenReturn(List.of("手机"));

        LambdaQueryWrapper<Product> wrapper = captureListWrapper("手机");

        assertThat(capturedParams(wrapper).stream().filter("%手机%"::equals).count()).isEqualTo(3);
        assertThat(capturedParams(wrapper)).noneMatch("%iPhone%"::equals);
    }

    // -------------------------------------------------------------------------
    // 搜索关键词（product.search_keywords）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("新增商品：搜索关键词落库")
    void createSavesSearchKeywords() {
        categoryExists();
        ProductSaveRequest req = productReq(List.of(sku(10)));
        req.setSearchKeywords("手机,智能手机,苹果手机");
        when(productMapper.insert(any(Product.class))).thenAnswer(inv -> {
            assertThat(((Product) inv.getArgument(0)).getSearchKeywords()).isEqualTo("手机,智能手机,苹果手机");
            return 1;
        });
        when(skuMapper.insert(any(ProductSku.class))).thenReturn(1);

        productService.create(req);
    }

    @Test
    @DisplayName("编辑商品：搜索关键词覆盖为绝对值")
    void updateAppliesSearchKeywords() {
        Product product = new Product();
        product.setId(1L);
        product.setPrice(new BigDecimal("99.00"));
        product.setSearchKeywords("旧词");
        when(productMapper.selectById(1L)).thenReturn(product);
        categoryExists();
        ProductSaveRequest req = productReq(null);
        req.setSearchKeywords("裤子,长裤");
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        productService.update(1L, req);

        assertThat(product.getSearchKeywords()).isEqualTo("裤子,长裤");
    }

    @Test
    @DisplayName("详情返回搜索关键词（后台编辑表单要回填）")
    void detailReturnsSearchKeywords() {
        Product p = new Product();
        p.setId(1L);
        p.setCategoryId(1L);
        p.setName("高腰直筒牛仔裤");
        p.setSearchKeywords("衣服,服装,服饰,裤子,长裤");
        when(productMapper.selectById(1L)).thenReturn(p);
        when(skuMapper.selectList(any())).thenReturn(List.of());
        when(imageMapper.selectList(any())).thenReturn(List.of());

        ProductDetail detail = productService.detail(1L);

        assertThat(detail.getSearchKeywords()).isEqualTo("衣服,服装,服饰,裤子,长裤");
    }

    // -------------------------------------------------------------------------
    // SKU 增量同步：编辑商品不再重建 SKU
    //
    // 回归的是这个真实事故：后台「保存商品」原本是「旧 SKU 全删 + 新 SKU 重插」，
    // SKU id 因此全变；而未完成订单的 order_item.sku_id 还指着旧 id，
    // 取消订单回补库存时 UPDATE 被自动补上 deleted=0 → 命中 0 行 → 库存静默丢失。
    // -------------------------------------------------------------------------

    /** 构造一条已存在的 SKU 行（模拟库里上一代数据）。 */
    private ProductSku storedSku(Long id, int stock) {
        ProductSku sku = new ProductSku();
        sku.setId(id);
        sku.setProductId(1L);
        sku.setSpecName("颜色");
        sku.setSpecValue("黑");
        sku.setPrice(new BigDecimal("99.00"));
        sku.setStock(stock);
        return sku;
    }

    /** 让 update 走到「商品已存在 + 分类已存在」这两个前置校验之后。 */
    private void stubExistingProduct() {
        Product product = new Product();
        product.setId(1L);
        product.setPrice(new BigDecimal("99.00"));
        when(productMapper.selectById(1L)).thenReturn(product);
        categoryExists();
        when(productMapper.updateById(any(Product.class))).thenReturn(1);
    }

    @Test
    @DisplayName("编辑商品：带 id 的 SKU 就地更新，不删不重插（id 保持不变）")
    void updateSkusInPlace() {
        stubExistingProduct();
        when(skuMapper.selectList(any())).thenReturn(List.of(storedSku(11L, 100)));

        ProductSaveRequest.SkuItem item = sku(80);
        item.setId(11L);
        productService.update(1L, productReq(List.of(item)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<ProductSku>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(skuMapper).update(isNull(), captor.capture());
        verify(skuMapper, never()).insert(any(ProductSku.class));
        verify(skuMapper, never()).delete(any());

        captor.getValue().getSqlSegment(); // 先实体化占位符，再断言参数
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains(11L, 80);
    }

    @Test
    @DisplayName("编辑商品：请求里没带的旧 SKU 才被逻辑删除")
    void updateDeletesRemovedSku() {
        stubExistingProduct();
        when(skuMapper.selectList(any()))
                .thenReturn(List.of(storedSku(11L, 100), storedSku(12L, 50)));

        ProductSaveRequest.SkuItem kept = sku(80);
        kept.setId(11L);
        productService.update(1L, productReq(List.of(kept)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ProductSku>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(skuMapper).delete(captor.capture());
        captor.getValue().getSqlSegment();
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains(12L)
                .doesNotContain(11L);
    }

    @Test
    @DisplayName("编辑商品：不带 id 的 SKU 走新增，已有 SKU 不受影响")
    void updateInsertsSkuWithoutId() {
        stubExistingProduct();
        when(skuMapper.selectList(any())).thenReturn(List.of(storedSku(11L, 100)));

        ProductSaveRequest.SkuItem kept = sku(80);
        kept.setId(11L);
        ProductSaveRequest.SkuItem added = sku(20); // 新增行不带 id
        productService.update(1L, productReq(List.of(kept, added)));

        ArgumentCaptor<ProductSku> captor = ArgumentCaptor.forClass(ProductSku.class);
        verify(skuMapper).insert(captor.capture());
        assertThat(captor.getValue().getId()).isNull();
        assertThat(captor.getValue().getProductId()).isEqualTo(1L);
        assertThat(captor.getValue().getStock()).isEqualTo(20);
        verify(skuMapper, never()).delete(any());
    }

    @Test
    @DisplayName("编辑商品：带别的商品的 SKU id 直接拒绝，不误改他人规格")
    void updateRejectsForeignSkuId() {
        // 这里不调 stubExistingProduct()：异常在 updateById 之前就抛出，
        // 多打一个桩会触发 Mockito 的 UnnecessaryStubbing
        Product product = new Product();
        product.setId(1L);
        product.setPrice(new BigDecimal("99.00"));
        when(productMapper.selectById(1L)).thenReturn(product);
        categoryExists();
        when(skuMapper.selectList(any())).thenReturn(List.of(storedSku(11L, 100)));

        ProductSaveRequest.SkuItem foreign = sku(80);
        foreign.setId(99L);

        assertThatThrownBy(() -> productService.update(1L, productReq(List.of(foreign))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不属于该商品");
        verify(skuMapper, never()).update(any(), any());
        verify(skuMapper, never()).delete(any());
    }

    @Test
    @DisplayName("编辑商品：总库存取库里 SKU 合计，不照抄请求里的数字")
    void updateRecalculatesStockFromDb() {
        stubExistingProduct();
        ProductSaveRequest.SkuItem item = sku(999); // 请求里是表单旧值
        item.setId(11L);
        // 第一次 selectList 给 syncSkus（库里已有的行），第二次给 skuStockTotal（重算结果）
        when(skuMapper.selectList(any()))
                .thenReturn(List.of(storedSku(11L, 100)))
                .thenReturn(List.of(storedSku(11L, 80)));

        productService.update(1L, productReq(List.of(item)));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).updateById(captor.capture());
        // 若照抄请求值会是 999；管理员打开抽屉后有人下单时，照抄会把库存写回旧值
        assertThat(captor.getValue().getStock()).isEqualTo(80);
    }

    @Test
    @DisplayName("编辑商品：请求不带 skus 时完全不碰 SKU 表")
    void updateWithoutSkusLeavesThemAlone() {
        stubExistingProduct();

        productService.update(1L, productReq(null));

        verify(skuMapper, never()).selectList(any());
        verify(skuMapper, never()).insert(any(ProductSku.class));
        verify(skuMapper, never()).update(any(), any());
        verify(skuMapper, never()).delete(any());
    }
}
