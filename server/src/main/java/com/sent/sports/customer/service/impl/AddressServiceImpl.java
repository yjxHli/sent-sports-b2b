package com.sent.sports.customer.service.impl;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.mapper.CustomerAddressMapper;
import com.sent.sports.customer.mapper.po.CustomerAddressPO;
import com.sent.sports.customer.service.AddressService;
import com.sent.sports.customer.service.dto.AddressCmd;
import com.sent.sports.customer.service.dto.AddressVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 收货地址簿实现。
 *
 * <p>三条容易写错的地方：</p>
 * <ul>
 *   <li><b>默认唯一</b>：置默认前先 {@code clearDefault}，否则结算页可能出现两个默认、预选结果随机；</li>
 *   <li><b>第一条自动默认</b>：新用户加完地址就能直接下单，不必再点一次「设为默认」；</li>
 *   <li><b>删默认要补位</b>：删掉唯一的默认地址后，若还有别的地址就自动把最新一条设为默认，
 *       否则用户下次结算会发现「一条都没选中」。</li>
 * </ul>
 */
@Service
public class AddressServiceImpl implements AddressService {

    private final CustomerAddressMapper addressMapper;

    public AddressServiceImpl(CustomerAddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    @Override
    public List<AddressVO> list(long customerId) {
        return addressMapper.selectByCustomer(customerId).stream().map(AddressServiceImpl::toVO).toList();
    }

    @Override
    @UseCase
    public AddressVO create(long customerId, AddressCmd cmd) {
        validate(cmd);
        boolean first = addressMapper.countByCustomer(customerId) == 0;
        boolean makeDefault = first || cmd.isDefault();
        if (!first && cmd.isDefault()) {
            // 只有「已有其它地址且这次显式要默认」才需要清旧默认；第一条地址时本来就没有默认可清
            addressMapper.clearDefault(customerId);
        }
        CustomerAddressPO po = new CustomerAddressPO();
        po.setCustomerId(customerId);
        fill(po, cmd);
        po.setIsDefault(makeDefault ? 1 : 0);
        addressMapper.insert(po);
        return toVO(po);
    }

    @Override
    @UseCase
    public AddressVO update(long customerId, long id, AddressCmd cmd) {
        validate(cmd);
        CustomerAddressPO existing = requireOwned(customerId, id);
        boolean wasDefault = isDefault(existing);
        if (cmd.isDefault()) {
            addressMapper.clearDefault(customerId);
        }
        fill(existing, cmd);
        // 已经是默认的地址，编辑时即使没勾「默认」也不能自己降级——会让结算页失去预选项
        existing.setIsDefault(cmd.isDefault() || wasDefault ? 1 : 0);
        addressMapper.updateById(existing);
        return toVO(existing);
    }

    @Override
    @UseCase
    public void delete(long customerId, long id) {
        CustomerAddressPO existing = requireOwned(customerId, id);
        if (addressMapper.deleteOwned(id, customerId) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (isDefault(existing)) {
            promoteAnother(customerId);
        }
    }

    @Override
    @UseCase
    public void setDefault(long customerId, long id) {
        requireOwned(customerId, id);
        addressMapper.clearDefault(customerId);
        if (addressMapper.setDefault(id, customerId) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    @Override
    public Optional<AddressVO> findOwned(long customerId, Long addressId) {
        if (addressId == null || addressId <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(addressMapper.selectOwned(addressId, customerId)).map(AddressServiceImpl::toVO);
    }

    /** 删掉默认地址后把剩下最新的一条补位成默认（列表已按 id 倒序，取第一条即可）。 */
    private void promoteAnother(long customerId) {
        List<CustomerAddressPO> rest = addressMapper.selectByCustomer(customerId);
        if (rest.isEmpty()) {
            return;
        }
        addressMapper.setDefault(rest.get(0).getId(), customerId);
    }

    private CustomerAddressPO requireOwned(long customerId, long id) {
        CustomerAddressPO po = addressMapper.selectOwned(id, customerId);
        if (po == null) {
            // 他人地址与不存在地址同一个错误码：不能用 403 泄露「这条存在」
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return po;
    }

    private static void validate(AddressCmd cmd) {
        if (cmd == null || isBlank(cmd.receiver()) || isBlank(cmd.phone()) || isBlank(cmd.detail())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "收件人、电话、详细地址都不能为空");
        }
    }

    private static void fill(CustomerAddressPO po, AddressCmd cmd) {
        po.setReceiver(cmd.receiver().trim());
        po.setPhone(cmd.phone().trim());
        po.setProvince(trimToNull(cmd.province()));
        po.setCity(trimToNull(cmd.city()));
        po.setDistrict(trimToNull(cmd.district()));
        po.setDetail(cmd.detail().trim());
    }

    private static AddressVO toVO(CustomerAddressPO po) {
        return new AddressVO(po.getId(), po.getReceiver(), po.getPhone(),
                po.getProvince(), po.getCity(), po.getDistrict(), po.getDetail(), isDefault(po));
    }

    private static boolean isDefault(CustomerAddressPO po) {
        return po.getIsDefault() != null && po.getIsDefault() == 1;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
