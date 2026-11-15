package com.sent.sports.customer.service;

import com.sent.sports.customer.service.dto.AddressCmd;
import com.sent.sports.customer.service.dto.AddressVO;

import java.util.List;
import java.util.Optional;

/**
 * 收货地址簿服务（用户端 CRUD + 下单快照来源）。
 *
 * <p>读接口 {@link #findOwned} 同时是<b>跨模块契约</b>：order 模块下单时用它按 {@code addressId}
 * 取地址再固化快照，只依赖本接口，不碰 customer 的 mapper / PO（ArchUnit 规则 4）。</p>
 */
public interface AddressService {

    /** 我的地址列表（默认地址在前）。 */
    List<AddressVO> list(long customerId);

    /** 新增地址；该客户的第一条地址自动成为默认。 */
    AddressVO create(long customerId, AddressCmd cmd);

    /** 修改地址（带归属校验，他人地址 404）。 */
    AddressVO update(long customerId, long id, AddressCmd cmd);

    /** 删除地址；删掉默认地址后自动补位一条新的默认。 */
    void delete(long customerId, long id);

    /** 设为默认（会清掉其余默认标记）。 */
    void setDefault(long customerId, long id);

    /**
     * 按 ID + 归属取地址；{@code addressId} 为空或不属于该客户时返回空。
     * <p>下单链路用它判断「地址是否合法」，不抛异常，由调用方决定错误码。</p>
     */
    Optional<AddressVO> findOwned(long customerId, Long addressId);
}
