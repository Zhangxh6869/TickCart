package com.tickcart.product.controller;

import com.tickcart.common.api.PageResult;
import com.tickcart.common.api.R;
import com.tickcart.product.dto.ProductQuery;
import com.tickcart.product.entity.Category;
import com.tickcart.product.service.CategoryService;
import com.tickcart.product.service.ProductService;
import com.tickcart.product.vo.ProductDetail;
import com.tickcart.product.vo.ProductListItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台商城商品接口（公开，无需登录）。
 */
@Tag(name = "商品(前台)", description = "商品浏览、搜索、分类筛选")
@RestController
@RequestMapping("/api")
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;

    public ProductController(ProductService productService, CategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    @Operation(summary = "商品列表", description = "支持关键字搜索、分类筛选、排序、分页；仅返回上架商品")
    @GetMapping("/products")
    public R<PageResult<ProductListItem>> list(ProductQuery query) {
        return R.ok(productService.page(query, true));
    }

    @Operation(summary = "商品详情")
    @GetMapping("/products/{id}")
    public R<ProductDetail> detail(@PathVariable Long id) {
        return R.ok(productService.detail(id));
    }

    @Operation(summary = "分类列表", description = "仅返回启用的分类")
    @GetMapping("/categories")
    public R<List<Category>> categories() {
        return R.ok(categoryService.list(true));
    }
}
