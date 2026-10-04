package com.tickcart.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 分类新增/编辑请求。
 */
@Schema(description = "分类新增/编辑请求")
public class CategorySaveRequest {

    @Schema(description = "分类ID（编辑时必填）")
    private Long id;

    @Schema(description = "父分类ID，0 为顶级", example = "0")
    @NotNull(message = "父分类不能为空")
    private Long parentId;

    @Schema(description = "分类名称", example = "数码电子")
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称最长 50 字")
    private String name;

    @Schema(description = "分类图标URL")
    private String icon;

    @Schema(description = "排序值（越小越靠前）", example = "1")
    private Integer sort;

    @Schema(description = "状态 0禁用 1启用", example = "1")
    private Integer status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
