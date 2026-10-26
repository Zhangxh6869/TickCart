package com.tickcart.it;

import com.tickcart.common.security.JwtTokenProvider;
import com.tickcart.support.AbstractIntegrationTest;
import com.tickcart.support.TestDataFactory;
import com.tickcart.trade.dto.OrderCreateRequest;
import com.tickcart.trade.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G4 权限闸门 · S1~S4：横向越权、认证、授权。
 *
 * <p>为什么必须走 MockMvc：越权检查发生在 {@code JwtAuthenticationFilter} 与 SecurityConfig
 * 这一层，直接调 Service 会绕过它们。功能测试时你永远只用自己的账号登录，
 * 所以这类漏洞最难被发现——它是电商最高危的问题（能看到别人的订单和地址）。</p>
 *
 * <p><b>同时验证「该放行的放行」</b>：只断言「访问被拒」有个陷阱——如果安全配置写错把
 * 所有人都拒了，测试反而全绿。所以这里加了正向用例（管理员能访问 /api/admin/**），
 * 确保拒绝是因为权限边界，而不是因为配置坏掉。</p>
 */
class AuthorizationIT extends AbstractIntegrationTest {

    private static final String AUTH = HttpHeaders.AUTHORIZATION;
    private static final int ROLE_USER = 0;
    private static final int ROLE_ADMIN = 1;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private TestDataFactory data;

    @BeforeEach
    void setUp() {
        data = new TestDataFactory(jdbc);
        cleanBusinessTables();
    }

    @Test
    @DisplayName("S1 用户 A 不能读取用户 B 的订单详情")
    void cannotReadOthersOrder() throws Exception {
        TestDataFactory.BuyerFixture buyerB = data.createBuyer(10, 1);
        String orderNoB = placeOrder(buyerB);

        long userA = data.createUser();

        mockMvc.perform(get("/api/orders/{orderNo}", orderNoB).header(AUTH, tokenOf(userA, ROLE_USER)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("S1 用户 A 不能取消用户 B 的订单")
    void cannotCancelOthersOrder() throws Exception {
        TestDataFactory.BuyerFixture buyerB = data.createBuyer(10, 1);
        String orderNoB = placeOrder(buyerB);

        long userA = data.createUser();

        mockMvc.perform(put("/api/orders/{orderNo}/cancel", orderNoB).header(AUTH, tokenOf(userA, ROLE_USER)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("S1 用户 A 不能读取用户 B 的收货地址")
    void cannotReadOthersAddress() throws Exception {
        TestDataFactory.BuyerFixture buyerB = data.createBuyer(10, 1);
        long userA = data.createUser();

        mockMvc.perform(get("/api/addresses/{id}", buyerB.addressId())
                        .header(AUTH, tokenOf(userA, ROLE_USER)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("S2 未携带令牌访问订单列表 → 401")
    void anonymousCannotListOrders() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("S4 伪造的令牌 → 401")
    void forgedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/orders").header(AUTH, "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("S3 普通用户访问后台接口 → 403")
    void normalUserCannotAccessAdminApi() throws Exception {
        long userA = data.createUser();

        mockMvc.perform(get("/api/admin/orders").header(AUTH, tokenOf(userA, ROLE_USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("正向对照：管理员访问后台接口 → 放行（证明拒绝不是因为配置全拒）")
    void adminCanAccessAdminApi() throws Exception {
        long adminId = data.createUser();

        mockMvc.perform(get("/api/admin/orders").header(AUTH, tokenOf(adminId, ROLE_ADMIN)))
                .andExpect(status().isOk());
    }

    /** 为买家下单，返回订单号。 */
    private String placeOrder(TestDataFactory.BuyerFixture buyer) {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(buyer.addressId());
        return orderService.create(buyer.userId(), req, null).getOrderNo();
    }

    /** 生成合法 JWT（走真实的 JwtAuthenticationFilter，不是 mock 认证）。 */
    private String tokenOf(long userId, int role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId, "it_user_" + userId, role);
    }
}
