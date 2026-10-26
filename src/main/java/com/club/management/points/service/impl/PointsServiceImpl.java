package com.club.management.points.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.club.management.club.entity.ClubMember;
import com.club.management.club.mapper.ClubMemberMapper;
import com.club.management.common.response.PageResult;
import com.club.management.common.util.SecurityUtil;
import com.club.management.common.util.TenantContext;
import com.club.management.common.exception.ForbiddenException;
import com.club.management.common.exception.ErrorCode;
import com.club.management.points.entity.PointsLog;
import com.club.management.points.mapper.PointsLogMapper;
import com.club.management.points.service.PointsService;
import com.club.management.points.vo.PointsLogVO;
import com.club.management.points.vo.PointsRankingVO;
import com.club.management.points.vo.PointsSummaryVO;
import com.club.management.points.vo.PointsClubVO;
import com.club.management.user.mapper.RoleMapper;
import com.club.management.user.entity.User;
import com.club.management.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.LocalDate;

@Slf4j
@Service
public class PointsServiceImpl implements PointsService {

    @Autowired
    private PointsLogMapper pointsLogMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ClubMemberMapper clubMemberMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Override
    public List<PointsClubVO> getAvailableClubs() {
        Long userId = SecurityUtil.getCurrentUserId();
        boolean allClubs = roleMapper.findRolesByUserId(userId).stream()
                .anyMatch(role -> "ADMIN".equals(role.getRoleCode()) || "TEACHER".equals(role.getRoleCode()));
        return pointsLogMapper.selectAvailableClubs(userId, allClubs);
    }

    private void selectClub(Long clubId) {
        if (clubId == null || getAvailableClubs().stream().noneMatch(club -> clubId.equals(club.getId()))) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        TenantContext.setClubId(clubId);
    }

    @Override
    public PageResult<PointsLogVO> getMyLogs(Long clubId, int pageNum, int pageSize) {
        selectClub(clubId);
        Long userId = SecurityUtil.getCurrentUserId();
        return queryLogs(clubId, userId, pageNum, pageSize);
    }

    @Override
    public PageResult<PointsLogVO> getClubLogs(Long clubId, int pageNum, int pageSize) {
        selectClub(clubId);
        return queryLogs(clubId, null, pageNum, pageSize);
    }

    private PageResult<PointsLogVO> queryLogs(Long clubId, Long userId, int pageNum, int pageSize) {
        LambdaQueryWrapper<PointsLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointsLog::getClubId, clubId)
                .eq(userId != null, PointsLog::getUserId, userId)
                .orderByDesc(PointsLog::getCreatedAt).orderByDesc(PointsLog::getId);

        pageNum = Math.max(1, pageNum);
        pageSize = Math.min(100, Math.max(1, pageSize));
        Page<PointsLog> page = new Page<>(pageNum, pageSize);
        pointsLogMapper.selectPage(page, wrapper);

        List<Long> userIds = page.getRecords().stream().map(PointsLog::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> users = userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        List<PointsLogVO> voList = page.getRecords().stream().map(log -> {
            PointsLogVO vo = new PointsLogVO();
            vo.setId(log.getId());
            vo.setUserId(log.getUserId());
            User user = users.get(log.getUserId());
            vo.setRealName(user == null ? "已删除成员" : user.getRealName());
            vo.setPoints(log.getPoints());
            vo.setReason(log.getReason());
            vo.setDescription(log.getDescription());
            vo.setRelatedActivityId(log.getRelatedActivityId());
            vo.setCreatedAt(log.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(pageNum, pageSize, page.getTotal(), voList);
    }

    @Override
    public List<PointsRankingVO> getRanking(Long clubId, int top) {
        selectClub(clubId);
        top = Math.min(100, Math.max(1, top));
        // 单条聚合 SQL 替代 N+1 查询
        List<Map<String, Object>> rows = pointsLogMapper.selectPointsRanking(clubId, top);
        if (rows.isEmpty()) return new ArrayList<>();

        // 批量查询用户信息
        List<Long> userIds = rows.stream()
                .map(r -> ((Number) r.get("user_id")).longValue())
                .collect(Collectors.toList());
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<PointsRankingVO> rankingList = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : rows) {
            Long uid = ((Number) row.get("user_id")).longValue();
            int totalPoints = ((Number) row.get("total_points")).intValue();
            User user = userMap.get(uid);
            if (user != null) {
                PointsRankingVO vo = new PointsRankingVO();
                vo.setUserId(uid);
                vo.setRealName(user.getRealName());
                vo.setTotalPoints(totalPoints);
                vo.setAvatar(user.getAvatar());
                vo.setRank(rank++);
                rankingList.add(vo);
            }
        }
        return rankingList;
    }

    @Override
    public PointsSummaryVO getSummary(Long clubId) {
        selectClub(clubId);
        Long userId = SecurityUtil.getCurrentUserId();

        // 总积分（单条聚合 SQL）
        int totalPoints = pointsLogMapper.selectTotalPoints(clubId, userId);

        // 排名（直接 DB 层计算，避免加载 top N 列表）
        int rank = pointsLogMapper.selectUserRank(clubId, userId);

        // 社团成员数
        LambdaQueryWrapper<ClubMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(ClubMember::getClubId, clubId).eq(ClubMember::getStatus, 1);
        int memberCount = Math.toIntExact(clubMemberMapper.selectCount(memberWrapper));

        PointsSummaryVO vo = new PointsSummaryVO();
        vo.setTotalPoints(totalPoints);
        vo.setRank(rank);
        vo.setMemberCount(memberCount);

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        Map<String, Object> overview = pointsLogMapper.selectClubOverview(clubId,
                monthStart.atStartOfDay(), monthStart.plusMonths(1).atStartOfDay());
        vo.setClubTotalPoints(((Number) overview.get("club_total_points")).intValue());
        vo.setMonthPoints(((Number) overview.get("month_points")).intValue());
        vo.setScoredMemberCount(((Number) overview.get("scored_member_count")).intValue());

        LocalDate weekStart = today.minusDays(6);
        Map<String, Integer> daily = pointsLogMapper.selectDailyPoints(clubId,
                weekStart.atStartOfDay(), today.plusDays(1).atStartOfDay()).stream()
                .collect(Collectors.toMap(PointsSummaryVO.DailyPoint::getDate, PointsSummaryVO.DailyPoint::getPoints));
        List<PointsSummaryVO.DailyPoint> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String date = weekStart.plusDays(i).toString();
            PointsSummaryVO.DailyPoint day = new PointsSummaryVO.DailyPoint();
            day.setDate(date);
            day.setPoints(daily.getOrDefault(date, 0));
            days.add(day);
        }
        vo.setDailyPoints(days);
        vo.setSourcePoints(pointsLogMapper.selectSourcePoints(clubId));
        return vo;
    }
}
