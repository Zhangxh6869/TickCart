package com.tickcart.admin.controller;

import com.tickcart.admin.dto.AdminUserQuery;
import com.tickcart.admin.service.AdminUserService;
import com.tickcart.admin.vo.AdminUserDetailVO;
import com.tickcart.admin.vo.AdminUserVO;
import com.tickcart.common.api.PageResult;
import com.tickcart.common.api.R;
import com.tickcart.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台用户管理接口（需 ROLE_ADMIN）。
 *
 * <p>路径前缀 {@code /api/admin/**}，由 {@code SecurityConfig} 统一要求管理员角色。</p>
 *
 * <p>只有三个动作：查列表、查详情、启用/禁用。改密码、改角色、删除用户
 * 都是刻意不做的（理由见 {@link AdminUserService} 类注释）。</p>
 */
@Tag(name = "用户(后台)", description = "用户查询与账号状态")
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Operation(summary = "用户列表", description = "关键字命中用户名/昵称/手机号，纯数字额外按ID匹配；手机号已脱敏")
    @GetMapping
    public R<PageResult<AdminUserVO>> list(AdminUserQuery query) {
        return R.ok(adminUserService.page(query));
    }

    @Operation(summary = "用户详情", description = "明文手机号 + 订单统计 + 最近5单 + 收货地址（只读）")
    @GetMapping("/{id}")
    public R<AdminUserDetailVO> detail(@PathVariable Long id) {
        return R.ok(adminUserService.detail(id));
    }

    @Operation(summary = "启用/禁用账号",
            description = "status 0正常 1禁用。禁用会立即吊销该用户全部会话（含已发出的 access token）；"
                    + "不能操作自己，也不能禁用管理员账号")
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminUserService.changeStatus(UserContext.getUserId(), id, status);
        return R.ok();
    }
}
