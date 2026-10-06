package com.tickcart.member.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tickcart.common.exception.BizException;
import com.tickcart.member.dto.AddressSaveRequest;
import com.tickcart.member.entity.UserAddress;
import com.tickcart.member.mapper.UserAddressMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AddressService} 单元测试。
 *
 * <p>覆盖归属校验、默认地址唯一性、数量上限与删除默认地址后的自动补位。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("收货地址服务")
class AddressServiceTest {

    private static final Long UID = 1001L;

    @Mock
    private UserAddressMapper addressMapper;

    @InjectMocks
    private AddressService addressService;

    /**
     * 纯单元测试没有 Spring 容器，而 {@code LambdaUpdateWrapper.set(SFunction, value)}
     * 会立即解析列名，需先手动初始化实体的 TableInfo 缓存，否则报 "can not find lambda cache"。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAddress.class);
    }

    private AddressSaveRequest req(Integer isDefault) {
        AddressSaveRequest r = new AddressSaveRequest();
        r.setReceiver("张三");
        r.setPhone("13800138000");
        r.setProvince("广东省");
        r.setCity("深圳市");
        r.setDistrict("南山区");
        r.setDetail("科技园路 1 号");
        r.setIsDefault(isDefault);
        return r;
    }

    private UserAddress address(Long id, Long userId, int isDefault) {
        UserAddress a = new UserAddress();
        a.setId(id);
        a.setUserId(userId);
        a.setIsDefault(isDefault);
        return a;
    }

    // ---------------- 新增 ----------------

    @Test
    @DisplayName("新增首个地址自动成为默认")
    void createFirstBecomesDefault() {
        when(addressMapper.selectCount(any())).thenReturn(0L);
        when(addressMapper.insert(any(UserAddress.class))).thenAnswer(inv -> {
            UserAddress a = inv.getArgument(0);
            a.setId(1L);
            return 1;
        });

        UserAddress created = addressService.create(UID, req(0));

        assertThat(created.getIsDefault()).isEqualTo(1);
        assertThat(created.getUserId()).isEqualTo(UID);
        // 首个地址无需清理其它默认
        verify(addressMapper, never()).update(ArgumentMatchers.<UserAddress>isNull(), any());
    }

    @Test
    @DisplayName("新增非首个地址且未勾选默认时保持非默认")
    void createNonFirstKeepsNotDefault() {
        when(addressMapper.selectCount(any())).thenReturn(3L);
        when(addressMapper.insert(any(UserAddress.class))).thenAnswer(inv -> {
            UserAddress a = inv.getArgument(0);
            a.setId(9L);
            return 1;
        });

        UserAddress created = addressService.create(UID, req(0));

        assertThat(created.getIsDefault()).isEqualTo(0);
        verify(addressMapper, never()).update(ArgumentMatchers.<UserAddress>isNull(), any());
    }

    @Test
    @DisplayName("新增并勾选默认时清除其它默认")
    void createWithDefaultClearsOthers() {
        when(addressMapper.selectCount(any())).thenReturn(2L);
        when(addressMapper.update(ArgumentMatchers.<UserAddress>isNull(), any())).thenReturn(1);
        when(addressMapper.insert(any(UserAddress.class))).thenAnswer(inv -> {
            UserAddress a = inv.getArgument(0);
            a.setId(10L);
            return 1;
        });

        UserAddress created = addressService.create(UID, req(1));

        assertThat(created.getIsDefault()).isEqualTo(1);
        verify(addressMapper).update(ArgumentMatchers.<UserAddress>isNull(), any());
    }

    @Test
    @DisplayName("地址数量达到上限时拒绝新增")
    void createExceedsLimit() {
        when(addressMapper.selectCount(any())).thenReturn(20L);

        assertThatThrownBy(() -> addressService.create(UID, req(0)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("最多只能保存 20 条收货地址");
    }

    // ---------------- 归属校验 ----------------

    @Test
    @DisplayName("查询他人地址按不存在处理")
    void getForeignAddressAsNotFound() {
        when(addressMapper.selectById(5L)).thenReturn(address(5L, 999L, 0));

        assertThatThrownBy(() -> addressService.getOwned(UID, 5L))
                .isInstanceOf(BizException.class)
                .hasMessage("收货地址不存在");
    }

    @Test
    @DisplayName("编辑他人地址被拒绝")
    void updateForeignAddressRejected() {
        when(addressMapper.selectById(5L)).thenReturn(address(5L, 999L, 0));

        assertThatThrownBy(() -> addressService.update(UID, 5L, req(0)))
                .isInstanceOf(BizException.class)
                .hasMessage("收货地址不存在");
    }

    @Test
    @DisplayName("删除他人地址被拒绝")
    void deleteForeignAddressRejected() {
        when(addressMapper.selectById(5L)).thenReturn(address(5L, 999L, 0));

        assertThatThrownBy(() -> addressService.delete(UID, 5L))
                .isInstanceOf(BizException.class)
                .hasMessage("收货地址不存在");
    }

    // ---------------- 编辑 ----------------

    @Test
    @DisplayName("编辑地址更新全部字段")
    void updateFields() {
        when(addressMapper.selectById(5L)).thenReturn(address(5L, UID, 0));
        when(addressMapper.updateById(any(UserAddress.class))).thenReturn(1);

        AddressSaveRequest r = req(0);
        r.setReceiver("李四");
        r.setDetail("深南大道 100 号");
        UserAddress updated = addressService.update(UID, 5L, r);

        assertThat(updated.getReceiver()).isEqualTo("李四");
        assertThat(updated.getDetail()).isEqualTo("深南大道 100 号");
    }

    @Test
    @DisplayName("编辑时勾选默认会清除其它默认")
    void updateToDefaultClearsOthers() {
        when(addressMapper.selectById(5L)).thenReturn(address(5L, UID, 0));
        when(addressMapper.update(ArgumentMatchers.<UserAddress>isNull(), any())).thenReturn(1);
        when(addressMapper.updateById(any(UserAddress.class))).thenReturn(1);

        UserAddress updated = addressService.update(UID, 5L, req(1));

        assertThat(updated.getIsDefault()).isEqualTo(1);
        verify(addressMapper).update(ArgumentMatchers.<UserAddress>isNull(), any());
    }

    // ---------------- 设为默认 ----------------

    @Test
    @DisplayName("设为默认时清除其它默认")
    void setDefaultClearsOthers() {
        when(addressMapper.selectById(7L)).thenReturn(address(7L, UID, 0));
        when(addressMapper.update(ArgumentMatchers.<UserAddress>isNull(), any())).thenReturn(1);
        when(addressMapper.updateById(any(UserAddress.class))).thenReturn(1);

        addressService.setDefault(UID, 7L);

        verify(addressMapper).update(ArgumentMatchers.<UserAddress>isNull(), any());
        verify(addressMapper).updateById(ArgumentMatchers.<UserAddress>argThat(a -> a.getIsDefault() == 1));
    }

    @Test
    @DisplayName("已是默认地址时重复设置不报错")
    void setDefaultIdempotent() {
        when(addressMapper.selectById(7L)).thenReturn(address(7L, UID, 1));
        when(addressMapper.update(ArgumentMatchers.<UserAddress>isNull(), any())).thenReturn(1);
        when(addressMapper.updateById(any(UserAddress.class))).thenReturn(1);

        addressService.setDefault(UID, 7L);

        verify(addressMapper).updateById(ArgumentMatchers.<UserAddress>argThat(a -> a.getIsDefault() == 1));
    }

    // ---------------- 删除 ----------------

    @Test
    @DisplayName("删除非默认地址不影响默认项")
    void deleteNonDefaultKeepsDefault() {
        when(addressMapper.selectById(5L)).thenReturn(address(5L, UID, 0));
        when(addressMapper.deleteById(5L)).thenReturn(1);

        addressService.delete(UID, 5L);

        verify(addressMapper).deleteById(5L);
        // 无默认项需要补位
        verify(addressMapper, never()).updateById(any(UserAddress.class));
    }

    @Test
    @DisplayName("删除默认地址后自动把剩余最新一条补为默认")
    void deleteDefaultPromotesAnother() {
        when(addressMapper.selectById(5L)).thenReturn(address(5L, UID, 1));
        when(addressMapper.deleteById(5L)).thenReturn(1);
        when(addressMapper.selectOne(any())).thenReturn(address(6L, UID, 0));
        when(addressMapper.updateById(any(UserAddress.class))).thenReturn(1);

        addressService.delete(UID, 5L);

        verify(addressMapper).updateById(ArgumentMatchers.<UserAddress>argThat(a -> a.getId() == 6L && a.getIsDefault() == 1));
    }

    @Test
    @DisplayName("删除最后一个默认地址后无剩余地址则不补位")
    void deleteLastDefaultNoPromote() {
        when(addressMapper.selectById(5L)).thenReturn(address(5L, UID, 1));
        when(addressMapper.deleteById(5L)).thenReturn(1);
        when(addressMapper.selectOne(any())).thenReturn(null);

        addressService.delete(UID, 5L);

        verify(addressMapper, never()).updateById(any(UserAddress.class));
    }
}
