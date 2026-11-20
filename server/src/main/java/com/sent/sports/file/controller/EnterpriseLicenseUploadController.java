package com.sent.sports.file.controller;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.storage.Storage;
import com.sent.sports.common.util.LicenseImageValidator;
import com.sent.sports.common.web.R;
import com.sent.sports.file.dto.FileDtos;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

/**
 * 企业注册营业执照上传（2026-11-18）：**唯一允许匿名的上传端点**。
 *
 * <p>企业注册发生在登录之前，此时没有令牌，通用 {@code POST /api/files} 会返回 40101，
 * 导致注册表单第一步就卡死。这里只放行「营业执照」这一种用途：限定 JPG/PNG、≤ 2MB，
 * 其余任何文件（含 PDF/可执行文件）一律拒绝，避免把通用上传口子开给匿名用户。</p>
 *
 * <p>路径挂在 {@code /api/auth/} 前缀下，复用既有的公开路径白名单，无需改动鉴权配置。</p>
 */
@RestController
@RequestMapping("/api/auth/enterprise")
public class EnterpriseLicenseUploadController {

    /** 2MB：营业执照照片 200KB 量级足够，限制住匿名写入的磁盘占用。 */
    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private static final String CONTENT_TYPE_JPEG = "image/jpeg";
    private static final String CONTENT_TYPE_PNG = "image/png";

    private final Storage storage;

    public EnterpriseLicenseUploadController(Storage storage) {
        this.storage = storage;
    }

    @PostMapping("/license")
    public R<FileDtos.FileVO> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请上传营业执照");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BizException(ErrorCode.PARAM_INVALID, "营业执照图片不能超过 2MB");
        }
        String originalName = file.getOriginalFilename();
        if (!isAllowed(file.getContentType(), originalName)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "营业执照仅支持 JPG / PNG 图片");
        }
        byte[] bytes = file.getBytes();
        LicenseImageValidator.validate(bytes);
        String fileId = storage.store(bytes, originalName);
        return R.ok(new FileDtos.FileVO(fileId, "/api/files/" + fileId,
                originalName == null ? "" : originalName, (long) bytes.length));
    }

    /**
     * 同时看 Content-Type 与扩展名：浏览器有时把 jpg 传成 application/octet-stream，
     * 只认 Content-Type 会误伤，只认扩展名又挡不住伪造，两者取「都像是图片」才放行。
     */
    private static boolean isAllowed(String contentType, String originalName) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        boolean typeOk = type.equals(CONTENT_TYPE_JPEG) || type.equals(CONTENT_TYPE_PNG)
                || type.equals("application/octet-stream");
        if (!typeOk) {
            return false;
        }
        String name = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
    }
}
