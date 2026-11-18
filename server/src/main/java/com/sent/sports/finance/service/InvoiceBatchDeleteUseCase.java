package com.sent.sports.finance.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.finance.mapper.InvoiceMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 管理端发票列表批量删除：只打 {@code invoice_request.deleted}，票面金额与 PDF 不改动。 */
@Service
public class InvoiceBatchDeleteUseCase {

    private final InvoiceMapper invoiceMapper;

    public InvoiceBatchDeleteUseCase(InvoiceMapper invoiceMapper) {
        this.invoiceMapper = invoiceMapper;
    }

    @UseCase
    public int delete(List<String> ids) {
        return invoiceMapper.batchMarkDeleted(BatchIds.normalize(ids));
    }
}
