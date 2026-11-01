package com.tickcart.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tickcart.admin.dto.AdminUserQuery;
import com.tickcart.admin.vo.AdminUserAddressVO;
import com.tickcart.admin.vo.AdminUserDetailVO;
import com.tickcart.admin.vo.AdminUserOrderVO;
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
import com.tickcart.trade.enums.OrderStatus;
import com.tickcart.trade.mapper.OrderMapper;
import com.tickcart.trade.mapper.RefundRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台用户管理服务。
 *
 * <p>只做「看得见 + 停得掉」两件事：</p>
 * <ul>
 *   <li>看得见：按手机号/昵称/ID 找到人，顺带把订单与消费统计拼上（一页只查一次库）</li>
 *   <li>停得掉：启用/禁用账号，禁用立即吊销会话</li>
 * </ul>
 *
 * <p><b>刻意不提供</b>改密码、改角色、删除用户：前者等于客服可以冒充任意用户，
 * 中者是误提权入口，后者会让历史订单的 {@code user_id} 悬空。</p>
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    /** 用户状态：正常 / 禁用 */
    private static final int STATUS_NORMAL = 0;
    private static final int STATUS_DISABLED = 1;
    /** 角色：管理员 */
    private static final int ROLE_ADMIN = 1;
    /** 详情里带几笔最近订单 */
    private static final int RECENT_ORDER_LIMIT = 5;

    /**
     * 计入「已付款 / 累计消费」的订单状态。
     *
     * <p>退款中（6）与已退款（7）不算：退了款还算消费，运营看到的人均消费就是假的。
     * 待付款（0）、已取消（4）、已超时（5）自然也不算。</p>
     */
    private static final List<Integer> PAID_STATUS_CODES = List.of(
            OrderStatus.PENDING_SHIPMENT.getCode(),
            OrderStatus.PENDING_RECEIPT.getCode(),
            OrderStatus.COMPLETED.getCode());

    /** 拼进 SQL 的状态列表，如 {@code 1,2,3}；值来自上面的常量，无注入风险 */
    private static final String PAID_STATUS_SQL = PAID_STATUS_CODES.stream()
            .map(String::valueOf)
            .reduce((a, b) -> a + "," + b)
            .orElse("1,2,3");

    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final UserAddressMapper userAddressMapper;
    private final AuthService authService;

    public AdminUserService(UserMapper userMapper,
                            OrderMapper orderMapper,
                            RefundRecordMapper refundRecordMapper,
                            UserAddressMapper userAddressMapper,
                            AuthService authService) {
        this.userMapper = userMapper;
        this.orderMapper = orderMapper;
        this.refundRecordMapper = refundRecordMapper;
        this.userAddressMapper = userAddressMapper;
        this.authService = authService;
    }

    // =========================================================================
    // 列表
    // =========================================================================

    /**
     * 用户分页。
     *
     * <p>统计走「先分页拿 20 个 id，再一次 GROUP BY」：逐行查会是 20 次额外查询，
     * 后台列表翻页很频繁，N+1 会被放大成秒级响应。</p>
     */
    public PageResult<AdminUserVO> page(AdminUserQuery query) {
        IPage<User> result = userMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), buildWrapper(query));

        List<User> users = result.getRecords();
        Map<Long, UserStat> stats = statsOf(users.stream().map(User::getId).toList());
        List<AdminUserVO> rows = users.stream()
                .map(user -> toRow(user, stats.getOrDefault(user.getId(), UserStat.EMPTY)))
                .toList();

        return new PageResult<>(rows, result.getTotal(), result.getCurrent(),
                result.getSize(), result.getPages());
    }

    private LambdaQueryWrapper<User> buildWrapper(AdminUserQuery query) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
        if (keyword != null) {
            wrapper.and(q -> {
                q.like(User::getUsername, keyword)
                        .or().like(User::getNickname, keyword)
                        .or().like(User::getPhone, keyword);
                // 用户来电常只报一个数字 ID，不按 ID 命中就会「查无此人」
                Long asId = parseId(keyword);
                if (asId != null) {
                    q.or().eq(User::getId, asId);
                }
            });
        }
        if (query.getStatus() != null) {
            wrapper.eq(User::getStatus, query.getStatus());
        }
        LocalDate begin = query.getBeginDate();
        if (begin != null) {
            wrapper.ge(User::getCreatedAt, begin.atStartOfDay());
        }
        LocalDate end = query.getEndDate();
        if (end != null) {
            // 用「次日 0 点」做开区间：直接 le end 会漏掉当天 00:00 之后的记录
            wrapper.lt(User::getCreatedAt, end.plusDays(1).atStartOfDay());
        }

        boolean asc = "asc".equalsIgnoreCase(query.getOrder());
        if ("id".equalsIgnoreCase(query.getSortBy())) {
            wrapper.orderBy(true, asc, User::getId);
        } else {
            wrapper.orderBy(true, asc, User::getCreatedAt);
        }
        return wrapper;
    }

    // =========================================================================
    // 详情
    // =========================================================================

    /** 用户详情：明文手机号 + 订单统计 + 最近订单 + 收货地址。 */
    public AdminUserDetailVO detail(Long userId) {
        User user = requireUser(userId);

        AdminUserDetailVO vo = new AdminUserDetailVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setGender(user.getGender());
        vo.setRole(user.getRole());
        vo.setStatus(normalizedStatus(user));
        vo.setCreatedAt(user.getCreatedAt());

        vo.setOrderCount(orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)));
        vo.setPaidOrderCount(orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .in(Order::getStatus, PAID_STATUS_CODES)));
        vo.setPaidAmount(sumPaidAmount(userId));
        vo.setLastOrderAt(lastOrderAt(userId));
        vo.setRefundCount(refundRecordMapper.selectCount(new LambdaQueryWrapper<RefundRecord>()
                .eq(RefundRecord::getUserId, userId)));
        vo.setRecentOrders(recentOrders(userId));
        vo.setAddresses(addresses(userId));
        return vo;
    }

    private BigDecimal sumPaidAmount(Long userId) {
        List<Object> objs = orderMapper.selectObjs(new QueryWrapper<Order>()
                .select("COALESCE(SUM(pay_amount), 0)")
                .eq("user_id", userId)
                .in("status", PAID_STATUS_CODES));
        if (objs.isEmpty() || objs.get(0) == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(objs.get(0).toString());
    }

    private LocalDateTime lastOrderAt(Long userId) {
        List<Object> objs = orderMapper.selectObjs(new QueryWrapper<Order>()
                .select("MAX(created_at)")
                .eq("user_id", userId));
        return objs.isEmpty() ? null : toLocalDateTime(objs.get(0));
    }

    private List<AdminUserOrderVO> recentOrders(Long userId) {
        IPage<Order> page = orderMapper.selectPage(new Page<>(1, RECENT_ORDER_LIMIT),
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .orderByDesc(Order::getCreatedAt));
        return page.getRecords().stream().map(order -> {
            AdminUserOrderVO vo = new AdminUserOrderVO();
            vo.setOrderNo(order.getOrderNo());
            vo.setStatus(order.getStatus());
            vo.setStatusLabel(statusLabel(order.getStatus()));
            vo.setPayAmount(order.getPayAmount());
            vo.setChannel(order.getChannel());
            vo.setCreatedAt(order.getCreatedAt());
            return vo;
        }).toList();
    }

    private List<AdminUserAddressVO> addresses(Long userId) {
        List<UserAddress> list = userAddressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault));
        return list.stream().map(address -> {
            AdminUserAddressVO vo = new AdminUserAddressVO();
            vo.setId(address.getId());
            vo.setReceiver(address.getReceiver());
            vo.setPhone(maskPhone(address.getPhone()));
            vo.setFullAddress(address.fullAddress());
            vo.setIsDefault(address.getIsDefault());
            return vo;
        }).toList();
    }

    // =========================================================================
    // 启用 / 禁用
    // =========================================================================

    /**
     * 修改账号状态。
     *
     * <p>禁用必须<b>立即生效</b>：只改库的话，已登录用户的 access token 还能用满有效期，
     * 「禁用」等于「下次登录才挡」。所以这里连带吊销全部会话。</p>
     *
     * @param operatorId 当前操作人（管理员）ID，用于拦「禁用自己」
     * @param userId     目标用户
     * @param status     0 正常 / 1 禁用
     */
    @Transactional
    public void changeStatus(Long operatorId, Long userId, Integer status) {
        if (status == null || (status != STATUS_NORMAL && status != STATUS_DISABLED)) {
            throw BizException.badRequest("账号状态只能是 0（正常）或 1（禁用）");
        }
        User user = requireUser(userId);

        if (userId.equals(operatorId)) {
            throw BizException.badRequest("不能修改自己的账号状态");
        }
        if (status == STATUS_DISABLED && user.getRole() != null && user.getRole() == ROLE_ADMIN) {
            // 管理员只有一个时，禁用掉就没人能再登进来改回来
            throw BizException.badRequest("管理员账号不支持在此处禁用");
        }

        if (normalizedStatus(user) == status) {
            // 幂等：重复点禁用不该重复吊销、也不该报错
            return;
        }

        User update = new User();
        update.setId(userId);
        update.setStatus(status);
        userMapper.updateById(update);

        if (status == STATUS_DISABLED) {
            authService.revokeSessions(userId);
            log.info("管理员 {} 禁用了用户 {}，已吊销其全部会话", operatorId, userId);
        } else {
            log.info("管理员 {} 启用了用户 {}", operatorId, userId);
        }
    }

    // =========================================================================
    // 内部方法
    // =========================================================================

    private User requireUser(Long userId) {
        if (userId == null) {
            throw BizException.badRequest("用户ID不能为空");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    private AdminUserVO toRow(User user, UserStat stat) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(maskPhone(user.getPhone()));
        vo.setGender(user.getGender());
        vo.setRole(user.getRole());
        vo.setStatus(normalizedStatus(user));
        vo.setOrderCount(stat.orderCount());
        vo.setPaidOrderCount(stat.paidOrderCount());
        vo.setPaidAmount(stat.paidAmount());
        vo.setLastOrderAt(stat.lastOrderAt());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    /**
     * 一次查出这一页用户的订单统计。
     *
     * <p>用 {@code selectMaps} 而不是逐用户 {@code selectCount}：
     * 20 行数据本该 1 次查询，逐行查就是 60 次。</p>
     */
    private Map<Long, UserStat> statsOf(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<Order> wrapper = new QueryWrapper<Order>()
                .select("user_id AS uid",
                        "COUNT(*) AS order_count",
                        "SUM(CASE WHEN status IN (" + PAID_STATUS_SQL + ") THEN 1 ELSE 0 END) AS paid_order_count",
                        "COALESCE(SUM(CASE WHEN status IN (" + PAID_STATUS_SQL
                                + ") THEN pay_amount ELSE 0 END), 0) AS paid_amount",
                        "MAX(created_at) AS last_order_at")
                .in("user_id", userIds)
                .groupBy("user_id");

        Map<Long, UserStat> stats = new HashMap<>();
        for (Map<String, Object> row : orderMapper.selectMaps(wrapper)) {
            Object rawUid = pick(row, "uid", "user_id");
            if (rawUid == null) {
                continue;
            }
            stats.put(toLong(rawUid), new UserStat(
                    toLong(pick(row, "order_count", "orderCount")),
                    toLong(pick(row, "paid_order_count", "paidOrderCount")),
                    toDecimal(pick(row, "paid_amount", "paidAmount")),
                    toLocalDateTime(pick(row, "last_order_at", "lastOrderAt"))));
        }
        return stats;
    }

    /**
     * 取列值，兼容别名大小写差异。
     *
     * <p>MySQL 会把未加引号的别名按原样返回，但不同驱动/版本对大小写的处理并不一致，
     * 少一个 fallback 就会静默取到 null（列表上全是 0 单 0 元）。</p>
     */
    private static Object pick(Map<String, Object> row, String first, String second) {
        Object value = row.get(first);
        return value != null ? value : row.get(second);
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return new Timestamp(date.getTime()).toLocalDateTime();
        }
        return null;
    }

    /** 关键字是纯数字时按用户 ID 精确匹配；超长数字（手机号 11 位也可能撞）交给其它条件。 */
    private static Long parseId(String keyword) {
        if (keyword.length() > 18) {
            return null;
        }
        for (int i = 0; i < keyword.length(); i++) {
            if (!Character.isDigit(keyword.charAt(i))) {
                return null;
            }
        }
        try {
            return Long.parseLong(keyword);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 手机号脱敏：列表是随手截图的位置，明文只在详情给。 */
    private static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return phone;
        }
        if (phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static int normalizedStatus(User user) {
        return user.getStatus() == null ? STATUS_NORMAL : user.getStatus();
    }

    private static String statusLabel(Integer code) {
        if (code == null) {
            return "";
        }
        try {
            return OrderStatus.of(code).getLabel();
        } catch (BizException e) {
            return "未知状态";
        }
    }

    /** 单个用户的订单统计。 */
    private record UserStat(long orderCount, long paidOrderCount, BigDecimal paidAmount,
                            LocalDateTime lastOrderAt) {

        /** 没有订单的用户（列表里的大多数） */
        static final UserStat EMPTY = new UserStat(0L, 0L, BigDecimal.ZERO, null);
    }
}
