package com.club.management.points.service;

import com.club.management.club.mapper.ClubMemberMapper;
import com.club.management.common.util.SecurityContext;
import com.club.management.common.util.TenantContext;
import com.club.management.points.mapper.PointsLogMapper;
import com.club.management.points.service.impl.PointsServiceImpl;
import com.club.management.points.vo.PointsClubVO;
import com.club.management.points.vo.PointsSummaryVO;
import com.club.management.user.entity.Role;
import com.club.management.user.mapper.RoleMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointsServiceImplTest {
    @Mock PointsLogMapper pointsLogMapper;
    @Mock ClubMemberMapper clubMemberMapper;
    @Mock RoleMapper roleMapper;
    @InjectMocks PointsServiceImpl service;

    @BeforeEach void setup() {
        SecurityContext.setUserId(3L);
        TenantContext.setClubId(1L);
    }
    @AfterEach void clear() { SecurityContext.clear(); TenantContext.clear(); }

    @Test void switchesToTheSelectedAccessibleClubBeforeReadingPoints() {
        when(roleMapper.findRolesByUserId(3L)).thenReturn(List.of());
        PointsClubVO club = new PointsClubVO(); club.setId(6L); club.setClubName("街舞社");
        when(pointsLogMapper.selectAvailableClubs(3L, false)).thenReturn(List.of(club));
        when(pointsLogMapper.selectTotalPoints(6L, 3L)).thenAnswer(call -> {
            assertThat(TenantContext.getClubId()).isEqualTo(6L);
            return 11;
        });
        when(pointsLogMapper.selectUserRank(6L, 3L)).thenReturn(1);
        when(clubMemberMapper.selectCount(any())).thenReturn(12L);
        when(pointsLogMapper.selectClubOverview(eq(6L), any(), any()))
            .thenReturn(Map.of("club_total_points", 80, "month_points", 30, "scored_member_count", 8));
        PointsSummaryVO.DailyPoint day = new PointsSummaryVO.DailyPoint();
        day.setDate(LocalDate.now().toString()); day.setPoints(7);
        when(pointsLogMapper.selectDailyPoints(eq(6L), any(), any())).thenReturn(List.of(day));
        var summary = service.getSummary(6L);
        assertThat(summary.getClubTotalPoints()).isEqualTo(80);
        assertThat(summary.getMonthPoints()).isEqualTo(30);
        assertThat(summary.getDailyPoints()).hasSize(7);
        assertThat(summary.getDailyPoints().get(0).getPoints()).isZero();
        assertThat(summary.getDailyPoints().get(6).getPoints()).isEqualTo(7);
    }

    @Test void rejectsClubsOutsideTheUsersMemberships() {
        when(roleMapper.findRolesByUserId(3L)).thenReturn(List.of());
        when(pointsLogMapper.selectAvailableClubs(3L, false)).thenReturn(List.of());
        assertThatThrownBy(() -> service.getSummary(8L)).isInstanceOf(RuntimeException.class);
        verify(pointsLogMapper, never()).selectTotalPoints(anyLong(), anyLong());
    }

    @Test void administratorsCanChooseAllApprovedClubs() {
        Role admin = new Role(); admin.setRoleCode("ADMIN");
        when(roleMapper.findRolesByUserId(3L)).thenReturn(List.of(admin));
        when(pointsLogMapper.selectAvailableClubs(3L, true)).thenReturn(List.of());
        service.getAvailableClubs();
        verify(pointsLogMapper).selectAvailableClubs(3L, true);
    }
}
