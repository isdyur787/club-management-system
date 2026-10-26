package com.club.management.ai.service;

import com.club.management.ai.dto.ChatRequestDTO;
import com.club.management.ai.dto.ExperienceRequestDTO;
import com.club.management.ai.dto.ReportGenerateDTO;
import com.club.management.ai.vo.ChatResponseVO;
import com.club.management.ai.vo.ExperienceSuggestionVO;
import com.club.management.ai.vo.KnowledgeDocVO;
import com.club.management.ai.vo.ReportDraftVO;
import com.club.management.common.response.PageResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiService {

    ChatResponseVO chat(ChatRequestDTO dto);

    SseEmitter chatStream(ChatRequestDTO dto);

    ReportDraftVO generateReport(ReportGenerateDTO dto);

    ReportDraftVO getReport(Long activityId);

    void confirmReport(Long id);

    void rejectReport(Long id, String reason);

    void confirmReportByActivityId(Long activityId);

    void rejectReportByActivityId(Long activityId, String reason);

    ExperienceSuggestionVO getExperience(ExperienceRequestDTO dto);

    KnowledgeDocVO uploadKnowledgeDocument(MultipartFile file, String docType, String title, Long clubId);

    PageResult<KnowledgeDocVO> listKnowledgeDocuments(Long clubId, int pageNum, int pageSize);

    void deleteKnowledgeDocument(Long id);
}
