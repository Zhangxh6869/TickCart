# SKU 全量重建导致「库存回补静默失效」——根因与修复

> 状态：**已修复**，两个 commit：
> ① `4d08aa0` SKU 增量同步 + 回补未命中告警
> ② `product.stock` 与 SKU 合计同步（见 §七）
> 涉及：`ProductService#syncSkus`（新）、`InventoryService`（汇总同步 + 告警）、`ProductServiceTest`（+6 用例）
> 关联文档：`docs/订单管理界面重设计方案.md` §六.10（问题是在做订单管理页时发现的）

## 一、现象

关闭一张「待付款」订单，接口返回 `success`、订单状态也确实变成「已取消」，**但库存没回来**。

用 MySQL general log 抓到当时的真实语句：

```sql
UPDATE product_sku  SET stock = stock + 1   WHERE deleted=0     AND (id = 34)
UPDATE t_order      SET ..., status=4, cancel_time='...'  WHERE id=18 AND deleted=0
```

订单更新成功，库存更新**影响 0 行** —— 因为 `sku 34` 是 `deleted=1`。

## 二、根因链

| 步 | 发生了什么 | 代码位置 |
|---|---|---|
| ① | 后台商品抽屉是**整表单提交**，改一个标题也会带上 `skus` | `ProductManageView.vue` 的 `onSubmit` → `updateProduct(id, form.value)` |
| ② | 服务端只要看到 `skus != null` 就**全量重建**：旧 SKU 全部逻辑删除，新 SKU 重新 insert（id 全新） | 原 `ProductService.update` + `saveSkus` |
| ③ | 未完成订单的 `order_item.sku_id` 仍指向旧 id；取消/超时/后台关单时 `restore` 的 UPDATE 被 MyBatis-Plus 自动补上 `deleted=0`，命中 0 行 | `InventoryService.restore` |

**为什么静默**：`restore()` 丢弃了 `update` 的返回值；而同文件的 `deduct()` 会检查 `rows == 0` 并抛「库存不足」。**只有回补这一侧不看影响行数**。

商品 1（iPhone17）的库内现状是直接证据——同一个「黑色 256G」有 **8 代**：

| 世代 | SKU id | deleted |
|---|---|---|
| 1~7 代 | 1-3 / 15-17 / 18-20 / 21-23 / 24-26 / 31-33 / **34-36** | 全是 1 |
| 第 8 代（当前） | 39 / 40 / 41 | 0 |

`product_sku` 上没有 `(product_id, spec_value)` 唯一索引，所以 8 代同名规格能和平共处 —— 这也是这个 bug 一直没被数据约束顶出来的原因。

## 三、影响面（修复前实测）

| 项 | 数值 |
|---|---|
| 存在已软删 SKU 的商品 | 34 个 |
| `product_sku` 已软删行 | 106 / 489 |
| `order_item` 指向已软删 SKU 的明细 | 25 条（171 条里占 15%） |
| **已取消/已超时、库存没补回来的** | **12 单 / 20 件**（已发生，无法自动追回） |
| 当时还挂着的待付款单（sku 34） | 2 单 |

三条路径（用户取消 / 超时关单 / 后台关闭）都走 `restoreStock()`，所以不是订单管理页独有的问题。

## 四、修复

### 4.1 `syncSkus` 取代「全删重插」

```java
// 带 id 且属于本商品 → 就地 update（id 不变）
// 不带 id            → insert
// 请求里没出现的旧行  → 逻辑删除
// 带了别人的 SKU id   → 400「SKU 不属于该商品，请刷新后重试」
```

要点：

1. **★ 就地更新必须用显式 `set`，不能用 `updateById`**：MP 默认 `NOT_NULL` 策略会把 null 字段从 SET 里剔掉，导致「清空规格值 / 清空 SKU 图」写不进去。（与分类预警线那次是同一个坑。）
2. **★ 跨商品 id 直接拒绝**：`existing` 只装了本商品的 SKU，传了别人的 id 就查不到；此时若静默改成 insert，会在本商品下造出一条规格重复的行。
3. 新建商品复用同一个方法（`existing` 为空 → 全是 insert），避免两条路径分叉。

### 4.2 `restore` 命中 0 行要留痕

```java
int rows = productSkuMapper.update(null, ...);
if (rows == 0) {
    log.warn("库存回补未命中 SKU（可能已被逻辑删除）：productId={}, skuId={}, quantity={}", ...);
}
```

不改变语义（回补到一条已删行本来就没有意义），但把「静默丢库存」变成可观测事件。存量数据里已经有指向旧 SKU 的订单，这条 warn 会继续报出来 —— 那是真实情况，不该藏。

## 五、验证

| 项 | 结果 |
|---|---|
| 单元测试 | ✅ 400 通过（商品服务 51 → 56，新增 5 条：就地更新 / 删除缺失行 / 新增无 id / 拒绝他人 id / 不带 skus 不碰表） |
| 整表单保存后 SKU 是否换 id | ✅ 商品 1 保存前后都是 `39/40/41`，SKU 行数仍 24（原来会新增 3 行、旧 3 行变 deleted=1） |
| **端到端** | ✅ 下单（sku 39：95→94）→ 保存商品 → 关闭订单 → **94→95 正常回补**（修复前这一步不会动） |
| 测试数据 | ✅ 已还原：订单 172 单（2/48/4/10/101/7），SKU 39/40/41 = 95/58/39，`product.stock` 195 |

## 六、遗留（本轮没动）

| 项 | 说明 |
|---|---|
| 2 个待付款订单仍指向已删的 sku 34 | 修复只防未来，不修历史。这两单关闭时库存仍补不回来；要补得手工 `UPDATE product_sku SET stock = stock + 1 WHERE id = 39`（同一规格的当前行） |
| 存量 106 行已删 SKU | 不影响功能，不必清理（清理反而会破坏历史订单的 `sku_id` 追溯） |
| 编辑 SKU 时的并发 | 两个管理员同时编辑同一商品，后保存的会覆盖先保存的 SKU 值（无乐观锁）。当前单管理员场景可接受 |

---

## 七、顺带修掉的第二个口径问题：`product.stock` 不随下单递减

### 7.1 现象

库存其实有两本账：

| 字段 | 角色 | 谁在改它 |
|---|---|---|
| `product_sku.stock` | **真账**，能不能卖看它 | 下单扣、取消补 |
| `product.stock` | 派生汇总值，商品列表 / 库存告急 / 分类页告急数都读它 | 只有「保存商品」「单独改 SKU 库存」会重算 |

下单只动前者，于是后者变成一张**只有保存时才刷新**的旧快照。

实测（商品 302 精选牛油果，2 件）：

| 时刻 | `product.stock` | SKU 合计 |
|---|---|---|
| 下单前 | 198 | 198 |
| 下单 2 件 | **198（没动）** | **196** |
| 取消后 | 198 | 198 |

后果不是超卖（扣的是 SKU），而是**后台看到的数字偏大**，以及**漏报告急**：
某商品剩 8 件、分类预警线 8，卖 1 件后真实 7（该报），但 `product.stock` 仍是 8 → 不报。

修复前存量：162 个有规格商品里 **7 个已漂**，最大差 9 件（针织衫 213 vs 204），合计虚高 18 件。

### 7.2 修法（3 处）

**① `InventoryService.deduct` / `restore` 同步汇总值**

```java
if (hasSku(skuId)) {
    int rows = productSkuMapper.update(... stock = stock - n ...);   // 真账，带 stock >= n
    if (rows == 0) throw BizException.badRequest("库存不足");
    adjustProductStock(productId, -quantity);                        // 汇总跟着动
    return;
}
// 无规格商品：product.stock 就是真账，只扣这一次
```

★ 两个必须守住的点：
- **无规格商品不能扣两次** —— 必须按 `hasSku` 分支，两个分支各自只更新一次（测试里用 `verify(...)` 的「恰好一次」语义锁住）。
- **`restore` 时 SKU 没命中就不要加汇总值** —— 否则汇总会比真实合计更大，从「少算」变成「多算」，更坏。宁可两边都不动，只打 warn。

**② `ProductService.update` 的总库存改成从库里重算**

```java
syncSkus(id, req.getSkus(), product.getPrice());
product.setStock(skuStockTotal(id));   // 原来是 sumSkuStock(req.getSkus())
```

去掉「用请求参数算一遍」的第二套算法，`product.stock` 只认库里的 SKU 合计。

**③ 存量校准（一次性）**

```sql
UPDATE product p
JOIN (SELECT product_id, SUM(stock) t FROM product_sku WHERE deleted=0 GROUP BY product_id) s
  ON s.product_id = p.id
SET p.stock = s.t
WHERE p.deleted = 0 AND p.stock <> s.t;
```

实测校准 7 行，校准后漂移 0：iPhone17 195 → 192、针织衫 213 → 204。

### 7.3 验证

| 项 | 结果 |
|---|---|
| 单元测试 | ✅ 403 通过（库存服务 6 → 8：有 SKU 同步汇总 / SKU 扣失败不动汇总 / 回补未命中不加汇总 / 无规格只扣一次；商品服务 56 → 57：总库存取库里合计） |
| 端到端 | ✅ 下单 2 件：`product.stock` 198 → **196**、SKU 合计 198 → 196（同步）；取消后双双回到 198 |
| 存量 | ✅ 漂移 7 → 0 |

### 7.4 不改的地方与已知风险

- 商品列表、告急筛选、分类页的 SQL **一行没动**，它们继续读 `product.stock`，只是这个值从此准确了
- 前台详情页不动（优先显示选中规格的 SKU 库存，本来就是对的）
- 秒杀完全不经过 `InventoryService`（走 Redis + 活动库存），所以它既不扣 SKU 也不扣汇总 —— 两边一致，不引入新账
- `product.stock` 理论上可能变负（只在有人手改库时）。**刻意不加 `GREATEST(...,0)`**：负数说明账真错了，应该暴露而不是掩盖
