package com.club.management.ai.service;

import com.club.management.activity.entity.Activity;
import com.club.management.activity.mapper.ActivityMapper;
import com.club.management.ai.config.AiConfig;
import com.club.management.ai.dto.ReportGenerateDTO;
import com.club.management.ai.entity.ActivityReport;
import com.club.management.ai.mapper.ActivityReportMapper;
import com.club.management.ai.service.impl.AiServiceImpl;
import com.club.management.checkin.entity.CheckinRecord;
import com.club.management.checkin.entity.CheckinTask;
import com.club.management.checkin.mapper.CheckinRecordMapper;
import com.club.management.checkin.mapper.CheckinTaskMapper;
import com.club.management.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServiceImplTest {
    @Mock ActivityMapper activityMapper;
    @Mock CheckinTaskMapper checkinTaskMapper;
    @Mock CheckinRecordMapper checkinRecordMapper;
    @Mock ActivityReportMapper reportMapper;
    @Mock AiConfig aiConfig;
    @Mock RestTemplate restTemplate;
    @InjectMocks AiServiceImpl service;

    @AfterEach
    void cleanup() { service.destroy(); }

    @Test
    void rejectedReportCanBeRegeneratedWithoutCreatingAnotherRow() {
        Activity activity = new Activity();
        activity.setId(7L); activity.setClubId(1L); activity.setStatus("COMPLETED");
        activity.setTitle("编程交流"); activity.setCurrentParticipants(10);
        CheckinTask task = new CheckinTask(); task.setId(2L);
        CheckinRecord record = new CheckinRecord(); record.setRating(4);
        ActivityReport previous = new ActivityReport();
        previous.setId(8L); previous.setStatus("REJECTED"); previous.setRevisionCount(1);
        previous.setRejectReason("补充总结");
        when(activityMapper.selectById(7L)).thenReturn(activity);
        when(checkinTaskMapper.selectOne(any())).thenReturn(task);
        when(checkinRecordMapper.selectCount(any())).thenReturn(1L);
        when(checkinRecordMapper.selectList(any())).thenReturn(List.of(record));
        lenient().when(reportMapper.selectCount(any())).thenReturn(1L);
        lenient().when(reportMapper.selectOne(any())).thenReturn(previous);
        when(aiConfig.getBaseUrl()).thenReturn("http://localhost:8000");
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("content", "重新生成的完整总结")));
        ReportGenerateDTO dto = new ReportGenerateDTO(); dto.setActivityId(7L);

        var result = service.generateReport(dto);

        assertThat(result.getId()).isEqualTo(8L);
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getContent()).contains("完整总结");
        assertThat(previous.getRejectReason()).isNull();
        verify(reportMapper).updateById(previous);
        verify(reportMapper, never()).insert(any(ActivityReport.class));
    }

    @Test
    void failedKnowledgeIngestionMustNotConfirmReport() {
        ActivityReport draft = new ActivityReport();
        draft.setId(8L); draft.setClubId(1L); draft.setActivityId(7L);
        draft.setTitle("测试报告"); draft.setContent("真实反馈"); draft.setStatus("DRAFT");
        when(reportMapper.selectById(8L)).thenReturn(draft);
        when(aiConfig.getBaseUrl()).thenReturn("http://localhost:8000");
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> service.confirmReport(8L)).isInstanceOf(BusinessException.class);
        assertThat(draft.getStatus()).isEqualTo("DRAFT");
        verify(reportMapper, never()).updateById(any(ActivityReport.class));
    }
}
