package com.club.management.statistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.management.activity.entity.Activity;
import com.club.management.activity.mapper.ActivityMapper;
import com.club.management.club.entity.ClubInfo;
import com.club.management.club.mapper.ClubInfoMapper;
import com.club.management.common.config.ScoreWeightsProperties;
import com.club.management.common.enums.ActivityStatus;
import com.club.management.statistics.service.StatisticsService;
import com.club.management.statistics.vo.ClubActivityStatsVO;
import com.club.management.statistics.vo.ClubScoreVO;
import com.club.management.statistics.vo.OverviewVO;
import com.club.management.user.mapper.UserMapper;
import com.club.management.registration.entity.Registration;
import com.club.management.registration.mapper.RegistrationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
@Service
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private ActivityMapper activityMapper;

    @Autowired
    private ClubInfoMapper clubInfoMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RegistrationMapper registrationMapper;

    @Autowired
    private ScoreWeightsProperties scoreWeights;

    @Override
    public ClubActivityStatsVO getClubActivityStats(Long clubId, Integer year) {
        if (year == null) year = LocalDateTime.now().getYear();

        ClubInfo club = clubInfoMapper.selectById(clubId);
        if (club == null) return null;

        // 查询该社团该年的活动
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Activity::getClubId, clubId)
                .ge(Activity::getStartTime, LocalDateTime.of(year, 1, 1, 0, 0))
                .lt(Activity::getStartTime, LocalDateTime.of(year + 1, 1, 1, 0, 0));
        List<Activity> activities = activityMapper.selectList(wrapper);

        // 月度统计
        Map<Integer, ClubActivityStatsVO.MonthlyStat> monthlyMap = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            ClubActivityStatsVO.MonthlyStat stat = new ClubActivityStatsVO.MonthlyStat();
            stat.setMonth(m);
            stat.setActivityCount(0);
            stat.setParticipantCount(0);
            monthlyMap.put(m, stat);
        }

        int totalParticipants = 0;
        for (Activity activity : activities) {
            int month = activity.getStartTime().getMonthValue();
            ClubActivityStatsVO.MonthlyStat stat = monthlyMap.get(month);
            if (stat != null) {
                stat.setActivityCount(stat.getActivityCount() + 1);
                stat.setParticipantCount(stat.getParticipantCount() + activity.getCurrentParticipants());
            }
            totalParticipants += activity.getCurrentParticipants();
        }

        ClubActivityStatsVO vo = new ClubActivityStatsVO();
        vo.setClubId(clubId);
        vo.setClubName(club.getClubName());
        vo.setYear(year);
        vo.setTotalActivities(activities.size());
        vo.setMonthlyStats(new ArrayList<>(monthlyMap.values()));
        vo.setTotalParticipants(totalParticipants);
        vo.setAverageParticipants(activities.isEmpty() ? 0 : totalParticipants / activities.size());
        return vo;
    }

    @Override
    public List<ClubScoreVO> getClubScores(Integer year) {
        if (year == null) year = LocalDateTime.now().getYear();

        LocalDateTime startTime = LocalDateTime.of(year, 1, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(year + 1, 1, 1, 0, 0);

        // 从 DB 层面聚合统计，避免全量加载到内存
        List<Map<String, Object>> statsRows = activityMapper.selectClubScoreStats(startTime, endTime);
        Map<Long, Map<String, Object>> statsByClub = new HashMap<>();
        for (Map<String, Object> row : statsRows) {
            Long clubId = ((Number) row.get("club_id")).longValue();
            statsByClub.put(clubId, row);
        }

        // 仅查询社团 ID + 名称（轻量查询）
        List<ClubInfo> clubs = clubInfoMapper.selectList(
                new LambdaQueryWrapper<ClubInfo>().select(ClubInfo::getId, ClubInfo::getClubName));

        List<ClubScoreVO> scores = new ArrayList<>();
        for (ClubInfo club : clubs) {
            Map<String, Object> stats = statsByClub.get(club.getId());
            int activityCount = 0;
            double avgParticipants = 0;
            int typeCount = 0;

            if (stats != null) {
                activityCount = ((Number) stats.get("activity_count")).intValue();
                avgParticipants = ((Number) stats.get("avg_participants")).doubleValue();
                typeCount = ((Number) stats.get("type_count")).intValue();
            }

            // 活动频次分
            double activityScore = Math.min(activityCount / scoreWeights.getActivityMaxCount() * scoreWeights.getActivityWeight(), scoreWeights.getActivityWeight());
            // 参与率分
            double participationScore = Math.min(avgParticipants / scoreWeights.getMaxParticipants() * scoreWeights.getParticipationWeight(), scoreWeights.getParticipationWeight());
            // 活动多样性
            double diversityScore = Math.min(typeCount / scoreWeights.getMaxTypeCount() * scoreWeights.getDiversityWeight(), scoreWeights.getDiversityWeight());
            // 成员活跃度
            double memberActivityScore = scoreWeights.getMemberActivityScore();

            double totalScore = activityScore + participationScore + diversityScore + memberActivityScore;

            ClubScoreVO scoreVO = new ClubScoreVO();
            scoreVO.setClubId(club.getId());
            scoreVO.setClubName(club.getClubName());
            scoreVO.setActivityScore(Math.round(activityScore * 10.0) / 10.0);
            scoreVO.setParticipationScore(Math.round(participationScore * 10.0) / 10.0);
            scoreVO.setTotalScore(Math.round(totalScore * 10.0) / 10.0);
            scores.add(scoreVO);
        }

        // 排序并设置排名
        scores.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));
        for (int i = 0; i < scores.size(); i++) {
            scores.get(i).setRank(i + 1);
        }

        return scores;
    }

    @Override
    public OverviewVO getOverview() {
        OverviewVO vo = new OverviewVO();
        vo.setTotalUsers(Math.toIntExact(userMapper.selectCount(new LambdaQueryWrapper<>())));
        vo.setTotalClubs(Math.toIntExact(clubInfoMapper.selectCount(new LambdaQueryWrapper<>())));

        // 聚合 SQL 替代全量加载
        Map<String, Object> overview = activityMapper.selectActivityOverview();
        vo.setTotalActivities(((Number) overview.get("total_count")).intValue());
        vo.setTotalParticipants(((Number) overview.get("total_participants")).intValue());

        // 最近5个活动
        LambdaQueryWrapper<Activity> recentWrapper = new LambdaQueryWrapper<>();
        recentWrapper.orderByDesc(Activity::getCreatedAt).last("LIMIT 5");
        List<Activity> recentActivities = activityMapper.selectList(recentWrapper);

        // 批量查询社团名称，避免 N+1
        Set<Long> clubIds = recentActivities.stream()
                .map(Activity::getClubId)
                .collect(Collectors.toSet());
        Map<Long, String> clubNameMap = new HashMap<>();
        if (!clubIds.isEmpty()) {
            List<ClubInfo> clubs = clubInfoMapper.selectBatchIds(clubIds);
            clubs.forEach(c -> clubNameMap.put(c.getId(), c.getClubName()));
        }

        List<OverviewVO.RecentActivity> recentList = recentActivities.stream().map(act -> {
            OverviewVO.RecentActivity recent = new OverviewVO.RecentActivity();
            recent.setId(act.getId());
            recent.setTitle(act.getTitle());
            recent.setStatus(act.getStatus());
            recent.setStartTime(act.getStartTime());
            recent.setClubName(clubNameMap.get(act.getClubId()));
            return recent;
        }).collect(Collectors.toList());
        vo.setRecentActivities(recentList);

        return vo;
    }
}
