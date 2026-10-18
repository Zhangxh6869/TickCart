package com.tickcart.seckill.controller;

import com.tickcart.common.api.PageResult;
import com.tickcart.common.api.R;
import com.tickcart.seckill.dto.SeckillActivityQuery;
import com.tickcart.seckill.dto.SeckillActivitySaveRequest;
import com.tickcart.seckill.service.SeckillActivityService;
import com.tickcart.seckill.vo.SeckillActivityVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

/**
 * 秒杀活动管理（后台，需 ADMIN 角色）。
 */
@Tag(name = "秒杀管理", description = "秒杀活动的增删改查与上下线")
@RestController
@RequestMapping("/api/admin/seckill")
public class AdminSeckillController {

    private final SeckillActivityService activityService;

    public AdminSeckillController(SeckillActivityService activityService) {
        this.activityService = activityService;
    }

    @Operation(summary = "活动分页列表", description = "status 为推导状态；指定后忽略 enabled 参数")
    @GetMapping("/activities")
    public R<PageResult<SeckillActivityVO>> page(SeckillActivityQuery query) {
        return R.ok(activityService.page(query));
    }

    @Operation(summary = "活动详情")
    @GetMapping("/activities/{id}")
    public R<SeckillActivityVO> detail(@PathVariable Long id) {
        return R.ok(activityService.detail(id));
    }

    @Operation(summary = "新建活动", description = "秒杀价必须低于商品原价；默认上线")
    @PostMapping("/activities")
    public R<Long> create(@Valid @RequestBody SeckillActivitySaveRequest req) {
        return R.ok(activityService.create(req));
    }

    @Operation(summary = "编辑活动", description = "活动已开始时禁止修改秒杀价与库存")
    @PutMapping("/activities/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody SeckillActivitySaveRequest req) {
        activityService.update(id, req);
        return R.ok();
    }

    @Operation(summary = "上线/下线")
    @PutMapping("/activities/{id}/enabled")
    public R<Void> changeEnabled(@PathVariable Long id,
                                 @Parameter(description = "0下线 1上线") @RequestParam Integer enabled) {
        activityService.changeEnabled(id, enabled);
        return R.ok();
    }

    @Operation(summary = "删除活动", description = "有成交记录时拒绝")
    @DeleteMapping("/activities/{id}")
    public R<Void> delete(@PathVariable Long id) {
        activityService.delete(id);
        return R.ok();
    }
}
