package com.sent.sports.file.controller;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.storage.LocalFileStorage;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.R;
import com.sent.sports.file.dto.FileDtos;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 文件服务（P6 收口）：真实落盘的上传 / 读取端点。
 *
 * <p>此前定制图稿、退款凭证都只传「前端随手生成的 fileId」，磁盘上没有文件，
 * 管理端自然看不到图；客服也无法发图。这里用 {@link LocalFileStorage} 做内容寻址落盘，
 * 业务侧只需保存返回的相对路径（fileId），读取走 {@code GET /api/files/{fileId}}，
 * 前端可直接放进 {@code <img src>}。</p>
 *
 * <p><b>读取路径在白名单里</b>：浏览器 {@code <img>} 带不了 Authorization 头，
 * fileId 是 SHA-256 摘要前缀路径不可枚举，所以读接口允许匿名；上传仍要求登录。</p>
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final LocalFileStorage storage;

    public FileController(LocalFileStorage storage) {
        this.storage = storage;
    }

    @PostMapping
    public R<FileDtos.FileVO> upload(@RequestParam("file") MultipartFile file,
                                     @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user)
            throws IOException {
        // 上传必须登录（用户端 / 管理端都可以，读接口则允许匿名，便于 <img> 直接加载）
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "上传文件为空");
        }
        byte[] bytes = file.getBytes();
        String originalName = file.getOriginalFilename();
        String fileId = storage.store(bytes, originalName);
        return R.ok(new FileDtos.FileVO(fileId, "/api/files/" + fileId,
                originalName == null ? "" : originalName, (long) bytes.length));
    }

    /** 读文件：fileId 形如 {@code 2026/11/ab/abcdef....png}，含斜杠所以用 {@code {*fileId}} 通配。 */
    @GetMapping("/{*fileId}")
    public ResponseEntity<Resource> read(@PathVariable String fileId) {
        String relative = fileId.startsWith("/") ? fileId.substring(1) : fileId;
        byte[] bytes;
        try {
            bytes = storage.read(relative);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件路径不合法");
        }
        if (bytes == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        MediaType mediaType = MediaTypeFactory.getMediaType(relative).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(new ByteArrayResource(bytes));
    }
}
