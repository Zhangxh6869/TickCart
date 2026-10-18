package com.tickcart.seckill.controller;

import com.tickcart.common.api.R;
import com.tickcart.seckill.service.SeckillActivityService;
import com.tickcart.seckill.vo.SeckillActivityVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 秒杀前台接口（匿名可访问）。
 *
 * <p>B1 只提供活动列表与详情；抢购接口在 B2 加入。</p>
 */
@Tag(name = "秒杀", description = "秒杀活动列表与详情")
@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    private final SeckillActivityService activityService;

    public SeckillController(SeckillActivityService activityService) {
        this.activityService = activityService;
    }

    @Operation(summary = "进行中与即将开始的秒杀活动", description = "按开始时间升序；已结束与已下线不返回")
    @GetMapping("/activities")
    public R<List<SeckillActivityVO>> activities() {
        return R.ok(activityService.listActive());
    }

    @Operation(summary = "秒杀活动详情", description = "已下线的活动按不存在处理")
    @GetMapping("/activities/{id}")
    public R<SeckillActivityVO> detail(@PathVariable Long id) {
        return R.ok(activityService.publicDetail(id));
    }
}
