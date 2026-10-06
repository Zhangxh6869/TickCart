package com.tickcart.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tickcart.common.exception.BizException;
import com.tickcart.member.dto.AddressSaveRequest;
import com.tickcart.member.entity.UserAddress;
import com.tickcart.member.mapper.UserAddressMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货地址服务。
 *
 * <p>所有方法均以「当前登录用户」为边界：{@link #getOwned} 统一做归属校验，
 * 越权访问按「不存在」处理，避免通过 ID 探测他人地址。</p>
 *
 * <p>默认地址唯一性由本层保证（DB 不加约束以保留灵活性）：任意时刻同一用户
 * 至多一条 {@code is_default = 1}；首个地址自动成为默认；删除默认地址后自动补位。</p>
 */
@Service
public class AddressService {

    /** 单用户地址数量上限，防止滥用 */
    private static final int MAX_ADDRESS_COUNT = 20;

    private final UserAddressMapper addressMapper;

    public AddressService(UserAddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    /** 当前用户的地址列表：默认地址排最前，其余按创建顺序倒序。 */
    public List<UserAddress> list(Long userId) {
        return addressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getId));
    }

    /** 获取当前用户的默认地址；无默认时退化为最新一条；一条都没有则返回 null。 */
    public UserAddress getDefault(Long userId) {
        return addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getId)
                .last("limit 1"));
    }

    /**
     * 按 ID 获取地址并校验归属。
     *
     * @throws BizException 地址不存在或不属于该用户（统一按 404 处理）
     */
    public UserAddress getOwned(Long userId, Long id) {
        UserAddress address = addressMapper.selectById(id);
        if (address == null || !userId.equals(address.getUserId())) {
            throw BizException.notFound("收货地址不存在");
        }
        return address;
    }

    /** 新增地址：首个地址自动成为默认；显式勾选默认时清除其它默认项。 */
    @Transactional
    public UserAddress create(Long userId, AddressSaveRequest req) {
        Long count = addressMapper.selectCount(
                new LambdaQueryWrapper<UserAddress>().eq(UserAddress::getUserId, userId));
        long existing = count == null ? 0L : count;
        if (existing >= MAX_ADDRESS_COUNT) {
            throw BizException.badRequest("最多只能保存 " + MAX_ADDRESS_COUNT + " 条收货地址");
        }

        UserAddress address = new UserAddress();
        address.setUserId(userId);
        applyRequest(address, req);

        // 首个地址自动成为默认，避免结算页选不出默认地址
        boolean asDefault = existing == 0 || Integer.valueOf(1).equals(req.getIsDefault());
        if (asDefault && existing > 0) {
            clearDefault(userId, null);
        }
        address.setIsDefault(asDefault ? 1 : 0);

        addressMapper.insert(address);
        return address;
    }

    /** 编辑地址：勾选默认时清除其它默认项，取消勾选不会清掉原有默认状态。 */
    @Transactional
    public UserAddress update(Long userId, Long id, AddressSaveRequest req) {
        UserAddress address = getOwned(userId, id);
        applyRequest(address, req);
        if (Integer.valueOf(1).equals(req.getIsDefault()) && !Integer.valueOf(1).equals(address.getIsDefault())) {
            clearDefault(userId, id);
            address.setIsDefault(1);
        }
        addressMapper.updateById(address);
        return address;
    }

    /** 设为默认地址（幂等）。 */
    @Transactional
    public void setDefault(Long userId, Long id) {
        UserAddress address = getOwned(userId, id);
        clearDefault(userId, id);
        address.setIsDefault(1);
        addressMapper.updateById(address);
    }

    /** 删除地址：若删除的是默认地址，自动把剩余最新一条补为默认。 */
    @Transactional
    public void delete(Long userId, Long id) {
        UserAddress address = getOwned(userId, id);
        addressMapper.deleteById(id);

        if (Integer.valueOf(1).equals(address.getIsDefault())) {
            UserAddress next = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                    .eq(UserAddress::getUserId, userId)
                    .orderByDesc(UserAddress::getId)
                    .last("limit 1"));
            if (next != null) {
                next.setIsDefault(1);
                addressMapper.updateById(next);
            }
        }
    }

    /** 把该用户其它地址的默认标记清零（{@code exceptId} 非空时保留该条不动）。 */
    private void clearDefault(Long userId, Long exceptId) {
        LambdaUpdateWrapper<UserAddress> wrapper = new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, 1)
                .set(UserAddress::getIsDefault, 0);
        if (exceptId != null) {
            wrapper.ne(UserAddress::getId, exceptId);
        }
        addressMapper.update(null, wrapper);
    }

    /** 把请求字段写入实体（不含 userId / isDefault，两者由业务规则决定）。 */
    private void applyRequest(UserAddress address, AddressSaveRequest req) {
        address.setReceiver(req.getReceiver());
        address.setPhone(req.getPhone());
        address.setProvince(req.getProvince());
        address.setCity(req.getCity());
        address.setDistrict(req.getDistrict());
        address.setDetail(req.getDetail());
    }
}
