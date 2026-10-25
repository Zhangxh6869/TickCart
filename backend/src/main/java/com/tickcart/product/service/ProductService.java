package com.tickcart.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tickcart.common.api.PageResult;
import com.tickcart.common.cache.CacheKeys;
import com.tickcart.common.cache.CacheProperties;
import com.tickcart.common.cache.CacheService;
import com.tickcart.common.exception.BizException;
import com.tickcart.product.dto.ProductQuery;
import com.tickcart.product.dto.ProductSaveRequest;
import com.tickcart.product.entity.Category;
import com.tickcart.product.entity.Product;
import com.tickcart.product.entity.ProductImage;
import com.tickcart.product.entity.ProductSku;
import com.tickcart.product.mapper.CategoryMapper;
import com.tickcart.product.mapper.ProductImageMapper;
import com.tickcart.product.mapper.ProductMapper;
import com.tickcart.product.mapper.ProductSkuMapper;
import com.tickcart.product.vo.LowStockItem;
import com.tickcart.product.vo.LowStockReport;
import com.tickcart.product.vo.ProductDetail;
import com.tickcart.product.vo.ProductListItem;
import com.tickcart.search.service.SynonymService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品服务：CRUD、上下架、改库存、列表/详情查询。
 */
@Service
public class ProductService {

    /** 状态：上架 / 下架 */
    private static final int STATUS_ON = 1;
    private static final int STATUS_OFF = 0;

    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductImageMapper imageMapper;
    private final CategoryMapper categoryMapper;
    private final SynonymService synonymService;
    private final CacheService cacheService;
    private final CacheProperties cacheProperties;
    private final StockWarnService stockWarnService;
    /** 随机分页时最多参与打乱的商品数，见 {@code tickcart.product.random-pool-limit} */
    private final int randomPoolLimit;
    /** 补货待办一次最多扫多少个在售商品，见 {@code tickcart.product.low-stock-scan-limit} */
    private final int lowStockScanLimit;

    public ProductService(ProductMapper productMapper,
                          ProductSkuMapper skuMapper,
                          ProductImageMapper imageMapper,
                          CategoryMapper categoryMapper,
                          SynonymService synonymService,
                          CacheService cacheService,
                          CacheProperties cacheProperties,
                          StockWarnService stockWarnService,
                          @Value("${tickcart.product.random-pool-limit:2000}") int randomPoolLimit,
                          @Value("${tickcart.product.low-stock-scan-limit:2000}") int lowStockScanLimit) {
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.imageMapper = imageMapper;
        this.categoryMapper = categoryMapper;
        this.synonymService = synonymService;
        this.cacheService = cacheService;
        this.cacheProperties = cacheProperties;
        this.stockWarnService = stockWarnService;
        this.randomPoolLimit = Math.max(1, randomPoolLimit);
        this.lowStockScanLimit = Math.max(1, lowStockScanLimit);
    }

    /**
     * 商品分页列表。
     *
     * <p>查询条件带 {@code seed} 时走随机分页（见 {@link #randomPage}），否则按常规排序分页。</p>
     *
     * @param onlyOnSale 前台调用置 true，强制只查上架商品
     */
    public PageResult<ProductListItem> page(ProductQuery query, boolean onlyOnSale) {
        if (query.getSeed() != null) {
            return randomPage(query, onlyOnSale);
        }

        LambdaQueryWrapper<Product> wrapper = filterWrapper(query, onlyOnSale);
        applySort(wrapper, query.getSortBy(), query.getOrder());

        Page<Product> page = productMapper.selectPage(
                Page.of(query.getPage(), query.getSize()), wrapper);

        List<ProductListItem> items = page.getRecords().stream()
                .map(ProductListItem::from)
                .toList();
        markStockLow(items);
        return new PageResult<>(items, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    /**
     * 给列表项打「库存告急」标记。
     *
     * <p>阈值按分类差异化，所以不能在前端用常量算；这里一次性取回
     * {@code categoryId → 有效阈值}（20 行量级）再逐条比对，避免逐行回查。
     * 详情页不标这个 —— 它展示的是 SKU 级库存，「某个尺码为 0」用绝对判断更准。</p>
     */
    private void markStockLow(List<ProductListItem> items) {
        if (items.isEmpty()) {
            return;
        }
        Map<Long, Integer> thresholds = stockWarnService.effectiveThresholds();
        for (ProductListItem item : items) {
            item.setStockLow(stockWarnService.isLow(item.getStock(), item.getCategoryId(), thresholds));
        }
    }

    /**
     * 随机分页：同一 seed 下顺序稳定，因此翻页既不重复也不漏项。
     *
     * <p>为什么不在数据库里随机：{@code ORDER BY RAND()} 每页各自随机，翻页必然重复/漏项；
     * {@code ORDER BY RAND(seed)} 虽然带种子，但取值依赖行的读取顺序，执行计划一变顺序就变，
     * 同样不能保证翻页一致。所以这里取回候选 id、在应用层用 seed 做确定性洗牌，再切出当前页。</p>
     *
     * <p><b>代价说明</b>：会取回符合条件的 id（上限 {@code tickcart.product.random-pool-limit}，
     * 默认 2000），适合演示数据量（几十~几千条）。当前库 647 个商品时，拉 id ≈ 5KB、
     * 洗牌 &lt;1ms，不构成瓶颈。</p>
     *
     * <p><b>触发改造的条件</b>（见 {@code docs/读放大与可靠性收尾设计.md} §4）：
     * 商品数 &gt; 5000，或该接口 P95 &gt; 50ms —— 届时改为落库随机权重列 + 索引，
     * 用「按权重排序 + 游标翻页」替代本实现。</p>
     */
    private PageResult<ProductListItem> randomPage(ProductQuery query, boolean onlyOnSale) {
        List<Long> ids = productMapper.selectList(
                        filterWrapper(query, onlyOnSale)
                                .select(Product::getId)
                                .orderByAsc(Product::getId)
                                .last("LIMIT " + randomPoolLimit))
                .stream()
                .map(Product::getId)
                .toList();

        List<Long> shuffled = new ArrayList<>(ids);
        Collections.shuffle(shuffled, new Random(query.getSeed()));

        int size = query.getSize();
        int from = Math.min((query.getPage() - 1) * size, shuffled.size());
        int to = Math.min(from + size, shuffled.size());
        List<Long> window = shuffled.subList(from, to);
        long pages = (shuffled.size() + size - 1L) / size;
        if (window.isEmpty()) {
            return new PageResult<>(List.of(), shuffled.size(), query.getPage(), size, pages);
        }

        // selectBatchIds 不保证返回顺序，按洗牌后的窗口顺序重排，翻页顺序才稳定
        Map<Long, Product> byId = productMapper.selectBatchIds(window).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<ProductListItem> items = window.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(ProductListItem::from)
                .toList();
        markStockLow(items);

        return new PageResult<>(items, shuffled.size(), query.getPage(), size, pages);
    }

    /**
     * LIKE 的转义字符。
     *
     * <p>特意不用反斜杠：MySQL 的字符串字面量里 {@code '\'} 本身是不完整的
     * （反斜杠会转义右引号），要写 {@code '\\'} 才行；而 {@code NO_BACKSLASH_ESCAPES}
     * 模式下 {@code '\\'} 又会变成两个反斜杠。换个字符就完全绕开这套引号/模式的坑。</p>
     */
    private static final String LIKE_ESCAPE = "!";

    /**
     * 组装筛选条件（关键字 / 分类 / 状态），常规分页与随机分页共用。
     *
     * <p><b>关键字匹配「商品名 + 副标题 + 搜索关键词」三个字段。</b>
     * 副标题放的是卖点/属性（「柔软亲肤，简约百搭」「120Hz 高刷，快充 67W」），
     * 用户搜的往往正是这些词，而它们几乎不会出现在商品名里 —— 只搜 name 时
     * 「亲肤」「百搭」「通勤」「快充」这类词一律搜不到（实测 30 个常见词里 7 个命中 0）。
     * 副标题提供了与商品名相当的可搜文本量（本库 647 字 vs 583 字），不纳入等于白丢一半。</p>
     *
     * <p>前两个字段都是「商品自带的文本」，覆盖不到**品类词**：搜「裤子」不会命中
     * 「高腰直筒牛仔裤」，搜「运动」也不会命中同分类的「轻量跳绳」。所以再加
     * {@code search_keywords} 一路人工维护的召回词（见 V15 迁移）。</p>
     *
     * <p><b>关键字必须转义通配符。</b> {@code LambdaQueryWrapper.like()} 只做
     * {@code '%' + 值 + '%'} 拼接，不会处理用户输入里的 {@code %} 与 {@code _} ——
     * 它们是 LIKE 的通配符。实测 {@code keyword=%} 会把**全部商品**都查出来
     * （搜「100%」同理），属于明确的搜索行为错误，所以这里用
     * {@code apply} + 显式 {@code ESCAPE}。</p>
     *
     * <p>注意：{@code subtitle} 列可为 NULL，{@code NULL LIKE '%x%'} 结果为 NULL（不为真），
     * 但外层是 OR，不会因此漏掉 name 命中的行。</p>
     *
     * <p><b>关键字会按同义词展开。</b>搜「苹果手机」时除了匹配「苹果手机」，还会连同义词
     * （iPhone）一起召回，见 {@link SynonymService}。展开出的每个词同样要转义通配符。</p>
     */
    private LambdaQueryWrapper<Product> filterWrapper(ProductQuery query, boolean onlyOnSale) {
        String keyword = query.getKeyword() == null ? "" : query.getKeyword().trim();

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(query.getCategoryId() != null, Product::getCategoryId, query.getCategoryId());

        if (StringUtils.hasText(keyword)) {
            applyKeyword(wrapper, keyword);
        }

        if (onlyOnSale) {
            wrapper.eq(Product::getStatus, STATUS_ON);
        } else if (query.getStatus() != null) {
            wrapper.eq(Product::getStatus, query.getStatus());
        }
        return wrapper;
    }

    /**
     * 关键字条件：把关键词按同义词展开成若干「展开词」，逐词 OR；每个词都同时匹配
     * 「商品名 + 副标题 + 搜索关键词」三个字段；整组用一层括号包住，
     * 避免与分类/状态条件的 AND 抢优先级。
     *
     * <p>展开词由 {@link SynonymService} 提供；无同义词时只含原关键词，SQL 与引入同义词前一致。
     * 每个展开词都要独立转义 LIKE 通配符（见 {@link #escapeLike}）。</p>
     *
     * <p><b>{@code search_keywords} 这一路是「品类词召回」。</b>只搜 name + subtitle 时，
     * 搜「裤子」永远 0 结果（库里只有「高腰直筒牛仔裤」，没有哪个商品名含「裤子」），
     * 搜「运动」也搜不到同分类的跳绳/篮球 —— 因为分类名不参与匹配。该列存的就是
     * 「用户会搜、但商品名里没有」的品类词与别名，见 V15 迁移。</p>
     */
    private void applyKeyword(LambdaQueryWrapper<Product> wrapper, String keyword) {
        List<String> terms = synonymService.expand(keyword);
        if (terms == null || terms.isEmpty()) {
            terms = List.of(keyword);
        }

        StringBuilder sql = new StringBuilder("(");
        Object[] args = new Object[terms.size() * 3];
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            int a = i * 3;
            sql.append("(name LIKE {").append(a).append("} ESCAPE '").append(LIKE_ESCAPE)
                    .append("' OR subtitle LIKE {").append(a + 1).append("} ESCAPE '")
                    .append(LIKE_ESCAPE).append("' OR search_keywords LIKE {").append(a + 2)
                    .append("} ESCAPE '").append(LIKE_ESCAPE).append("')");
            String pattern = "%" + escapeLike(terms.get(i)) + "%";
            args[a] = pattern;
            args[a + 1] = pattern;
            args[a + 2] = pattern;
        }
        sql.append(")");
        wrapper.apply(sql.toString(), args);
    }

    /**
     * 把用户输入里的 LIKE 通配符转义成字面量。
     *
     * <p>转义字符自身必须先转义，否则 {@code !%} 这种输入会被解析成「转义符 + 通配符」，
     * 用户就又能注入通配符了。</p>
     */
    private static String escapeLike(String value) {
        return value
                .replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }

    /**
     * 商品详情（含 SKU、图集、分类名）。
     *
     * <p>走读缓存：一次详情原本要打 4 条 SQL（product / product_sku / product_image / category），
     * 命中后为 0 条。库存也在缓存里，允许 TTL 内的短暂陈旧 —— 权威校验在下单时的
     * 条件 UPDATE（{@code WHERE stock >= n}），详情页只做展示。</p>
     */
    public ProductDetail detail(Long id) {
        ProductDetail detail = cacheService.get(
                CacheKeys.productDetail(id),
                cacheProperties.getProductDetailTtlSeconds(),
                ProductDetail.class,
                () -> loadDetail(id));
        if (detail == null) {
            throw BizException.notFound("商品不存在");
        }
        return detail;
    }

    /**
     * 商品详情（绕过缓存，直查 DB）。
     *
     * <p>后台编辑弹窗用：避免拿到 TTL 内的旧数据，保存后覆盖掉刚被别人改过的字段。</p>
     */
    public ProductDetail detailFresh(Long id) {
        ProductDetail detail = loadDetail(id);
        if (detail == null) {
            throw BizException.notFound("商品不存在");
        }
        return detail;
    }

    /**
     * 直查 DB 组装详情。
     *
     * <p><b>商品不存在时返回 null 而不是抛异常</b>：只有返回 null，缓存层才能写入空值标记，
     * 挡住对同一个无效 id 的重复请求（防穿透）。404 由调用方统一抛。</p>
     */
    private ProductDetail loadDetail(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            return null;
        }
        List<ProductSku> skus = skuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id));
        List<ProductImage> images = imageMapper.selectList(
                new LambdaQueryWrapper<ProductImage>().eq(ProductImage::getProductId, id)
                        .orderByAsc(ProductImage::getSort));
        ProductDetail detail = ProductDetail.from(product, skus, images);
        Category category = categoryMapper.selectById(product.getCategoryId());
        if (category != null) {
            detail.setCategoryName(category.getName());
        }
        return detail;
    }

    /** 新增商品（含 SKU 与图集）。 */
    @Transactional
    public Long create(ProductSaveRequest req) {
        requireCategory(req.getCategoryId());

        Product product = new Product();
        product.setCategoryId(req.getCategoryId());
        product.setName(req.getName());
        product.setSubtitle(req.getSubtitle());
        product.setSearchKeywords(req.getSearchKeywords());
        product.setMainImage(req.getMainImage());
        product.setDetail(req.getDetail());
        product.setPrice(req.getPrice());
        // 销量允许后台铺初始值；不传按 0。成交后由 OrderService.markPaid 自动累加。
        product.setSales(req.getSales() == null ? 0 : req.getSales());
        product.setStatus(req.getStatus() == null ? STATUS_OFF : req.getStatus());
        product.setIsSeckill(req.getIsSeckill() == null ? 0 : req.getIsSeckill());
        // 总库存 = SKU 库存之和（无 SKU 时为 0）
        product.setStock(sumSkuStock(req.getSkus()));
        productMapper.insert(product);

        saveSkus(product.getId(), req.getSkus(), product.getPrice());
        saveImages(product.getId(), req.getImages());
        return product.getId();
    }

    /** 编辑商品。 */
    @Transactional
    public void update(Long id, ProductSaveRequest req) {
        Product product = requireProduct(id);
        requireCategory(req.getCategoryId());

        product.setCategoryId(req.getCategoryId());
        product.setName(req.getName());
        product.setSubtitle(req.getSubtitle());
        product.setSearchKeywords(req.getSearchKeywords());
        product.setMainImage(req.getMainImage());
        product.setDetail(req.getDetail());
        product.setPrice(req.getPrice());
        if (req.getStatus() != null) {
            product.setStatus(req.getStatus());
        }
        if (req.getIsSeckill() != null) {
            product.setIsSeckill(req.getIsSeckill());
        }
        // 销量：不传保持原值（老调用方漏传不该把已有销量清零）。
        // 直接覆盖为绝对值，而不是累加 —— 这里是对账入口，累加语义会让「改成 50」变得不可预期。
        if (req.getSales() != null) {
            product.setSales(req.getSales());
        }
        if (req.getSkus() != null) {
            // 全量重建 SKU：先逻辑删除旧的，再插入新的
            skuMapper.delete(new LambdaQueryWrapper<ProductSku>()
                    .eq(ProductSku::getProductId, id));
            saveSkus(id, req.getSkus(), product.getPrice());
            product.setStock(sumSkuStock(req.getSkus()));
        }
        productMapper.updateById(product);

        if (req.getImages() != null) {
            imageMapper.delete(new LambdaQueryWrapper<ProductImage>()
                    .eq(ProductImage::getProductId, id));
            saveImages(id, req.getImages());
        }
        // 提交后失效详情缓存：事务内删的话，并发读可能在提交前读到旧值并回填
        cacheService.evictAfterCommit(CacheKeys.productDetail(id));
    }

    /** 上架/下架。 */
    @Transactional
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != STATUS_ON && status != STATUS_OFF)) {
            throw BizException.badRequest("状态值不合法");
        }
        Product product = requireProduct(id);
        product.setStatus(status);
        productMapper.updateById(product);
        cacheService.evictAfterCommit(CacheKeys.productDetail(id));
    }

    /** 删除商品（连同 SKU、图集）。 */
    @Transactional
    public void delete(Long id) {
        requireProduct(id);
        skuMapper.delete(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id));
        imageMapper.delete(new LambdaQueryWrapper<ProductImage>().eq(ProductImage::getProductId, id));
        productMapper.deleteById(id);
        cacheService.evictAfterCommit(CacheKeys.productDetail(id));
    }

    // -------------------------------------------------------------------------
    // 批量操作
    //
    // 为什么不复用单条接口循环调用：
    //   · 循环 = N 个独立事务，第 13 条失败时前 12 条已提交，用户看到「部分失败」
    //     却不知道是哪几条成了 —— 「以为都下架了、实际有 3 个还在卖」是真实会出事的；
    //   · 批量删除还有级联（SKU + 图集），必须同一个事务里一起删。
    // 也不复用 update()：它是全量覆盖语义，会重建 SKU（先逻辑删再插），
    // product_sku.id 一变，seckill_activity.sku_id 的引用就全断了。
    // -------------------------------------------------------------------------

    /** 单次批量操作的条数上限：防止误传全表 id 把行锁住。 */
    private static final int BATCH_MAX_SIZE = 100;

    /**
     * 校验并规整批量操作的 id 列表。
     *
     * <p><b>要求全部存在</b>，不存在的直接整批拒绝 —— 这是刻意的：
     * 「部分成功」在批量操作里是最难收拾的状态，运营无法从一句「部分失败」
     * 判断出到底哪几条生效了。宁可全不做，让他刷新后重试。</p>
     */
    private List<Long> normalizeBatchIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw BizException.badRequest("请先选择商品");
        }
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            throw BizException.badRequest("请先选择商品");
        }
        if (distinct.size() > BATCH_MAX_SIZE) {
            throw BizException.badRequest("单次最多处理 " + BATCH_MAX_SIZE + " 个商品");
        }
        Long found = productMapper.selectCount(
                new LambdaQueryWrapper<Product>().in(Product::getId, distinct));
        if (found == null || found != distinct.size()) {
            throw BizException.notFound("选中的商品里有已删除的，本次操作已取消，请刷新后重试");
        }
        return distinct;
    }

    /** 批量失效详情缓存（一条命令发多个 key，而不是逐条往返）。 */
    private void evictProductDetails(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        cacheService.evictAfterCommit(ids.stream()
                .map(CacheKeys::productDetail)
                .toArray(String[]::new));
    }

    /**
     * 批量上架/下架。
     *
     * @return 实际更新的行数
     */
    @Transactional
    public int batchChangeStatus(List<Long> ids, Integer status) {
        if (status == null || (status != STATUS_ON && status != STATUS_OFF)) {
            throw BizException.badRequest("状态值不合法");
        }
        List<Long> target = normalizeBatchIds(ids);

        Product patch = new Product();
        patch.setStatus(status);
        int affected = productMapper.update(patch,
                new LambdaQueryWrapper<Product>().in(Product::getId, target));
        evictProductDetails(target);
        return affected;
    }

    /**
     * 批量改分类。
     *
     * <p>只更新 {@code category_id} 一列 —— 不走 {@link #update} 的全量覆盖，
     * 否则会把每个商品的 SKU 删了重建。</p>
     *
     * @return 实际更新的行数
     */
    @Transactional
    public int batchChangeCategory(List<Long> ids, Long categoryId) {
        if (categoryId == null) {
            throw BizException.badRequest("请选择目标分类");
        }
        requireCategory(categoryId);
        List<Long> target = normalizeBatchIds(ids);

        Product patch = new Product();
        patch.setCategoryId(categoryId);
        int affected = productMapper.update(patch,
                new LambdaQueryWrapper<Product>().in(Product::getId, target));
        evictProductDetails(target);
        return affected;
    }

    /**
     * 批量删除（连同 SKU、图集，单事务）。
     *
     * @return 实际删除的行数
     */
    @Transactional
    public int batchDelete(List<Long> ids) {
        List<Long> target = normalizeBatchIds(ids);

        skuMapper.delete(new LambdaQueryWrapper<ProductSku>()
                .in(ProductSku::getProductId, target));
        imageMapper.delete(new LambdaQueryWrapper<ProductImage>()
                .in(ProductImage::getProductId, target));
        int affected = productMapper.delete(new LambdaQueryWrapper<Product>()
                .in(Product::getId, target));
        evictProductDetails(target);
        return affected;
    }

    // -------------------------------------------------------------------------
    // 补货待办
    // -------------------------------------------------------------------------

    /**
     * 补货待办：库存低于所属分类预警线的 SKU 清单。
     *
     * <p><b>粒度是 SKU 不是商品。</b>总库存 120 但某个尺码 0 件时，商品级判断
     * 完全看不出问题，用户却买不到那个尺码。所以这里不能复用列表的 {@code stockLow}
     * （那是商品级总库存）——两者不一致是刻意的：列表回答「整体缺不缺货」，
     * 这里回答「具体补哪个规格」。</p>
     *
     * <p><b>为什么不用一条 JOIN SQL：</b>项目里没有 Mapper XML；而且阈值继承是在
     * Java 里沿 {@code parent_id} 解析的，放进 SQL 就变成第二份实现，两边规则一有
     * 出入就会出现「清单里有、页面没标红」。两段式（在售商品 → 它们的所有 SKU）
     * 与 {@link #randomPage} 取 id 到内存是同一套路，当前 162 商品 / 383 SKU
     * 的量级完全够用。</p>
     *
     * <p><b>注意不能先按「商品总库存」粗筛：</b>总库存 120、2 个 SKU（120 + 0）时，
     * 总库存远高于阈值但其中一个 SKU 已断货。所以必须把所有在售商品的 SKU 都看一遍。</p>
     *
     * @param limit 返回条数上限（1~500），只影响明细，不影响 {@code total}
     */
    public LowStockReport lowStock(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);

        // ① 只算在售商品：下架商品不需要补货。按库存升序取前 N 个，超限时优先保留最可能告急的
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .select(Product::getId, Product::getCategoryId, Product::getName,
                        Product::getMainImage, Product::getStock)
                .eq(Product::getStatus, STATUS_ON)
                .orderByAsc(Product::getStock)
                .orderByAsc(Product::getId)
                .last("LIMIT " + lowStockScanLimit));
        if (products.isEmpty()) {
            return LowStockReport.empty();
        }

        Map<Long, Integer> thresholds = stockWarnService.effectiveThresholds();
        Map<Long, String> categoryNames = stockWarnService.categoryNameMap();

        // ② 一次取回这些商品的 SKU（含无规格商品的占位 SKU）
        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, List<ProductSku>> skusByProduct = skuMapper.selectList(
                        new LambdaQueryWrapper<ProductSku>()
                                .in(ProductSku::getProductId, productIds))
                .stream()
                .collect(Collectors.groupingBy(ProductSku::getProductId));

        // ③ 逐 SKU 判定
        List<LowStockItem> all = new ArrayList<>();
        for (Product product : products) {
            int threshold = stockWarnService.thresholdOf(product.getCategoryId(), thresholds);
            if (threshold <= 0) {
                // 预警线配成 0 表示「该分类不告警」，不是「所有 SKU 都告急」
                continue;
            }
            for (ProductSku sku : skusByProduct.getOrDefault(product.getId(), List.of())) {
                int stock = sku.getStock() == null ? 0 : sku.getStock();
                if (stock >= threshold) {
                    continue;
                }
                all.add(toLowStockItem(product, sku, threshold,
                        categoryNames.get(product.getCategoryId())));
            }
        }

        // ④ 排序：剩余比例升序（0 = 断货最急）→ 缺口降序 → id 稳定
        all.sort(Comparator
                .comparingDouble(LowStockItem::getRemainRatio)
                .thenComparing(Comparator.comparingInt(LowStockItem::getShortage).reversed())
                .thenComparing(LowStockItem::getProductId)
                .thenComparing(LowStockItem::getSkuId));

        boolean truncated = all.size() > safeLimit || products.size() >= lowStockScanLimit;
        List<LowStockItem> items = all.size() > safeLimit
                ? new ArrayList<>(all.subList(0, safeLimit))
                : all;
        int outOfStock = (int) all.stream().filter(it -> it.getStock() == 0).count();
        return new LowStockReport(all.size(), outOfStock, truncated, items);
    }

    private LowStockItem toLowStockItem(Product product, ProductSku sku,
                                        int threshold, String categoryName) {
        int stock = sku.getStock() == null ? 0 : sku.getStock();
        LowStockItem item = new LowStockItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setMainImage(product.getMainImage());
        item.setCategoryId(product.getCategoryId());
        item.setCategoryName(categoryName);
        item.setSkuId(sku.getId());
        item.setSpecName(sku.getSpecName());
        item.setSpecValue(sku.getSpecValue());
        item.setSkuImage(sku.getSkuImage());
        item.setStock(stock);
        item.setThreshold(threshold);
        item.setShortage(threshold - stock);
        // 保留两位小数：前端直接当百分比展示，不必再处理浮点尾巴
        item.setRemainRatio(Math.round(stock * 100.0 / threshold) / 100.0);
        return item;
    }

    // -------------------------------------------------------------------------
    // 库存（供 trade/seckill 阶段复用的原子更新入口）
    // -------------------------------------------------------------------------

    /** 直接设置 SKU 库存，并回写商品总库存。 */
    @Transactional
    public void updateSkuStock(Long skuId, Integer stock) {
        if (stock == null || stock < 0) {
            throw BizException.badRequest("库存不能为负");
        }
        ProductSku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw BizException.notFound("SKU 不存在");
        }
        sku.setStock(stock);
        skuMapper.updateById(sku);
        refreshProductStock(sku.getProductId());
        // 详情里含 SKU 库存，改库存后必须失效，否则后台改完前台仍显示旧值
        cacheService.evictAfterCommit(CacheKeys.productDetail(sku.getProductId()));
    }

    /** 依据 SKU 汇总刷新商品总库存。 */
    private void refreshProductStock(Long productId) {
        List<ProductSku> skus = skuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, productId));
        int total = skus.stream().mapToInt(s -> s.getStock() == null ? 0 : s.getStock()).sum();
        Product product = productMapper.selectById(productId);
        if (product != null) {
            product.setStock(total);
            productMapper.updateById(product);
        }
    }

    // -------------------------------------------------------------------------
    // 规格判断（加购 / 下单共用的口径）
    // -------------------------------------------------------------------------

    /**
     * 商品是否配置了「真实规格」。
     *
     * <p>种子数据里部分无规格商品也带了一条 {@code spec_value} 为空的 SKU 占位记录，
     * 这类记录不构成规格，不应强制用户选择。</p>
     *
     * <p>加购与下单都依赖这个判断：有真实规格的商品必须带 {@code skuId}，
     * 否则会退化成按商品级库存扣减，与 SKU 库存各算一套账。</p>
     */
    public boolean hasRealSpec(Long productId) {
        Long count = skuMapper.selectCount(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, productId)
                .isNotNull(ProductSku::getSpecValue)
                .ne(ProductSku::getSpecValue, ""));
        return count != null && count > 0;
    }

    // -------------------------------------------------------------------------
    // 内部方法
    // -------------------------------------------------------------------------

    private Product requireProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw BizException.notFound("商品不存在");
        }
        return product;
    }

    private void requireCategory(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw BizException.badRequest("分类不存在");
        }
    }

    private int sumSkuStock(List<ProductSaveRequest.SkuItem> skus) {
        if (skus == null || skus.isEmpty()) {
            return 0;
        }
        return skus.stream().mapToInt(s -> s.getStock() == null ? 0 : s.getStock()).sum();
    }

    private void saveSkus(Long productId, List<ProductSaveRequest.SkuItem> skus, BigDecimal productPrice) {
        if (skus == null || skus.isEmpty()) {
            return;
        }
        for (ProductSaveRequest.SkuItem item : skus) {
            ProductSku sku = new ProductSku();
            sku.setProductId(productId);
            sku.setSpecName(item.getSpecName());
            sku.setSpecValue(item.getSpecValue());
            sku.setSkuImage(item.getSkuImage());
            sku.setPrice(resolveSkuPrice(item, productPrice));
            sku.setStock(item.getStock() == null ? 0 : item.getStock());
            skuMapper.insert(sku);
        }
    }

    /**
     * 计算落库的 SKU 价格。
     *
     * <p>后台新建商品表单会预置一行「规格名 / 规格值 / 价格都为空」的 SKU，开发者往往只填库存，
     * 于是这行会以 price=0 落库；而商城详情页会自动选中第一个有库存的 SKU 并以它的价格成交，
     * 结果是整件商品显示并可按 ¥0.00 下单（商品 6/7 就踩了这个坑）。
     *
     * <p>所以价格未填（&lt;=0）的 SKU 一律继承商品价格 —— 价格没填不等于免费；
     * 规格名为空的行同理，它代表商品默认规格，本就不该有独立价格。
     * 有规格且填了正价的行完全不受影响。
     */
    private BigDecimal resolveSkuPrice(ProductSaveRequest.SkuItem item, BigDecimal productPrice) {
        boolean blankSpec = !StringUtils.hasText(item.getSpecName())
                && !StringUtils.hasText(item.getSpecValue());
        BigDecimal price = item.getPrice();
        if (!blankSpec && price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            return price;
        }
        return productPrice == null ? BigDecimal.ZERO : productPrice;
    }

    private void saveImages(Long productId, List<String> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        int sort = 0;
        for (String url : images) {
            if (!StringUtils.hasText(url)) {
                continue;
            }
            ProductImage image = new ProductImage();
            image.setProductId(productId);
            image.setUrl(url);
            image.setSort(sort++);
            imageMapper.insert(image);
        }
    }

    /** 排序：白名单字段，防 SQL 注入。 */
    private void applySort(LambdaQueryWrapper<Product> wrapper, String sortBy, String order) {
        boolean asc = !"desc".equalsIgnoreCase(order);
        String field = sortBy == null ? "createdAt" : sortBy;
        switch (field) {
            case "price" -> wrapper.orderBy(true, asc, Product::getPrice);
            case "sales" -> wrapper.orderBy(true, asc, Product::getSales);
            default -> wrapper.orderBy(true, asc, Product::getCreatedAt);
        }
        // 主键兜底次序：排序键不唯一时（如批量导入的演示商品 created_at 完全相同），
        // MySQL 不保证两次查询的行序一致，翻页会重复/漏项。补一个唯一键收口。
        wrapper.orderBy(true, asc, Product::getId);
    }
}
