package com.club.management.statistics.controller;

import com.club.management.common.annotation.RequirePermission;
import com.club.management.common.response.R;
import com.club.management.statistics.service.StatisticsService;
import com.club.management.statistics.vo.ClubActivityStatsVO;
import com.club.management.statistics.vo.ClubScoreVO;
import com.club.management.statistics.vo.OverviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    @GetMapping("/clubs/{clubId}/activities")
    @RequirePermission("statistics:view")
    public R<ClubActivityStatsVO> getClubActivityStats(
            @PathVariable Long clubId,
            @RequestParam(required = false) Integer year) {
        ClubActivityStatsVO vo = statisticsService.getClubActivityStats(clubId, year);
        return R.ok(vo);
    }

    @GetMapping("/clubs/score")
    @RequirePermission("statistics:view")
    public R<List<ClubScoreVO>> getClubScores(@RequestParam(required = false) Integer year) {
        List<ClubScoreVO> scores = statisticsService.getClubScores(year);
        return R.ok(scores);
    }

    @GetMapping("/overview")
    public R<OverviewVO> getOverview() {
        OverviewVO vo = statisticsService.getOverview();
        return R.ok(vo);
    }
}
