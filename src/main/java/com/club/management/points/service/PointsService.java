package com.club.management.points.service;

import com.club.management.common.response.PageResult;
import com.club.management.points.vo.PointsLogVO;
import com.club.management.points.vo.PointsRankingVO;
import com.club.management.points.vo.PointsSummaryVO;
import com.club.management.points.vo.PointsClubVO;

import java.util.List;

public interface PointsService {

    List<PointsClubVO> getAvailableClubs();

    PageResult<PointsLogVO> getClubLogs(Long clubId, int pageNum, int pageSize);

    PageResult<PointsLogVO> getMyLogs(Long clubId, int pageNum, int pageSize);

    List<PointsRankingVO> getRanking(Long clubId, int top);

    PointsSummaryVO getSummary(Long clubId);
}
