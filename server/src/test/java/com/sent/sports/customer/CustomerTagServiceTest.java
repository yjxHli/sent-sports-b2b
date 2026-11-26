package com.sent.sports.customer;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.domain.TagCategory;
import com.sent.sports.customer.dto.admin.CustomerTagDtos;
import com.sent.sports.customer.mapper.CustomerMapper;
import com.sent.sports.customer.mapper.CustomerTagLinkMapper;
import com.sent.sports.customer.mapper.CustomerTagMapper;
import com.sent.sports.customer.mapper.po.CustomerTagPO;
import com.sent.sports.customer.service.impl.CustomerTagServiceImpl;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客户标签服务测试（TDD 红）。
 *
 * <p>标签是「客服之间的内部判断」，直接影响后续怎么对待这个客户（羊毛党 vs 忠实老客），
 * 所以这里锁四件事：</p>
 * <ol>
 *   <li><b>分类带语义与配色键</b>：四大类必须能一眼区分，前端不自己猜颜色；</li>
 *   <li><b>打标是差异增删</b>：弹窗一次提交全量勾选，重复提交不能重复插、也不能把没动的标签删掉重插；</li>
 *   <li><b>只认启用中的标签</b>：停用的标签不能再被新打上（老数据仍展示）；</li>
 *   <li><b>留痕</b>：谁给谁打了什么、取消了什么，写 {@code sys_operation_log}。</li>
 * </ol>
 */
class CustomerTagServiceTest extends BaseUseCaseTest {

    private static final long OPERATOR = 9001L;
    private static final long CUSTOMER = 9002L;

    @Mock
    private CustomerTagMapper tagMapper;
    @Mock
    private CustomerTagLinkMapper linkMapper;
    @Mock
    private CustomerMapper customerMapper;

    @InjectMocks
    private CustomerTagServiceImpl service;

    private CustomerTagPO tag(long id, String name, String category, String status) {
        CustomerTagPO po = new CustomerTagPO();
        po.setId(id);
        po.setName(name);
        po.setCategory(category);
        po.setStatus(status);
        po.setSortNo((int) id);
        return po;
    }

    /* ---------------- 目录 ---------------- */

    @Test
    @DisplayName("目录：分类翻成中文名 + 配色键，停用项默认不返回")
    void catalogTranslatesCategory() {
        when(tagMapper.selectCatalog(false)).thenReturn(List.of(
                tag(1L, "忠实老客", "EXCELLENT", "ACTIVE"),
                tag(2L, "羊毛党", "MALICIOUS", "ACTIVE")));

        List<CustomerTagDtos.TagVO> catalog = service.catalog(false);

        assertEquals(2, catalog.size());
        CustomerTagDtos.TagVO first = catalog.get(0);
        assertEquals("忠实老客", first.name());
        assertEquals("EXCELLENT", first.category());
        assertEquals("优质 / 正面", first.categoryLabel(), "分类必须给中文名，前端不硬编码");
        assertEquals("excellent", first.colorKey(), "配色键由后端下发，四大类颜色才能各端一致");
        assertEquals("MALICIOUS", catalog.get(1).category());
        assertEquals("malicious", catalog.get(1).colorKey());
    }

    @Test
    @DisplayName("目录：脏分类回落中性普通，不能让整页 500")
    void catalogFallsBackForUnknownCategory() {
        when(tagMapper.selectCatalog(true)).thenReturn(List.of(
                tag(9L, "历史标签", "WEIRD", "ACTIVE")));

        List<CustomerTagDtos.TagVO> catalog = service.catalog(true);

        assertEquals(TagCategory.NEUTRAL.name(), catalog.get(0).category());
        assertEquals("中性普通", catalog.get(0).categoryLabel());
    }

    /* ---------------- 打标 ---------------- */

    @Test
    @DisplayName("打标：只对差异增删（existing 1,2 → target 2,3）")
    void applyTagsDiffsInsteadOfFullReplace() {
        when(customerMapper.selectById(CUSTOMER)).thenReturn(new com.sent.sports.customer.mapper.po.CustomerPO());
        when(linkMapper.selectTagIdsOfCustomer(CUSTOMER)).thenReturn(List.of(1L, 2L));
        when(tagMapper.selectByIds(List.of(2L, 3L))).thenReturn(List.of(
                tag(2L, "忠实老客", "EXCELLENT", "ACTIVE"),
                tag(3L, "价格敏感", "NEUTRAL", "ACTIVE")));
        when(linkMapper.selectTagsOfCustomer(CUSTOMER)).thenReturn(List.of(
                tag(2L, "忠实老客", "EXCELLENT", "ACTIVE"),
                tag(3L, "价格敏感", "NEUTRAL", "ACTIVE")));

        service.applyTags(CUSTOMER, List.of(2L, 3L), OPERATOR);

        verify(linkMapper).insertIgnore(eq(CUSTOMER), eq(3L), eq(OPERATOR));
        verify(linkMapper).deleteOne(CUSTOMER, 1L);
        verify(linkMapper, never()).insertIgnore(eq(CUSTOMER), eq(2L), any());
        verify(linkMapper, never()).deleteOne(CUSTOMER, 2L);
        verify(tagMapper).insertLog(eq(OPERATOR), eq("TAG_APPLY"), eq(String.valueOf(CUSTOMER)), anyString());
    }

    @Test
    @DisplayName("打标：空清单 = 全部取消，重复 ID 先去重")
    void applyTagsHandlesEmptyAndDuplicates() {
        when(customerMapper.selectById(CUSTOMER)).thenReturn(new com.sent.sports.customer.mapper.po.CustomerPO());
        when(linkMapper.selectTagIdsOfCustomer(CUSTOMER)).thenReturn(List.of(1L, 2L));
        when(linkMapper.selectTagsOfCustomer(CUSTOMER)).thenReturn(List.of());

        service.applyTags(CUSTOMER, List.of(), OPERATOR);
        verify(linkMapper).deleteOne(CUSTOMER, 1L);
        verify(linkMapper).deleteOne(CUSTOMER, 2L);
        verify(linkMapper, never()).insertIgnore(anyLong(), anyLong(), any());

        when(tagMapper.selectByIds(List.of(5L))).thenReturn(List.of(
                tag(5L, "新客", "NEUTRAL", "ACTIVE")));
        service.applyTags(CUSTOMER, List.of(5L, 5L, 5L), OPERATOR);
        verify(linkMapper).insertIgnore(CUSTOMER, 5L, OPERATOR);
    }

    @Test
    @DisplayName("打标：客户不存在 404；停用/不存在的标签 40001，且不写任何关联")
    void applyTagsValidatesCustomerAndTags() {
        when(customerMapper.selectById(404L)).thenReturn(null);
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BizException.class,
                () -> service.applyTags(404L, List.of(1L), OPERATOR)).getErrorCode());

        when(customerMapper.selectById(CUSTOMER)).thenReturn(new com.sent.sports.customer.mapper.po.CustomerPO());
        when(tagMapper.selectByIds(List.of(7L))).thenReturn(List.of(
                tag(7L, "已停用标签", "RISK", "DISABLED")));

        BizException disabled = assertThrows(BizException.class,
                () -> service.applyTags(CUSTOMER, List.of(7L), OPERATOR));
        assertEquals(ErrorCode.PARAM_INVALID, disabled.getErrorCode());
        assertTrue(disabled.resolvedMessage().contains("已停用标签"), "报错要指出是哪个标签不能打");

        verify(linkMapper, never()).insertIgnore(anyLong(), anyLong(), any());
        verify(linkMapper, never()).deleteOne(anyLong(), anyLong());
        verify(linkMapper, never()).selectTagIdsOfCustomer(anyLong());
    }

    @Test
    @DisplayName("某客户身上的标签：按分类顺序返回，便于头像旁按「好话在前、风险在后」排列")
    void tagsOfCustomerSorted() {
        when(linkMapper.selectTagsOfCustomer(CUSTOMER)).thenReturn(List.of(
                tag(3L, "羊毛党", "MALICIOUS", "ACTIVE"),
                tag(4L, "忠实老客", "EXCELLENT", "ACTIVE")));

        List<CustomerTagDtos.TagVO> tags = service.tagsOf(CUSTOMER);

        assertEquals(2, tags.size());
        assertEquals("忠实老客", tags.get(0).name(), "优质类排在恶意类前面");
        assertEquals("羊毛党", tags.get(1).name());
    }

    /* ---------------- 管理端维护 ---------------- */

    @Test
    @DisplayName("新增：重名直接拒绝（含已停用的同名），分类必须在白名单内")
    void createValidatesNameAndCategory() {
        when(tagMapper.selectByName("羊毛党")).thenReturn(tag(3L, "羊毛党", "MALICIOUS", "ACTIVE"));

        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> service.create(new CustomerTagDtos.TagForm(" 羊毛党 ", "MALICIOUS"), OPERATOR))
                .getErrorCode());
        verify(tagMapper, never()).insert(any(CustomerTagPO.class));

        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> service.create(new CustomerTagDtos.TagForm("新标签", "UNKNOWN"), OPERATOR))
                .getErrorCode());

        when(tagMapper.selectByName("新标签")).thenReturn(null);
        when(tagMapper.insert(any(CustomerTagPO.class))).thenAnswer(inv -> {
            inv.getArgument(0, CustomerTagPO.class).setId(88L);
            return 1;
        });

        CustomerTagDtos.TagVO created = service.create(
                new CustomerTagDtos.TagForm(" 新标签 ", "RISK"), OPERATOR);

        assertEquals(88L, created.tagId());
        assertEquals("新标签", created.name());
        assertEquals("风险留意", created.categoryLabel());
        assertEquals("ACTIVE", created.status());
        verify(tagMapper).insertLog(eq(OPERATOR), eq("TAG_CREATE"), eq("88"), anyString());
    }

    @Test
    @DisplayName("改名 / 停用：不存在的标签 404；状态只认 ACTIVE/DISABLED")
    void updateAndStatusRules() {
        when(tagMapper.selectById(1L)).thenReturn(tag(1L, "忠实老客", "EXCELLENT", "ACTIVE"));
        when(tagMapper.selectByName("老客")).thenReturn(null);

        CustomerTagDtos.TagVO updated = service.update(1L,
                new CustomerTagDtos.TagForm("老客", "EXCELLENT"), OPERATOR);
        assertEquals("老客", updated.name());

        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> service.setStatus(1L, "ON", OPERATOR)).getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BizException.class,
                () -> service.update(404L, new CustomerTagDtos.TagForm("x", "RISK"), OPERATOR))
                .getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BizException.class,
                () -> service.setStatus(404L, "DISABLED", OPERATOR)).getErrorCode());
    }

    @Test
    @DisplayName("删除：有客户还在用就不给删（否则那些客户的头像旁会凭空少一个标签）")
    void deleteBlockedWhenInUse() {
        when(tagMapper.selectById(1L)).thenReturn(tag(1L, "忠实老客", "EXCELLENT", "ACTIVE"));
        when(linkMapper.countByTag(1L)).thenReturn(3);

        BizException inUse = assertThrows(BizException.class, () -> service.delete(1L, OPERATOR));
        assertEquals(ErrorCode.PARAM_INVALID, inUse.getErrorCode());
        assertTrue(inUse.resolvedMessage().contains("3"), "要说明还有几个客户在用");
        verify(tagMapper, never()).deleteById(anyLong());

        when(linkMapper.countByTag(9L)).thenReturn(0);
        when(tagMapper.selectById(9L)).thenReturn(tag(9L, "废弃标签", "NEUTRAL", "DISABLED"));
        service.delete(9L, OPERATOR);
        verify(tagMapper).deleteById(9L);
    }
}
