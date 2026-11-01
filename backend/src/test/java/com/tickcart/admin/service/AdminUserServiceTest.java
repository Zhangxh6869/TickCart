package com.tickcart.admin.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tickcart.admin.dto.AdminUserQuery;
import com.tickcart.admin.vo.AdminUserDetailVO;
import com.tickcart.admin.vo.AdminUserVO;
import com.tickcart.auth.entity.User;
import com.tickcart.auth.mapper.UserMapper;
import com.tickcart.auth.service.AuthService;
import com.tickcart.common.api.PageResult;
import com.tickcart.common.exception.BizException;
import com.tickcart.member.entity.UserAddress;
import com.tickcart.member.mapper.UserAddressMapper;
import com.tickcart.trade.entity.Order;
import com.tickcart.trade.entity.RefundRecord;
import com.tickcart.trade.mapper.OrderMapper;
import com.tickcart.trade.mapper.RefundRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminUserService} 单元测试。
 *
 * <p>重点盯三件事：手机号脱敏边界、统计合并（不能退化成 N+1）、禁用是否真的吊销会话。
 * 前两个错了只是显示难看，第三个错了等于「禁用」形同虚设。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("后台用户管理服务")
class AdminUserServiceTest {

    private static final long USER_ID = 7L;
    private static final long ADMIN_ID = 1L;

    @Mock
    private UserMapper userMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private RefundRecordMapper refundRecordMapper;
    @Mock
    private UserAddressMapper userAddressMapper;
    @Mock
    private AuthService authService;

    private AdminUserService service;

    /**
     * 纯单元测试没有 Spring 容器，Lambda wrapper 解析列名要查实体 TableInfo 缓存，
     * 不预初始化就报 "can not find lambda cache"。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, Order.class);
        TableInfoHelper.initTableInfo(assistant, RefundRecord.class);
        TableInfoHelper.initTableInfo(assistant, UserAddress.class);
    }

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userMapper, orderMapper, refundRecordMapper,
                userAddressMapper, authService);
    }

    // =========================================================================
    // 列表
    // =========================================================================

    @Test
    @DisplayName("列表：手机号脱敏，订单统计按用户合并进来")
    void pageMasksPhoneAndMergesStats() {
        User user = user(USER_ID, "13800008888", 0, 0);
        user.setAvatar("/api/files/2026/11/01/a.jpg");
        stubPage(List.of(user), 1);

        Map<String, Object> row = new HashMap<>();
        row.put("uid", USER_ID);
        row.put("order_count", 5L);
        row.put("paid_order_count", 2L);
        row.put("paid_amount", new BigDecimal("199.00"));
        row.put("last_order_at", Timestamp.valueOf("2026-10-30 12:00:00"));
        when(orderMapper.selectMaps(any())).thenReturn(List.of(row));

        PageResult<AdminUserVO> result = service.page(new AdminUserQuery());

        assertThat(result.getTotal()).isEqualTo(1);
        AdminUserVO vo = result.getRecords().get(0);
        assertThat(vo.getPhone()).isEqualTo("138****8888");
        // 列表行必须带头像，否则后台用户管理页只能显示首字占位
        assertThat(vo.getAvatar()).isEqualTo("/api/files/2026/11/01/a.jpg");
        assertThat(vo.getOrderCount()).isEqualTo(5);
        assertThat(vo.getPaidOrderCount()).isEqualTo(2);
        assertThat(vo.getPaidAmount()).isEqualByComparingTo("199.00");
        assertThat(vo.getLastOrderAt()).isEqualTo(LocalDateTime.of(2026, 10, 30, 12, 0));
        assertThat(vo.getStatus()).isEqualTo(0);
    }

    @Test
    @DisplayName("列表：没有订单的用户统计为 0，不是 null")
    void pageDefaultsStatsWhenUserHasNoOrder() {
        stubPage(List.of(user(USER_ID, "13800008888", 0, 0)), 1);
        when(orderMapper.selectMaps(any())).thenReturn(List.of());

        AdminUserVO vo = service.page(new AdminUserQuery()).getRecords().get(0);

        assertThat(vo.getOrderCount()).isZero();
        assertThat(vo.getPaidAmount()).isEqualByComparingTo("0");
        assertThat(vo.getLastOrderAt()).isNull();
    }

    @Test
    @DisplayName("列表：本页为空时不再查订单统计（省一次无意义查询）")
    void pageSkipsStatsWhenEmpty() {
        stubPage(List.of(), 0);

        assertThat(service.page(new AdminUserQuery()).getRecords()).isEmpty();
        verify(orderMapper, never()).selectMaps(any());
    }

    // =========================================================================
    // 详情
    // =========================================================================

    @Test
    @DisplayName("详情：手机号给明文，地址里的手机号仍脱敏")
    void detailReturnsPlainPhoneButMasksAddressPhone() {
        when(userMapper.selectById(USER_ID)).thenReturn(user(USER_ID, "13800008888", 0, 0));
        when(orderMapper.selectCount(any())).thenReturn(6L, 3L);
        when(orderMapper.selectObjs(any()))
                .thenReturn(List.of(new BigDecimal("520.50")))
                .thenReturn(List.of(Timestamp.valueOf("2026-10-31 09:30:00")));
        when(refundRecordMapper.selectCount(any())).thenReturn(1L);
        when(orderMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<Order> page = inv.getArgument(0);
            page.setRecords(List.of(order("NO20261031001", 3, "99.00")));
            return page;
        });
        when(userAddressMapper.selectList(any())).thenReturn(List.of(address("13900001111")));

        AdminUserDetailVO vo = service.detail(USER_ID);

        assertThat(vo.getPhone()).isEqualTo("13800008888");
        assertThat(vo.getOrderCount()).isEqualTo(6);
        assertThat(vo.getPaidOrderCount()).isEqualTo(3);
        assertThat(vo.getPaidAmount()).isEqualByComparingTo("520.50");
        assertThat(vo.getRefundCount()).isEqualTo(1);
        assertThat(vo.getLastOrderAt()).isEqualTo(LocalDateTime.of(2026, 10, 31, 9, 30));
        assertThat(vo.getRecentOrders()).hasSize(1);
        assertThat(vo.getRecentOrders().get(0).getStatusLabel()).isEqualTo("已完成");
        assertThat(vo.getAddresses().get(0).getPhone()).isEqualTo("139****1111");
        assertThat(vo.getAddresses().get(0).getFullAddress()).isEqualTo("广东省深圳市南山区科技园路 1 号");
    }

    @Test
    @DisplayName("详情：用户不存在 → 404")
    void detailOfMissingUser() {
        when(userMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> service.detail(USER_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户不存在");
    }

    // =========================================================================
    // 启用 / 禁用
    // =========================================================================

    @Test
    @DisplayName("禁用：改状态 + 吊销全部会话（否则旧 token 还能下单）")
    void disableRevokesSessions() {
        when(userMapper.selectById(USER_ID)).thenReturn(user(USER_ID, "13800008888", 0, 0));

        service.changeStatus(ADMIN_ID, USER_ID, 1);

        assertThat(capturedStatus()).isEqualTo(1);
        verify(authService).revokeSessions(USER_ID);
    }

    @Test
    @DisplayName("启用：只改状态，不动会话")
    void enableDoesNotRevokeSessions() {
        when(userMapper.selectById(USER_ID)).thenReturn(user(USER_ID, "13800008888", 0, 1));

        service.changeStatus(ADMIN_ID, USER_ID, 0);

        assertThat(capturedStatus()).isEqualTo(0);
        verify(authService, never()).revokeSessions(anyLong());
    }

    @Test
    @DisplayName("重复禁用：幂等，不重复吊销也不报错")
    void disableIsIdempotent() {
        when(userMapper.selectById(USER_ID)).thenReturn(user(USER_ID, "13800008888", 0, 1));

        service.changeStatus(ADMIN_ID, USER_ID, 1);

        verify(userMapper, never()).updateById(any(User.class));
        verify(authService, never()).revokeSessions(anyLong());
    }

    @Test
    @DisplayName("不能禁用自己：后台只有一个管理员时没人能救回来")
    void cannotDisableSelf() {
        when(userMapper.selectById(ADMIN_ID)).thenReturn(user(ADMIN_ID, "13800000000", 1, 0));

        assertThatThrownBy(() -> service.changeStatus(ADMIN_ID, ADMIN_ID, 1))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("自己");

        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("管理员账号不允许在此处禁用")
    void cannotDisableAdminAccount() {
        when(userMapper.selectById(USER_ID)).thenReturn(user(USER_ID, "13800008888", 1, 0));

        assertThatThrownBy(() -> service.changeStatus(ADMIN_ID, USER_ID, 1))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("管理员");

        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("非法状态值 → 400")
    void rejectsIllegalStatus() {
        assertThatThrownBy(() -> service.changeStatus(ADMIN_ID, USER_ID, 2))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("账号状态");

        verify(userMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("禁用不存在的用户 → 404")
    void changeStatusOfMissingUser() {
        when(userMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> service.changeStatus(ADMIN_ID, USER_ID, 1))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户不存在");
    }

    // =========================================================================
    // 修改角色
    // =========================================================================

    @Test
    @DisplayName("升为管理员：改 role + 吊销会话（旧 token 仍是普通用户）")
    void promoteToAdminRevokesSessions() {
        when(userMapper.selectById(USER_ID)).thenReturn(user(USER_ID, "13800008888", 0, 0));

        service.changeRole(ADMIN_ID, USER_ID, 1);

        assertThat(capturedRole()).isEqualTo(1);
        verify(authService).revokeSessions(USER_ID);
    }

    @Test
    @DisplayName("降为普通用户：改 role + 吊销会话（否则旧 token 还能进后台）")
    void demoteToUserRevokesSessions() {
        when(userMapper.selectById(USER_ID)).thenReturn(user(USER_ID, "13800008888", 1, 0));
        when(userMapper.selectCount(any())).thenReturn(2L);

        service.changeRole(ADMIN_ID, USER_ID, 0);

        assertThat(capturedRole()).isEqualTo(0);
        verify(authService).revokeSessions(USER_ID);
    }

    @Test
    @DisplayName("不能修改自己的角色")
    void cannotChangeOwnRole() {
        when(userMapper.selectById(ADMIN_ID)).thenReturn(user(ADMIN_ID, "13800000000", 1, 0));

        assertThatThrownBy(() -> service.changeRole(ADMIN_ID, ADMIN_ID, 0))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("自己");

        verify(userMapper, never()).updateById(any(User.class));
        verify(authService, never()).revokeSessions(anyLong());
    }

    @Test
    @DisplayName("最后一个可用管理员不能降级")
    void cannotDemoteLastActiveAdmin() {
        when(userMapper.selectById(USER_ID)).thenReturn(user(USER_ID, "13800008888", 1, 0));
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(ADMIN_ID, USER_ID, 0))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("至少");

        verify(userMapper, never()).updateById(any(User.class));
        verify(authService, never()).revokeSessions(anyLong());
    }

    @Test
    @DisplayName("角色未变化：幂等，不写库也不吊销")
    void sameRoleIsIdempotent() {
        when(userMapper.selectById(USER_ID)).thenReturn(user(USER_ID, "13800008888", 0, 0));

        service.changeRole(ADMIN_ID, USER_ID, 0);

        verify(userMapper, never()).updateById(any(User.class));
        verify(authService, never()).revokeSessions(anyLong());
    }

    @Test
    @DisplayName("非法角色值 → 400")
    void rejectsIllegalRole() {
        assertThatThrownBy(() -> service.changeRole(ADMIN_ID, USER_ID, 2))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("角色");

        verify(userMapper, never()).selectById(any());
    }

    // =========================================================================
    // 夹具
    // =========================================================================

    private void stubPage(List<User> records, long total) {
        lenient().when(userMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<User> page = inv.getArgument(0);
            page.setRecords(records);
            page.setTotal(total);
            return page;
        });
    }

    /** 取最后一次 updateById 里的 status。 */
    private int capturedStatus() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        return captor.getValue().getStatus();
    }

    /** 取最后一次 updateById 里的 role。 */
    private int capturedRole() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        return captor.getValue().getRole();
    }

    private static User user(long id, String phone, int role, int status) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setNickname("昵称" + id);
        user.setPhone(phone);
        user.setRole(role);
        user.setStatus(status);
        user.setCreatedAt(LocalDateTime.of(2026, 10, 1, 10, 0));
        return user;
    }

    private static Order order(String orderNo, int status, String payAmount) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setStatus(status);
        order.setPayAmount(new BigDecimal(payAmount));
        order.setChannel(0);
        order.setCreatedAt(LocalDateTime.of(2026, 10, 31, 9, 0));
        return order;
    }

    private static UserAddress address(String phone) {
        UserAddress address = new UserAddress();
        address.setId(11L);
        address.setReceiver("张三");
        address.setPhone(phone);
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetail("科技园路 1 号");
        address.setIsDefault(1);
        return address;
    }
}
