package com.tickcart.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 批量改分类请求。
 *
 * <p>单独建一个 DTO 而不是复用 {@link ProductSaveRequest}：批量改分类只动
 * {@code category_id} 一列，走 {@code ProductService#update} 会触发全量覆盖
 * （重建 SKU、重算库存、把未传字段清空），代价和风险都不对等。</p>
 */
@Schema(description = "批量改分类请求")
public class ProductBatchCategoryRequest {

    @Schema(description = "商品ID列表", example = "[101, 102]")
    private List<Long> ids;

    @Schema(description = "目标分类ID", example = "5")
    @NotNull(message = "请选择目标分类")
    private Long categoryId;

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}
