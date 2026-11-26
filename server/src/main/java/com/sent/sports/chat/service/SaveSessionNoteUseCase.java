package com.sent.sports.chat.service;

import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionNote;
import com.sent.sports.chat.mapper.ChatSessionNoteMapper;
import com.sent.sports.chat.mapper.po.ChatSessionNotePO;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.dto.SaveNoteCmd;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/** 服务工单落库：必填/枚举校验在领域层，重复填写按 session_id 幂等更新。 */
@Service
public class SaveSessionNoteUseCase {

    private static final List<DateTimeFormatter> FOLLOWUP_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

    private final ChatSessionRepository sessionRepository;
    private final ChatSessionNoteMapper noteMapper;

    public SaveSessionNoteUseCase(ChatSessionRepository sessionRepository, ChatSessionNoteMapper noteMapper) {
        this.sessionRepository = sessionRepository;
        this.noteMapper = noteMapper;
    }

    @UseCase
    public ChatSessionNote save(SaveNoteCmd cmd) {
        ChatSession session = sessionRepository.findById(cmd.sessionId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        Long owner = session.getStaffId();
        // Long 必须按值比：接待人 ID 来自数据库包装类型，与请求反序列化的 Long 不是同一对象。
        if (owner == null || owner.longValue() != cmd.staffId()) {
            throw new BizException(ErrorCode.BIZ_REJECT, "只有本次接待的客服可以填写服务工单");
        }
        LocalDateTime followupTime = parseFollowupTime(cmd.followupTime());
        // 暂存（submit=false）允许缺项，提交才跑五项必填校验——校验口径在领域层统一。
        ChatSessionNote note = cmd.submit()
                ? new ChatSessionNote(cmd.sessionId(), session.getCustomerId(), cmd.staffId(),
                        cmd.summary(), cmd.category(), cmd.subCategory(), cmd.customerDemand(),
                        cmd.verifiedInfo(), cmd.info(), cmd.result(), cmd.solution(),
                        cmd.needFollowup(), followupTime, cmd.followupContact(), cmd.attachmentsJson())
                : ChatSessionNote.draft(cmd.sessionId(), session.getCustomerId(), cmd.staffId(),
                        cmd.summary(), cmd.category(), cmd.subCategory(), cmd.customerDemand(),
                        cmd.verifiedInfo(), cmd.info(), cmd.result(), cmd.solution(),
                        cmd.needFollowup(), followupTime, cmd.followupContact(), cmd.attachmentsJson());

        String submitStatus = note.getSubmitStatus().name();
        LocalDateTime submittedAt = note.getSubmitStatus() == ChatSessionNote.SubmitStatus.SUBMITTED
                ? LocalDateTime.now() : null;

        ChatSessionNotePO existing = noteMapper.selectBySessionId(cmd.sessionId());
        if (existing == null) {
            ChatSessionNotePO po = new ChatSessionNotePO();
            po.setSessionId(note.getSessionId());
            po.setCustomerId(note.getCustomerId());
            po.setStaffId(note.getStaffId());
            po.setSummary(note.getSummary());
            po.setCategory(note.getCategory() == null ? null : note.getCategory().name());
            po.setSubCategory(note.getSubCategory());
            po.setCustomerDemand(note.getCustomerDemand());
            po.setVerifiedInfo(note.getVerifiedInfo());
            po.setIssueInfo(note.getInfo() == null ? null : note.getInfo().name());
            po.setResult(note.getResult() == null ? null : note.getResult().name());
            po.setSolution(note.getSolution());
            po.setNeedFollowup(note.isNeedFollowup());
            po.setFollowupTime(note.getFollowupTime());
            po.setFollowupContact(note.getFollowupContact());
            po.setAttachmentsJson(note.getAttachmentsJson());
            po.setSubmitStatus(submitStatus);
            po.setSubmittedAt(submittedAt);
            noteMapper.insert(po);
        } else {
            // 重复提交原位覆盖：管理端审查里同一个会话号只保留最新一版（用户要求）。
            noteMapper.updateBySessionId(note.getSessionId(), note.getStaffId(), note.getSummary(),
                    note.getCategory() == null ? null : note.getCategory().name(),
                    note.getSubCategory(), note.getCustomerDemand(),
                    note.getVerifiedInfo(), note.getInfo() == null ? null : note.getInfo().name(),
                    note.getResult() == null ? null : note.getResult().name(), note.getSolution(),
                    note.isNeedFollowup(), note.getFollowupTime(), note.getFollowupContact(),
                    note.getAttachmentsJson(), submitStatus, submittedAt);
        }
        return note;
    }

    /** 前端 el-date-picker 可能给 ISO 或「yyyy-MM-dd HH:mm」；空串按未填处理。 */
    private static LocalDateTime parseFollowupTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        for (DateTimeFormatter formatter : FOLLOWUP_FORMATS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // 试下一种格式
            }
        }
        throw new BizException(ErrorCode.SERVICE_TICKET_INCOMPLETE, "回访时间格式不合法");
    }
}
