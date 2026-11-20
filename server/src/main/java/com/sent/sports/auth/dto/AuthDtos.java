package com.sent.sports.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 认证接口 Web 层 DTO（P4-06）。
 * 请求体承载外部入参并带校验注解；响应体 {@link TokenVO} 暴露给前端。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 登录请求：用户名 + 密码 + 可选登录端/设备标识（P5-25 按设备隔离刷新令牌）。 */
    public record LoginCmd(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password,
            String aud,
            String deviceId) {
    }

    /** 发送登录验证码请求：只要手机号，验证码由后端生成并打印控制台。 */
    public record SendCodeCmd(
            @NotBlank(message = "手机号不能为空") String phone) {
    }

    /** 发送验证码结果：demoCode 仅在演示开关开启时非空，供前端控制台打印；生产恒为 null。 */
    public record SendCodeVO(String demoCode) {
    }

    /** 验证码登录请求：手机号 + 验证码 + 可选设备标识（aud 固定 user）。 */
    public record LoginByCodeCmd(
            @NotBlank(message = "手机号不能为空") String phone,
            @NotBlank(message = "验证码不能为空") String code,
            String deviceId) {
    }

    /** 找回密码发送验证码请求：手机号。 */
    public record SendResetCodeCmd(
            @NotBlank(message = "手机号不能为空") String phone) {
    }

    /** 找回密码第一步校验验证码请求：只校验不消费。 */
    public record VerifyResetCodeCmd(
            @NotBlank(message = "手机号不能为空") String phone,
            @NotBlank(message = "验证码不能为空") String code) {
    }

    /** 找回密码确认重置请求：手机号 + 验证码 + 两次新密码。 */
    public record ResetPasswordCmd(
            @NotBlank(message = "手机号不能为空") String phone,
            @NotBlank(message = "验证码不能为空") String code,
            @NotBlank(message = "密码不能为空") String password,
            @NotBlank(message = "请再次输入密码") String confirmPassword) {
    }

    /** 刷新 / 登出请求：携带刷新令牌。 */
    public record RefreshCmd(
            @NotBlank(message = "刷新令牌不能为空") String refreshToken) {
    }

    /** 注册请求：未注册手机号 + 两次密码（后端校验一致性与格式）。 */
    public record RegisterCmd(
            @NotBlank(message = "手机号不能为空") String phone,
            @NotBlank(message = "密码不能为空") String password,
            @NotBlank(message = "请再次输入密码") String confirmPassword) {
    }

    /** 企业注册请求：账号密码 + 企业资质 + 一次性短信验证码，营业执照传 fileId。 */
    public record EnterpriseRegisterCmd(
            @NotBlank(message = "手机号不能为空") String phone,
            @NotBlank(message = "密码不能为空") String password,
            @NotBlank(message = "请再次输入密码") String confirmPassword,
            @NotBlank(message = "企业名称不能为空") String companyName,
            @NotBlank(message = "统一社会信用代码不能为空") String creditCode,
            @NotBlank(message = "请上传营业执照") String licenseFileId,
            @NotBlank(message = "请输入短信验证码") String code) {
    }

    /** 企业审核进度查询：手机号 + 短信验证码（未审核通过时没有令牌可用）。 */
    public record AuditProgressCmd(
            @NotBlank(message = "手机号不能为空") String phone,
            @NotBlank(message = "请输入短信验证码") String code) {
    }

    /** 注册结果：只回业务身份，令牌由前端随后走标准登录链路获取。 */
    public record RegisterVO(long customerId, String phone, String nickname, String customerNo) {
    }

    /** 企业注册结果：不签发令牌，必须等管理端审核通过后才能登录。 */
    public record EnterpriseRegisterVO(String phone, String nickname, String auditStatus) {
    }

    /** 令牌响应：双令牌 + 访问令牌有效期（秒）。 */
    public record TokenVO(String accessToken, String refreshToken, long expiresIn) {
    }
}
