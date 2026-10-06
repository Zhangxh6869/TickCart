package com.tickcart.member.controller;

import com.tickcart.common.api.R;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.security.UserContext;
import com.tickcart.member.dto.AddressSaveRequest;
import com.tickcart.member.entity.UserAddress;
import com.tickcart.member.service.AddressService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址接口（需登录）。
 *
 * <p>用户身份取自 JWT，不接受客户端传入 userId，杜绝越权操作他人地址。</p>
 */
@Tag(name = "收货地址", description = "登录用户的收货地址管理")
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @Operation(summary = "地址列表", description = "默认地址排最前")
    @GetMapping
    public R<List<UserAddress>> list() {
        return R.ok(addressService.list(currentUserId()));
    }

    @Operation(summary = "地址详情")
    @GetMapping("/{id}")
    public R<UserAddress> detail(@PathVariable Long id) {
        return R.ok(addressService.getOwned(currentUserId(), id));
    }

    @Operation(summary = "新增地址", description = "首个地址自动成为默认")
    @PostMapping
    public R<Long> create(@Valid @RequestBody AddressSaveRequest req) {
        return R.ok(addressService.create(currentUserId(), req).getId());
    }

    @Operation(summary = "编辑地址")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody AddressSaveRequest req) {
        addressService.update(currentUserId(), id, req);
        return R.ok();
    }

    @Operation(summary = "设为默认地址")
    @PutMapping("/{id}/default")
    public R<Void> setDefault(@PathVariable Long id) {
        addressService.setDefault(currentUserId(), id);
        return R.ok();
    }

    @Operation(summary = "删除地址", description = "删除默认地址后自动补位")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        addressService.delete(currentUserId(), id);
        return R.ok();
    }

    /** 取当前登录用户 ID；理论上已被 Security 拦截，此处兜底防 NPE。 */
    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw BizException.unauthorized("未登录或登录已过期");
        }
        return userId;
    }
}
