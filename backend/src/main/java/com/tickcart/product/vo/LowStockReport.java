package com.tickcart.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 补货待办清单。
 *
 * <p>{@code total} 是全库告急 SKU 数，**不受 {@code limit} 影响** ——
 * 界面上「4 个 SKU 库存告急」那个数字要准，不能因为只取了前 100 条就变成 100。</p>
 */
@Schema(description = "补货待办清单")
public class LowStockReport {

    @Schema(description = "全库告急 SKU 总数")
    private int total;

    /**
     * 其中已断货（库存 0）的 SKU 数。
     *
     * <p>后端算而不是前端数 {@code items}：{@code items} 会被 limit 截断，
     * 前端数出来的断货数会偏小，横幅上「其中 2 个已断货」就不准了。</p>
     */
    @Schema(description = "其中已断货（库存 0）的数量")
    private int outOfStock;

    @Schema(description = "是否被扫描上限或 limit 截断")
    private boolean truncated;

    @Schema(description = "告急明细，按紧急程度排序")
    private List<LowStockItem> items;

    public LowStockReport() {
    }

    public LowStockReport(int total, int outOfStock, boolean truncated, List<LowStockItem> items) {
        this.total = total;
        this.outOfStock = outOfStock;
        this.truncated = truncated;
        this.items = items;
    }

    /** 没有任何告急时的空结果。 */
    public static LowStockReport empty() {
        return new LowStockReport(0, 0, false, List.of());
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getOutOfStock() {
        return outOfStock;
    }

    public void setOutOfStock(int outOfStock) {
        this.outOfStock = outOfStock;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public List<LowStockItem> getItems() {
        return items;
    }

    public void setItems(List<LowStockItem> items) {
        this.items = items;
    }
}
