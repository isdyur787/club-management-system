package com.club.management.points.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.club.management.club.entity.ClubMember;
import com.club.management.club.mapper.ClubMemberMapper;
import com.club.management.common.response.PageResult;
import com.club.management.common.util.SecurityUtil;
import com.club.management.points.entity.PointsLog;
import com.club.management.points.mapper.PointsLogMapper;
import com.club.management.points.service.PointsService;
import com.club.management.points.vo.PointsLogVO;
import com.club.management.points.vo.PointsRankingVO;
import com.club.management.points.vo.PointsSummaryVO;
import com.club.management.user.entity.User;
import com.club.management.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PointsServiceImpl implements PointsService {

    @Autowired
    private PointsLogMapper pointsLogMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ClubMemberMapper clubMemberMapper;

    @Override
    public PageResult<PointsLogVO> getMyLogs(Long clubId, int pageNum, int pageSize) {
        Long userId = SecurityUtil.getCurrentUserId();

        LambdaQueryWrapper<PointsLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointsLog::getClubId, clubId)
                .eq(PointsLog::getUserId, userId)
                .orderByDesc(PointsLog::getCreatedAt);

        Page<PointsLog> page = new Page<>(pageNum, pageSize);
        pointsLogMapper.selectPage(page, wrapper);

        List<PointsLogVO> voList = page.getRecords().stream().map(log -> {
            PointsLogVO vo = new PointsLogVO();
            vo.setId(log.getId());
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
        Long userId = SecurityUtil.getCurrentUserId();

        // 总积分（单条聚合 SQL）
        int totalPoints = pointsLogMapper.selectTotalPoints(clubId, userId);

        // 排名（直接 DB 层计算，避免加载 top N 列表）
        int rank = pointsLogMapper.selectUserRank(clubId, userId);

        // 社团成员数
        LambdaQueryWrapper<ClubMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(ClubMember::getClubId, clubId);
        int memberCount = Math.toIntExact(clubMemberMapper.selectCount(memberWrapper));

        PointsSummaryVO vo = new PointsSummaryVO();
        vo.setTotalPoints(totalPoints);
        vo.setRank(rank);
        vo.setMemberCount(memberCount);
        return vo;
    }
}
