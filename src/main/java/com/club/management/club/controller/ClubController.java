package com.club.management.club.controller;

import com.club.management.club.dto.*;
import com.club.management.club.service.ClubService;
import com.club.management.club.vo.ClubDetailVO;
import com.club.management.club.vo.ClubVO;
import com.club.management.club.vo.MemberVO;
import com.club.management.common.annotation.OperationLog;
import com.club.management.common.annotation.RequirePermission;
import com.club.management.common.response.PageResult;
import com.club.management.common.response.R;
import com.club.management.common.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clubs")
public class ClubController {

    @Autowired
    private ClubService clubService;

    @PostMapping
    @RequirePermission("club:create")
    @OperationLog(module = "社团管理", operation = "创建社团")
    public R<ClubVO> createClub(@Valid @RequestBody ClubCreateDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        ClubVO vo = clubService.createClub(userId, dto);
        return R.ok(vo);
    }

    @GetMapping
    public R<PageResult<ClubVO>> listClubs(ClubQueryDTO dto) {
        PageResult<ClubVO> result = clubService.listClubs(dto);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    public R<ClubDetailVO> getClubDetail(@PathVariable Long id) {
        ClubDetailVO vo = clubService.getClubDetail(id);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @RequirePermission("club:edit")
    @OperationLog(module = "社团管理", operation = "修改社团信息")
    public R<Void> updateClub(@PathVariable Long id, @Valid @RequestBody ClubUpdateDTO dto) {
        clubService.updateClub(id, dto);
        return R.ok();
    }

    @PutMapping("/{id}/approve")
    @RequirePermission("club:approve")
    @OperationLog(module = "社团管理", operation = "审批社团")
    public R<Void> approveClub(@PathVariable Long id, @Valid @RequestBody ClubApprovalDTO dto) {
        clubService.approveClub(id, dto);
        return R.ok();
    }

    @PostMapping("/{id}/members")
    @RequirePermission("club:join")
    @OperationLog(module = "社团管理", operation = "加入社团")
    public R<Void> joinClub(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        clubService.joinClub(id, userId);
        return R.ok();
    }

    @DeleteMapping("/{clubId}/members/me")
    @OperationLog(module = "社团管理", operation = "退出社团")
    public R<Void> leaveClub(@PathVariable Long clubId) {
        Long userId = SecurityUtil.getCurrentUserId();
        clubService.leaveClub(clubId, userId);
        return R.ok();
    }

    @GetMapping("/{id}/members")
    public R<PageResult<MemberVO>> listMembers(@PathVariable Long id, MemberQueryDTO dto) {
        PageResult<MemberVO> result = clubService.listMembers(id, dto);
        return R.ok(result);
    }

    @DeleteMapping("/{clubId}/members/{userId}")
    @RequirePermission("club:member:manage")
    @OperationLog(module = "社团管理", operation = "移除成员")
    public R<Void> removeMember(@PathVariable Long clubId, @PathVariable Long userId) {
        clubService.removeMember(clubId, userId);
        return R.ok();
    }
}
