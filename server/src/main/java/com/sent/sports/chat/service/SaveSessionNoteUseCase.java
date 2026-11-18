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

/** 会话小结落库：必填/枚举校验在领域层，重复填写按 session_id 幂等更新。 */
@Service
public class SaveSessionNoteUseCase {

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
        if (owner == null || owner != cmd.staffId()) {
            throw new BizException(ErrorCode.BIZ_REJECT, "只有本次接待的客服可以填写小结");
        }
        ChatSessionNote note = new ChatSessionNote(cmd.sessionId(), session.getCustomerId(), cmd.staffId(),
                cmd.summary(), cmd.category(), cmd.info(), cmd.result(), cmd.solution());

        ChatSessionNotePO existing = noteMapper.selectBySessionId(cmd.sessionId());
        if (existing == null) {
            ChatSessionNotePO po = new ChatSessionNotePO();
            po.setSessionId(note.getSessionId());
            po.setCustomerId(note.getCustomerId());
            po.setStaffId(note.getStaffId());
            po.setSummary(note.getSummary());
            po.setCategory(note.getCategory().name());
            po.setIssueInfo(note.getInfo().name());
            po.setResult(note.getResult().name());
            po.setSolution(note.getSolution());
            noteMapper.insert(po);
        } else {
            noteMapper.updateBySessionId(note.getSessionId(), note.getStaffId(), note.getSummary(),
                    note.getCategory().name(), note.getInfo().name(), note.getResult().name(), note.getSolution());
        }
        return note;
    }
}
