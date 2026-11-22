package com.sent.sports.review.mapper.po;

import java.time.LocalDateTime;

/**
 * 评价列表行（读面）。
 * <p>昵称随行带出：列表一次 LEFT JOIN {@code customer} 取全，避免逐条回查造成 N+1。
 * 昵称只用于服务端脱敏，绝不原样透出给前端。</p>
 */
public class ReviewRow {

    private Long id;
    private Long customerId;
    private Integer rating;
    private String content;
    private String imagesJson;
    private Integer isAnonymous;
    private LocalDateTime createdAt;
    /** 客户昵称（原始值，脱敏在 service 完成）。 */
    private String nickname;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getImagesJson() {
        return imagesJson;
    }

    public void setImagesJson(String imagesJson) {
        this.imagesJson = imagesJson;
    }

    public Integer getIsAnonymous() {
        return isAnonymous;
    }

    public void setIsAnonymous(Integer isAnonymous) {
        this.isAnonymous = isAnonymous;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
