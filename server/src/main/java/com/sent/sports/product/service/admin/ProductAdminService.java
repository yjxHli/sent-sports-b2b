package com.sent.sports.product.service.admin;

import com.sent.sports.common.dto.TierDto;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.dto.admin.AdminProductDtos;

import java.util.List;

/**
 * 商品管理（写侧）服务接口。
 * <p>商品 / SKU 的增删改已接真库；删除 SKU 采用软下线，保留历史订单与库存台账可追溯。
 * 阶梯价、一客一价同样真落库，列表委托同模块查询门面。</p>
 */
public interface ProductAdminService {

    /** 新建商品（含 SKU）。 */
    AdminProductDtos.ProductVO create(AdminProductDtos.ProductForm form);

    /** 更新商品与 SKU 矩阵：提交集合中缺少的既有 SKU 会被软下线。 */
    AdminProductDtos.ProductVO update(Long id, AdminProductDtos.ProductForm form);

    /** 删除商品。 */
    void delete(Long id);

    /** 上/下架。 */
    AdminProductDtos.ProductVO publish(Long id, boolean onShelf);

    /** 新建 SKU。 */
    AdminProductDtos.SkuVO addSku(AdminProductDtos.SkuForm form);

    /** 更新 SKU。 */
    AdminProductDtos.SkuVO updateSku(Long id, AdminProductDtos.SkuForm form);

    /** 删除 SKU（软下线并清理定价）。 */
    void deleteSku(Long id);

    /** 阶梯价批量配置：全量替换（空集合等于清空该 SKU 的全部折扣）。 */
    void configLadder(Long skuId, List<TierDto> tiers);

    /** 读取某 SKU 的既有阶梯价，折扣率由库内绝对价反算，供编辑页回显。 */
    List<TierDto> listLadderPrices(Long skuId);

    /** 一客一价新建/更新（真落库并返回真实主键）。 */
    AdminProductDtos.CustomerPriceVO upsertCustomerPrice(AdminProductDtos.CustomerPriceForm form);

    /** 删除一客一价。 */
    void deleteCustomerPrice(Long id);

    /** 一客一价列表（按 SKU / 客户可选）。 */
    List<AdminProductDtos.CustomerPriceVO> listCustomerPrices(Long skuId, Long customerId);

    /** 商品分页列表（委托 ProductQueryService，筛选条件下推，附带折扣 SKU 数）。 */
    PageResult<AdminProductDtos.ProductVO> list(PageQuery page, String keyword, Long categoryId,
                                                Boolean onShelf);
}
