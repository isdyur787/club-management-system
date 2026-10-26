package com.club.management.ai.controller;

import com.club.management.ai.dto.ChatRequestDTO;
import com.club.management.ai.dto.ExperienceRequestDTO;
import com.club.management.ai.dto.ReportGenerateDTO;
import com.club.management.ai.dto.ReportReviewDTO;
import com.club.management.ai.service.AiService;
import com.club.management.ai.vo.ChatResponseVO;
import com.club.management.ai.vo.ExperienceSuggestionVO;
import com.club.management.ai.vo.KnowledgeDocVO;
import com.club.management.ai.vo.ReportDraftVO;
import com.club.management.common.annotation.OperationLog;
import com.club.management.common.annotation.RequirePermission;
import com.club.management.common.response.PageResult;
import com.club.management.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "AI 智能助手", description = "包含对话、成果报告、经验参考、知识库管理")
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    // === 对话 ===
    @Operation(summary = "AI 对话", description = "基于 RAG 的智能问答，支持 Function Calling + 语义缓存 + 安全护栏")
    @PostMapping("/chat")
    @RequirePermission("ai:chat")
    @OperationLog(module = "AI", operation = "AI对话")
    public R<ChatResponseVO> chat(@RequestBody @Valid ChatRequestDTO dto) {
        return R.ok(aiService.chat(dto));
    }

    // === SSE 流式对话 ===
    @Operation(summary = "AI 流式对话", description = "SSE 流式输出，逐句推送回答内容")
    @PostMapping("/chat/stream")
    @RequirePermission("ai:chat")
    @OperationLog(module = "AI", operation = "AI流式对话")
    public SseEmitter chatStream(@RequestBody @Valid ChatRequestDTO dto) {
        return aiService.chatStream(dto);
    }

    // === 成果报告 ===
    @Operation(summary = "生成成果报告", description = "基于活动数据和打卡汇编自动生成成果报告")
    @PostMapping("/report/generate")
    @RequirePermission("ai:report:manage")
    @OperationLog(module = "AI", operation = "生成成果报告")
    public R<ReportDraftVO> generateReport(@RequestBody @Valid ReportGenerateDTO dto) {
        return R.ok(aiService.generateReport(dto));
    }

    @Operation(summary = "获取报告")
    @GetMapping("/report/{activityId}")
    @RequirePermission("ai:report:manage")
    public R<ReportDraftVO> getReport(
            @Parameter(description = "活动ID") @PathVariable Long activityId) {
        return R.ok(aiService.getReport(activityId));
    }

    @Operation(summary = "确认报告")
    @PostMapping("/report/{activityId}/confirm")
    @RequirePermission("ai:report:manage")
    @OperationLog(module = "AI", operation = "确认报告")
    public R<Void> confirmReport(@PathVariable Long activityId) {
        aiService.confirmReportByActivityId(activityId);
        return R.ok();
    }

    @Operation(summary = "打回报告")
    @PostMapping("/report/{activityId}/reject")
    @RequirePermission("ai:report:manage")
    @OperationLog(module = "AI", operation = "打回报告")
    public R<Void> rejectReport(@PathVariable Long activityId,
                                 @RequestBody ReportReviewDTO dto) {
        aiService.rejectReportByActivityId(activityId, dto.getReason());
        return R.ok();
    }

    // === 经验参考 ===
    @Operation(summary = "获取经验参考", description = "基于历史活动经验生成策划建议")
    @PostMapping("/experience")
    @RequirePermission("ai:chat")
    public R<ExperienceSuggestionVO> getExperience(
            @RequestBody @Valid ExperienceRequestDTO dto) {
        return R.ok(aiService.getExperience(dto));
    }

    // === 知识库 ===
    @Operation(summary = "上传知识库文档", description = "上传文档并自动向量化入库")
    @PostMapping("/knowledge/documents")
    @RequirePermission("ai:knowledge:manage")
    @OperationLog(module = "AI", operation = "上传知识库文档")
    public R<KnowledgeDocVO> uploadDocument(
            @RequestParam MultipartFile file,
            @Parameter(description = "文档类型") @RequestParam String docType,
            @Parameter(description = "文档标题") @RequestParam String title,
            @Parameter(description = "社团ID") @RequestParam Long clubId) {
        return R.ok(aiService.uploadKnowledgeDocument(file, docType, title, clubId));
    }

    @Operation(summary = "知识库文档列表")
    @GetMapping("/knowledge/documents")
    @RequirePermission("ai:knowledge:manage")
    public R<PageResult<KnowledgeDocVO>> listDocuments(
            @Parameter(description = "社团ID") @RequestParam Long clubId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(aiService.listKnowledgeDocuments(clubId, pageNum, pageSize));
    }

    @Operation(summary = "删除知识库文档")
    @DeleteMapping("/knowledge/documents/{id}")
    @RequirePermission("ai:knowledge:manage")
    @OperationLog(module = "AI", operation = "删除知识库文档")
    public R<Void> deleteDocument(@PathVariable Long id) {
        aiService.deleteKnowledgeDocument(id);
        return R.ok();
    }
}
