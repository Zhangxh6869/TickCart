package com.tickcart.product.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.dto.ProductQuery;
import com.tickcart.product.dto.ProductSaveRequest;
import com.tickcart.product.entity.Category;
import com.tickcart.product.entity.Product;
import com.tickcart.product.entity.ProductSku;
import com.tickcart.product.mapper.CategoryMapper;
import com.tickcart.product.mapper.ProductImageMapper;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.product.mapper.ProductSkuMapper;
import com.tickcart.product.vo.ProductDetail;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private ProductService productService;

    /**
     * 纯单元测试没有 Spring 容器，{@code LambdaQueryWrapper} 解析列名时会去查实体的
     * TableInfo 缓存；只要断言里调用了 {@code getSqlSegment()} 把条件实体化，
     * 就必须先手动初始化，否则报 "can not find lambda cache"。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProductSku.class);
        // 关键字转义的断言要实体化商品表的 wrapper，Product 也要初始化
        TableInfoHelper.initTableInfo(assistant, Product.class);
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
    @DisplayName("编辑商品时重建的 SKU 同样继承商品价")
    void updateRebuiltSkuInheritsProductPrice() {
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
    @DisplayName("商品名与副标题两个字段都参与匹配")
    void subtitleIsAlsoSearched() {
        LambdaQueryWrapper<Product> wrapper = captureListWrapper("亲肤");

        assertThat(wrapper.getSqlSegment())
                .contains("name LIKE")
                .contains("subtitle LIKE")
                // 两个字段都要显式 ESCAPE，别只给 name 加
                .contains("ESCAPE '!'");
        assertThat(wrapper.getSqlSegment()).contains(" OR ");

        // 两个字段共用同一份 pattern（两个占位符，值相同）
        assertThat(capturedParams(wrapper).stream().filter("%亲肤%"::equals).count())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("副标题同样受通配符转义保护")
    void subtitleIsAlsoEscaped() {
        LambdaQueryWrapper<Product> wrapper = captureListWrapper("%");

        // name 与 subtitle 两个占位符都必须是转义后的 pattern，不能有一个漏掉
        assertThat(capturedParams(wrapper).stream().filter("%!%%"::equals).count())
                .isEqualTo(2);
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
}
