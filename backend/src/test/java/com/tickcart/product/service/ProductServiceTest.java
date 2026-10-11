package com.tickcart.product.service;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}
