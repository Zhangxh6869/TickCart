package com.tickcart.product.controller;

import com.tickcart.common.api.PageResult;
import com.tickcart.common.api.R;
import com.tickcart.product.dto.CategorySaveRequest;
import com.tickcart.product.dto.ProductBatchCategoryRequest;
import com.tickcart.product.dto.ProductQuery;
import com.tickcart.product.dto.ProductSaveRequest;
import com.tickcart.product.entity.Category;
import com.tickcart.product.service.CategoryService;
import com.tickcart.product.service.ProductService;
import com.tickcart.product.vo.LowStockReport;
import com.tickcart.product.vo.ProductDetail;
import com.tickcart.product.vo.ProductListItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台商品管理接口（需 ROLE_ADMIN）。
 *
 * <p>路径前缀 {@code /api/admin/**}，由 {@code SecurityConfig} 统一要求管理员角色。</p>
 */
@Tag(name = "商品(后台)", description = "商品与分类管理")
@RestController
@RequestMapping("/api/admin")
public class AdminProductController {

    private final ProductService productService;
    private final CategoryService categoryService;

    public AdminProductController(ProductService productService, CategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    // ---------------- 商品 ----------------

    @Operation(summary = "商品列表", description = "可查全部状态（含下架）")
    @GetMapping("/products")
    public R<PageResult<ProductListItem>> list(ProductQuery query) {
        return R.ok(productService.page(query, false));
    }

    @Operation(summary = "商品详情", description = "后台读取绕过缓存，避免编辑到 TTL 内的旧数据")
    @GetMapping("/products/{id}")
    public R<ProductDetail> detail(@PathVariable Long id) {
        return R.ok(productService.detailFresh(id));
    }

    @Operation(summary = "新增商品")
    @PostMapping("/products")
    public R<Long> create(@Valid @RequestBody ProductSaveRequest req) {
        return R.ok(productService.create(req));
    }

    @Operation(summary = "编辑商品")
    @PutMapping("/products/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ProductSaveRequest req) {
        productService.update(id, req);
        return R.ok();
    }

    @Operation(summary = "上架/下架")
    @PutMapping("/products/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        productService.changeStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "删除商品")
    @DeleteMapping("/products/{id}")
    public R<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return R.ok();
    }

    // ---------------- 批量（单事务，全部成功或全部不做） ----------------

    @Operation(summary = "批量上架/下架", description = "返回实际更新行数；选中项里有已删除的会整批取消")
    @PutMapping("/products/batch/status")
    public R<Integer> batchChangeStatus(@RequestParam Integer status, @RequestBody List<Long> ids) {
        return R.ok(productService.batchChangeStatus(ids, status));
    }

    @Operation(summary = "批量改分类", description = "只改 category_id，不重建 SKU")
    @PutMapping("/products/batch/category")
    public R<Integer> batchChangeCategory(@Valid @RequestBody ProductBatchCategoryRequest req) {
        return R.ok(productService.batchChangeCategory(req.getIds(), req.getCategoryId()));
    }

    @Operation(summary = "批量删除", description = "连同 SKU 与图集，单事务")
    @PostMapping("/products/batch/delete")
    public R<Integer> batchDelete(@RequestBody List<Long> ids) {
        return R.ok(productService.batchDelete(ids));
    }

    @Operation(summary = "修改 SKU 库存")
    @PutMapping("/skus/{skuId}/stock")
    public R<Void> updateSkuStock(@PathVariable Long skuId, @RequestParam Integer stock) {
        productService.updateSkuStock(skuId, stock);
        return R.ok();
    }

    @Operation(summary = "补货待办",
            description = "库存低于所属分类预警线的 SKU 清单，按剩余比例升序（断货最前）。total 不受 limit 影响")
    @GetMapping("/products/low-stock")
    public R<LowStockReport> lowStock(@RequestParam(defaultValue = "100") int limit) {
        return R.ok(productService.lowStock(limit));
    }

    // ---------------- 分类 ----------------

    @Operation(summary = "分类列表", description = "含禁用分类")
    @GetMapping("/categories")
    public R<List<Category>> categories() {
        return R.ok(categoryService.list(false));
    }

    @Operation(summary = "新增分类")
    @PostMapping("/categories")
    public R<Long> createCategory(@Valid @RequestBody CategorySaveRequest req) {
        return R.ok(categoryService.create(req).getId());
    }

    @Operation(summary = "编辑分类")
    @PutMapping("/categories/{id}")
    public R<Void> updateCategory(@PathVariable Long id, @Valid @RequestBody CategorySaveRequest req) {
        categoryService.update(id, req);
        return R.ok();
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/categories/{id}")
    public R<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return R.ok();
    }
}
