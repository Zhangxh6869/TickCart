package com.tickcart.seckill.controller;

import com.tickcart.common.api.R;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.security.UserContext;
import com.tickcart.seckill.cache.SeckillResult;
import com.tickcart.seckill.dto.SeckillBuyRequest;
import com.tickcart.seckill.service.SeckillActivityService;
import com.tickcart.seckill.service.SeckillBuyService;
import com.tickcart.seckill.service.SeckillResultService;
import com.tickcart.seckill.vo.SeckillResultVO;
import com.tickcart.seckill.vo.SeckillActivityVO;
import com.tickcart.seckill.vo.SeckillBuyResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final SeckillBuyService buyService;
    private final SeckillResultService resultService;

    public SeckillController(SeckillActivityService activityService,
                             SeckillBuyService buyService,
                             SeckillResultService resultService) {
        this.activityService = activityService;
        this.buyService = buyService;
        this.resultService = resultService;
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

    @Operation(summary = "抢购",
            description = "同步完成库存原子扣减并把建单任务投递到 MQ，返回 requestId 供轮询结果；"
                    + "必须登录（限购需要用户维度），且必须指定属于本人的收货地址")
    @PostMapping("/activities/{activityId}/order")
    public R<SeckillBuyResultVO> buy(@PathVariable Long activityId,
                                     @Valid @RequestBody SeckillBuyRequest req) {
        int quantity = req.getQuantity() == null ? 1 : req.getQuantity();
        return R.ok(buyService.buy(currentUserId(), activityId, quantity, req.getAddressId()));
    }

    @Operation(summary = "抢购结果",
            description = "供前端轮询：SUCCESS 带订单号，FAILED 带原因（库存已退回）；"
                    + "不存在、已过期或非本人一律 404")
    @GetMapping("/result/{requestId}")
    public R<SeckillResultVO> result(@PathVariable String requestId) {
        SeckillResult result = resultService.get(requestId, currentUserId());
        if (result == null) {
            throw BizException.notFound("抢购结果不存在或已过期");
        }
        SeckillResultVO vo = new SeckillResultVO();
        vo.setRequestId(result.getRequestId());
        vo.setStatus(result.getStatus());
        vo.setOrderNo(result.getOrderNo());
        vo.setMessage(result.getMessage());
        return R.ok(vo);
    }

    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw BizException.unauthorized("未登录或登录已过期");
        }
        return userId;
    }
}
