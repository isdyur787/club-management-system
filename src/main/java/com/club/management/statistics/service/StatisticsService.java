package com.club.management.statistics.service;

import com.club.management.statistics.vo.ClubActivityStatsVO;
import com.club.management.statistics.vo.ClubScoreVO;
import com.club.management.statistics.vo.OverviewVO;

import java.util.List;

public interface StatisticsService {

    ClubActivityStatsVO getClubActivityStats(Long clubId, Integer year);

    List<ClubScoreVO> getClubScores(Integer year);

    OverviewVO getOverview();
}
