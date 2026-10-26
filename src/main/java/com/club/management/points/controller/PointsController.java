package com.club.management.points.controller;

import com.club.management.common.annotation.RequirePermission;
import com.club.management.common.response.PageResult;
import com.club.management.common.response.R;
import com.club.management.points.service.PointsService;
import com.club.management.points.vo.PointsLogVO;
import com.club.management.points.vo.PointsRankingVO;
import com.club.management.points.vo.PointsSummaryVO;
import com.club.management.points.vo.PointsClubVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/points")
public class PointsController {

    @Autowired
    private PointsService pointsService;

    @GetMapping("/clubs")
    @RequirePermission("points:view")
    public R<List<PointsClubVO>> getAvailableClubs() {
        return R.ok(pointsService.getAvailableClubs());
    }

    @GetMapping("/activity")
    @RequirePermission("points:view")
    public R<PageResult<PointsLogVO>> getClubLogs(@RequestParam Long clubId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(pointsService.getClubLogs(clubId, pageNum, pageSize));
    }

    @GetMapping("/my")
    @RequirePermission("points:view")
    public R<PageResult<PointsLogVO>> getMyLogs(
            @RequestParam Long clubId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(pointsService.getMyLogs(clubId, pageNum, pageSize));
    }

    @GetMapping("/ranking")
    @RequirePermission("points:view")
    public R<List<PointsRankingVO>> getRanking(
            @RequestParam Long clubId,
            @RequestParam(defaultValue = "20") int top) {
        return R.ok(pointsService.getRanking(clubId, top));
    }

    @GetMapping("/summary")
    @RequirePermission("points:view")
    public R<PointsSummaryVO> getSummary(@RequestParam Long clubId) {
        return R.ok(pointsService.getSummary(clubId));
    }
}
