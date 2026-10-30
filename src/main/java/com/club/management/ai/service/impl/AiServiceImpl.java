package com.club.management.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.club.management.ai.config.AiConfig;
import com.club.management.ai.dto.ChatRequestDTO;
import com.club.management.ai.dto.ExperienceRequestDTO;
import com.club.management.ai.dto.ReportGenerateDTO;
import com.club.management.ai.entity.ActivityReport;
import com.club.management.ai.entity.ChatSession;
import com.club.management.ai.entity.KnowledgeDocument;
import com.club.management.ai.mapper.ActivityReportMapper;
import com.club.management.ai.mapper.ChatSessionMapper;
import com.club.management.ai.mapper.KnowledgeDocumentMapper;
import com.club.management.ai.service.AiService;
import com.club.management.ai.vo.ChatResponseVO;
import com.club.management.ai.vo.ExperienceSuggestionVO;
import com.club.management.ai.vo.KnowledgeDocVO;
import com.club.management.ai.vo.ReportDraftVO;
import com.club.management.activity.entity.Activity;
import com.club.management.activity.mapper.ActivityMapper;
import com.club.management.checkin.entity.CheckinRecord;
import com.club.management.checkin.entity.CheckinTask;
import com.club.management.checkin.mapper.CheckinRecordMapper;
import com.club.management.checkin.mapper.CheckinTaskMapper;
import com.club.management.common.enums.ActiveStatus;
import com.club.management.common.exception.BusinessException;
import com.club.management.common.exception.ErrorCode;
import com.club.management.common.exception.NotFoundException;
import com.club.management.common.response.PageResult;
import com.club.management.common.util.SecurityUtil;
import com.club.management.common.util.TenantContext;
import com.club.management.file.service.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Java 侧 AI 服务实现，代理 Python AI 接口、转发 SSE 事件，并维护会话、报告和知识库元数据及缓存。
 */
@Slf4j
@Service
public class AiServiceImpl implements AiService {

    /**
     * SSE 转发会等待 Python 的网络响应，交给共享线程池执行，避免一直占用 Servlet 请求线程。
     * 固定工作线程数限制同时执行的转发任务；应用关闭时由 destroy 统一释放。
     */
    private final ExecutorService sseExecutor = Executors.newFixedThreadPool(
            Math.max(16, Runtime.getRuntime().availableProcessors() * 2),
            r -> {
                Thread t = new Thread(r, "sse-stream");
                t.setDaemon(true);
                return t;
            }
    );

    /**
     * 每用户 SSE 并发限流：每个用户最多 2 个并发 SSE 请求
     * 使用定时清理防止内存泄漏
     */
    private final ConcurrentHashMap<Long, Semaphore> userSseLimiters = new ConcurrentHashMap<>();

    /** 定时清理空闲的 Semaphore，防止内存泄漏 */
    private final ScheduledExecutorService limiterCleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "limiter-cleaner");
        t.setDaemon(true);
        return t;
    });

    { // 初始化块：每 5 分钟清理空闲 limiter
        limiterCleaner.scheduleAtFixedRate(() -> {
            userSseLimiters.entrySet().removeIf(e -> {
                Semaphore sem = e.getValue();
                // 仅当所有许可都已释放时才移除
                return sem.availablePermits() == 2;
            });
        }, 5, 5, java.util.concurrent.TimeUnit.MINUTES);
    }

    private Semaphore getUserLimiter(Long userId) {
        return userSseLimiters.computeIfAbsent(userId, k -> new Semaphore(2));
    }

    @PreDestroy
    public void destroy() {
        limiterCleaner.shutdown();
        sseExecutor.shutdown();
        try {
            if (!limiterCleaner.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                limiterCleaner.shutdownNow();
            }
            if (!sseExecutor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                sseExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            limiterCleaner.shutdownNow();
            sseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Autowired
    private AiConfig aiConfig;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("aiRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    private ActivityReportMapper reportMapper;

    @Autowired
    private ChatSessionMapper sessionMapper;

    @Autowired
    private KnowledgeDocumentMapper knowledgeDocMapper;

    @Autowired
    private ActivityMapper activityMapper;

    @Autowired
    private CheckinTaskMapper checkinTaskMapper;

    @Autowired
    private CheckinRecordMapper checkinRecordMapper;

    @Autowired
    private FileService fileService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public SseEmitter chatStream(ChatRequestDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();

        // 校验并发限流
        Semaphore limiter = getUserLimiter(userId);
        if (!limiter.tryAcquire()) {
            return createErrorEmitter("您的并发请求过多，请稍后再试");
        }

        String sessionId = dto.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        SseEmitter emitter = new SseEmitter(120_000L); // 120s 超时
        emitter.onCompletion(limiter::release);
        emitter.onTimeout(limiter::release);
        emitter.onError(e -> limiter.release());

        // 先在请求线程取出所需参数，再交给共享线程池；普通 ThreadLocal 身份不会自动传入工作线程。
        final String finalSessionId = sessionId;
        doStreamChat(emitter, dto, finalSessionId);

        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> log.warn("SSE emitter error: {}", e.getMessage()));

        return emitter;
    }

    /**
     * 创建携带错误信息的 SSE emitter
     */
    private SseEmitter createErrorEmitter(String message) {
        SseEmitter errorEmitter = new SseEmitter(5_000L);
        sseExecutor.submit(() -> {
            try {
                errorEmitter.send(SseEmitter.event().name("error")
                        .data("{\"message\":\"" + message + "\"}"));
                errorEmitter.complete();
            } catch (Exception ignored) {
                errorEmitter.completeWithError(ignored);
            }
        });
        return errorEmitter;
    }

    /**
     * 异步执行 SSE 流式转发
     */
    private void doStreamChat(SseEmitter emitter, ChatRequestDTO dto, String sessionId) {
        sseExecutor.submit(() -> {
            try {
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(
                        Map.of("type", "session", "sessionId", sessionId))));
                String url = aiConfig.getBaseUrl() + "/chat/stream";
                Map<String, Object> body = new HashMap<>();
                body.put("club_id", dto.getClubId());
                body.put("session_id", sessionId);
                body.put("message", dto.getMessage());

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                restTemplate.execute(
                    url,
                    HttpMethod.POST,
                    req -> {
                        req.getHeaders().putAll(headers);
                        req.getBody().write(objectMapper.writeValueAsBytes(body));
                    },
                    response -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                // Python 返回的是 SSE 行协议；这里只转发 data 行，[DONE] 转为前端 done 事件。
                                if (line.startsWith("data:")) {
                                    String data = line.startsWith("data: ") ? line.substring(6) : line.substring(5);
                                    if ("[DONE]".equals(data)) {
                                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                        break;
                                    }
                                    emitter.send(SseEmitter.event().data(data));
                                }
                            }
                        }
                        return null;
                    }
                );
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE stream error: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error")
                        .data("{\"type\":\"error\",\"content\":\"流式对话服务异常\"}"));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChatResponseVO chat(ChatRequestDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();

        // 创建或获取会话
        String sessionId = dto.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
            ChatSession session = new ChatSession();
            session.setSessionId(sessionId);
            session.setClubId(dto.getClubId());
            session.setUserId(userId);
            session.setTitle(dto.getMessage().substring(0, Math.min(50, dto.getMessage().length())));
            session.setMessageCount(0);
            session.setLastActiveAt(LocalDateTime.now());
            session.setStatus(ActiveStatus.ACTIVE.getCode());
            sessionMapper.insert(session);
        }

        // 转发到 Python 服务
        String url = aiConfig.getBaseUrl() + "/chat";
        Map<String, Object> body = new HashMap<>();
        body.put("club_id", dto.getClubId());
        body.put("session_id", sessionId);
        body.put("message", dto.getMessage());

        try {
            ResponseEntity<Map> response = retryPostForEntity(url, body, Map.class);
            Map<String, Object> data = response.getBody();
            if (data == null) {
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }

            ChatResponseVO vo = new ChatResponseVO();
            vo.setSessionId(sessionId);
            vo.setAnswer((String) data.get("answer"));
            vo.setSources((List<Map<String, String>>) data.get("sources"));

            // 更新会话
            LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatSession::getSessionId, sessionId);
            ChatSession session = sessionMapper.selectOne(wrapper);
            if (session != null) {
                session.setMessageCount(session.getMessageCount() + 1);
                session.setLastActiveAt(LocalDateTime.now());
                sessionMapper.updateById(session);
            }

            return vo;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "report", key = "#dto.activityId")
    public ReportDraftVO generateReport(ReportGenerateDTO dto) {
        // 校验活动
        Activity activity = activityMapper.selectById(dto.getActivityId());
        if (activity == null) {
            throw new NotFoundException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        if (!"COMPLETED".equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_COMPLETED);
        }

        // 校验打卡数据
        LambdaQueryWrapper<CheckinTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(CheckinTask::getActivityId, dto.getActivityId());
        CheckinTask task = checkinTaskMapper.selectOne(taskWrapper);
        if (task == null) {
            throw new BusinessException(ErrorCode.CHECKIN_DATA_EMPTY);
        }

        LambdaQueryWrapper<CheckinRecord> recordWrapper = new LambdaQueryWrapper<>();
        recordWrapper.eq(CheckinRecord::getTaskId, task.getId());
        int checkinCount = Math.toIntExact(checkinRecordMapper.selectCount(recordWrapper));
        if (checkinCount == 0) {
            throw new BusinessException(ErrorCode.CHECKIN_DATA_EMPTY);
        }

        // 校验报告不存在
        LambdaQueryWrapper<ActivityReport> reportWrapper = new LambdaQueryWrapper<>();
        reportWrapper.eq(ActivityReport::getActivityId, dto.getActivityId());
        ActivityReport existing = reportMapper.selectOne(reportWrapper);
        if (existing != null && !"REJECTED".equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_EXISTS);
        }

        // 调用 Python 服务生成报告
        String url = aiConfig.getBaseUrl() + "/report/generate";
        Map<String, Object> body = new HashMap<>();
        body.put("activity_id", dto.getActivityId());
        if (existing != null) body.put("feedback", existing.getRejectReason());

        try {
            ResponseEntity<Map> response = retryPostForEntity(url, body, Map.class);
            Map<String, Object> data = response.getBody();

            // 保存报告
            ActivityReport report = existing != null ? existing : new ActivityReport();
            report.setActivityId(dto.getActivityId());
            report.setClubId(activity.getClubId());
            report.setTitle(activity.getTitle() + "-成果报告");
            report.setContent(data != null ? (String) data.getOrDefault("content", "") : "");
            report.setStatus("DRAFT");
            if (existing == null) report.setRevisionCount(0);
            report.setRejectReason(null);
            report.setIsIngested(ActivityReport.NOT_INGESTED);

            // 统计信息
            List<CheckinRecord> records = checkinRecordMapper.selectList(recordWrapper);
            double avgRating = records.stream()
                    .filter(r -> r.getRating() != null)
                    .mapToInt(CheckinRecord::getRating)
                    .average()
                    .orElse(0.0);
            report.setAvgRating(BigDecimal.valueOf(avgRating).setScale(1, java.math.RoundingMode.HALF_UP));
            report.setParticipantCount(activity.getCurrentParticipants());
            report.setCheckinCount(checkinCount);

            if (existing == null) reportMapper.insert(report);
            else reportMapper.updateById(report);

            return toReportVO(report);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    @Override
    @Cacheable(value = "report", key = "#activityId")
    public ReportDraftVO getReport(Long activityId) {
        LambdaQueryWrapper<ActivityReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ActivityReport::getActivityId, activityId);
        ActivityReport report = reportMapper.selectOne(wrapper);
        if (report == null) {
            throw new NotFoundException(ErrorCode.AI_REPORT_NOT_FOUND);
        }
        return toReportVO(report);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "report", allEntries = true)
    public void confirmReport(Long id) {
        ActivityReport report = reportMapper.selectById(id);
        doConfirmReport(report);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "report", allEntries = true)
    public void rejectReport(Long id, String reason) {
        ActivityReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new NotFoundException(ErrorCode.AI_REPORT_NOT_FOUND);
        }

        report.setStatus("REJECTED");
        report.setRejectReason(reason);
        report.setRevisionCount(report.getRevisionCount() + 1);
        reportMapper.updateById(report);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "report", key = "#activityId")
    public void confirmReportByActivityId(Long activityId) {
        LambdaQueryWrapper<ActivityReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ActivityReport::getActivityId, activityId);
        ActivityReport report = reportMapper.selectOne(wrapper);
        doConfirmReport(report);
    }

    private void doConfirmReport(ActivityReport report) {
        if (report == null) {
            throw new NotFoundException(ErrorCode.AI_REPORT_NOT_FOUND);
        }
        if ("CONFIRMED".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.AI_REPORT_ALREADY_CONFIRMED);
        }

        // 先让 Python 完成知识库入库，再保存 CONFIRMED；入库失败时不把草稿标为已确认。
        // Python 的外部写入不受本地数据库事务回滚控制。
        try {
            String url = aiConfig.getBaseUrl() + "/knowledge/ingest-report";
            Map<String, Object> body = Map.of(
                "club_id", report.getClubId(),
                "activity_id", report.getActivityId(),
                "title", report.getTitle(),
                "content", report.getContent()
            );
            retryPostForEntity(url, body, Map.class);

            report.setIsIngested(ActivityReport.INGESTED);
            report.setIngestedAt(LocalDateTime.now());
            report.setStatus("CONFIRMED");
            report.setConfirmedBy(SecurityUtil.getCurrentUserId());
            report.setConfirmedAt(LocalDateTime.now());
            reportMapper.updateById(report);
        } catch (Exception e) {
            log.warn("报告入库失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "report", key = "#activityId")
    public void rejectReportByActivityId(Long activityId, String reason) {
        LambdaQueryWrapper<ActivityReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ActivityReport::getActivityId, activityId);
        ActivityReport report = reportMapper.selectOne(wrapper);
        if (report == null) {
            throw new NotFoundException(ErrorCode.AI_REPORT_NOT_FOUND);
        }

        report.setStatus("REJECTED");
        report.setRejectReason(reason);
        report.setRevisionCount(report.getRevisionCount() + 1);
        reportMapper.updateById(report);
    }

    @Override
    public ExperienceSuggestionVO getExperience(ExperienceRequestDTO dto) {
        String url = aiConfig.getBaseUrl() + "/experience";
        Map<String, Object> body = new HashMap<>();
        body.put("club_id", dto.getClubId());
        body.put("activity_type", dto.getActivityType());
        body.put("title", dto.getTitle());
        body.put("location", dto.getLocation());
        body.put("max_participants", dto.getMaxParticipants());
        body.put("budget", dto.getBudget());

        try {
            ResponseEntity<Map> response = retryPostForEntity(url, body, Map.class);
            Map<String, Object> data = response.getBody();
            if (data == null) {
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }

            ExperienceSuggestionVO vo = new ExperienceSuggestionVO();
            vo.setHasHistory((Boolean) data.getOrDefault("has_history", false));
            vo.setExperienceSummary((String) data.getOrDefault("experience_summary", ""));
            vo.setSuggestions((List<String>) data.getOrDefault("suggestions", new ArrayList<>()));
            vo.setRiskWarnings((List<String>) data.getOrDefault("risk_warnings", new ArrayList<>()));
            return vo;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "knowledge", allEntries = true)
    public KnowledgeDocVO uploadKnowledgeDocument(MultipartFile file, String docType, String title, Long clubId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Long tenantId = TenantContext.getClubId();
        if (clubId == null || clubId < 1 || (tenantId != null && !tenantId.equals(clubId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 上传文件
        Map<String, Object> uploadResult = fileService.upload(file, "knowledge");
        String filePath = (String) uploadResult.get("url");

        // 调用 Python 服务入库（Python 负责写入 MySQL）
        String url = aiConfig.getBaseUrl() + "/knowledge/documents/ingest";
        Map<String, Object> body = new HashMap<>();
        body.put("club_id", clubId);
        body.put("doc_type", docType);
        body.put("title", title);
        body.put("file_path", filePath);

        try {
            ResponseEntity<Map> response = retryPostForEntity(url, body, Map.class);
            Map<String, Object> data = response.getBody();
            if (data == null) {
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }

            // 从 Python 响应构建 VO（Python 已写入 MySQL）
            KnowledgeDocVO vo = new KnowledgeDocVO();
            vo.setId(data.get("id") != null ? ((Number) data.get("id")).longValue() : null);
            vo.setClubId(clubId);
            vo.setDocType(docType);
            vo.setTitle(title);
            vo.setSourceType("upload");
            vo.setChunkCount(data.get("chunk_count") != null ? (Integer) data.get("chunk_count") : 0);
            return vo;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    @Override
    @Cacheable(value = "knowledge", key = "#clubId + ':' + #pageNum + ':' + #pageSize")
    public PageResult<KnowledgeDocVO> listKnowledgeDocuments(Long clubId, int pageNum, int pageSize) {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDocument::getClubId, clubId)
                .eq(KnowledgeDocument::getStatus, ActiveStatus.ACTIVE.getCode())
                .orderByDesc(KnowledgeDocument::getCreatedAt);

        Page<KnowledgeDocument> page = new Page<>(pageNum, pageSize);
        knowledgeDocMapper.selectPage(page, wrapper);

        List<KnowledgeDocVO> voList = page.getRecords().stream()
                .map(this::toKnowledgeVO)
                .collect(Collectors.toList());

        return new PageResult<>(pageNum, pageSize, page.getTotal(), voList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "knowledge", allEntries = true)
    public void deleteKnowledgeDocument(Long id) {
        KnowledgeDocument doc = knowledgeDocMapper.selectById(id);
        if (doc == null) {
            throw new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // 调用 Python 服务删除向量
        try {
            String url = aiConfig.getBaseUrl() + "/knowledge/documents/" + id;
            restTemplate.delete(url);
        } catch (Exception e) {
            log.warn("删除向量失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }

        // 逻辑删除
        doc.setStatus(ActiveStatus.INACTIVE.getCode());
        knowledgeDocMapper.updateById(doc);
    }

    private ReportDraftVO toReportVO(ActivityReport report) {
        ReportDraftVO vo = new ReportDraftVO();
        vo.setId(report.getId());
        vo.setActivityId(report.getActivityId());
        vo.setTitle(report.getTitle());
        vo.setContent(report.getContent());
        vo.setFilePath(report.getFilePath());
        vo.setAvgRating(report.getAvgRating());
        vo.setParticipantCount(report.getParticipantCount());
        vo.setCheckinCount(report.getCheckinCount());
        vo.setStatus(report.getStatus());
        vo.setRevisionCount(report.getRevisionCount());
        vo.setCreatedAt(report.getCreatedAt());
        return vo;
    }

    private KnowledgeDocVO toKnowledgeVO(KnowledgeDocument doc) {
        KnowledgeDocVO vo = new KnowledgeDocVO();
        vo.setId(doc.getId());
        vo.setClubId(doc.getClubId());
        vo.setDocType(doc.getDocType());
        vo.setTitle(doc.getTitle());
        vo.setSourceType(doc.getSourceType());
        vo.setChunkCount(doc.getChunkCount());
        vo.setCreatedAt(doc.getCreatedAt());
        return vo;
    }

    /**
     * AI 请求最多尝试 3 次，仅连接/读取等 ResourceAccessException 触发重试，间隔依次为 1、2 秒。
     * HTTP 业务错误不会走此重试；写入类接口是否可安全重复调用取决于 Python 端的幂等处理。
     */
    @SuppressWarnings("unchecked")
    private <T> org.springframework.http.ResponseEntity<T> retryPostForEntity(
            String url, Object request, Class<T> responseType) {
        int maxAttempts = 3;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                return restTemplate.postForEntity(url, request, responseType);
            } catch (org.springframework.web.client.ResourceAccessException e) {
                if (i == maxAttempts - 1) throw e;
                log.warn("AI 服务请求失败，第 {} 次重试: {}", i + 1, e.getMessage());
                try { Thread.sleep(1000L * (i + 1)); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
        throw new org.springframework.web.client.ResourceAccessException("重试耗尽");
    }
}
