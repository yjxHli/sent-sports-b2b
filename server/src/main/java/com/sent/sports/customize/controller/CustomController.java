package com.sent.sports.customize.controller;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.R;
import com.sent.sports.customize.dto.CustomDtos;
import com.sent.sports.customize.service.CustomQueryService;
import com.sent.sports.customize.service.SubmitDraftUseCase;
import com.sent.sports.customize.service.dto.SubmitDraftCmd;
import com.sent.sports.customize.service.dto.SubmitDraftResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定制控制器（P4-25）：上传图稿 / 定制单详情（plan §8.7）。
 * <p>上传图稿依赖同模块用例 {@link SubmitDraftUseCase}；详情依赖查询门面
 * {@link CustomQueryService}（桩，P5 接入仓储）。审核/投产属管理侧（ReviewCustomUseCase /
 * ProduceCustomUseCase），本期不做用户侧端点。当前用户绑定（@CurrentUserArg）在 Phase 5，
 * 现以路径/请求体携带的定制单号占位。</p>
 */
@RestController
@RequestMapping("/api/customize")
public class CustomController {

    private final SubmitDraftUseCase submitDraftUseCase;
    private final CustomQueryService customQueryService;

    public CustomController(SubmitDraftUseCase submitDraftUseCase, CustomQueryService customQueryService) {
        this.submitDraftUseCase = submitDraftUseCase;
        this.customQueryService = customQueryService;
    }

    @PostMapping("/orders/{customOrderId}/drafts")
    @RequirePerm("custom:upload")
    public R<CustomDtos.DraftVO> uploadDraft(@PathVariable String customOrderId,
                                            @Valid @RequestBody CustomDtos.UploadDraftCmd cmd) {
        SubmitDraftResult r = submitDraftUseCase.submit(
                new SubmitDraftCmd(customOrderId, cmd.fileId(), cmd.fileName(), cmd.fileSize()));
        return R.ok(new CustomDtos.DraftVO(r.customOrderId(), r.version(), r.status(), r.reviewDeadlineMillis()));
    }

    @GetMapping("/orders/{customOrderId}")
    public R<CustomDtos.CustomVO> detail(@PathVariable String customOrderId) {
        return R.ok(customQueryService.detail(customOrderId));
    }
}
