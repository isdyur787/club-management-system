package com.club.management.club.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.club.management.club.dto.*;
import com.club.management.club.entity.ClubInfo;
import com.club.management.club.entity.ClubMember;
import com.club.management.club.mapper.ClubInfoMapper;
import com.club.management.club.mapper.ClubMemberMapper;
import com.club.management.club.service.ClubService;
import com.club.management.club.vo.ClubDetailVO;
import com.club.management.club.vo.ClubVO;
import com.club.management.club.vo.MemberVO;
import com.club.management.common.enums.ActiveStatus;
import com.club.management.common.enums.ClubApprovalStatus;
import com.club.management.common.enums.ClubMemberRole;
import com.club.management.common.event.MemberJoinedEvent;
import com.club.management.common.event.MemberLeftEvent;
import com.club.management.common.exception.*;
import com.club.management.common.response.PageResult;
import com.club.management.common.util.SecurityUtil;
import com.club.management.user.entity.User;
import com.club.management.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClubServiceImpl implements ClubService {

    private final ClubInfoMapper clubInfoMapper;
    private final ClubMemberMapper clubMemberMapper;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClubVO createClub(Long userId, ClubCreateDTO dto) {
        // 检查社团名称唯一性
        LambdaQueryWrapper<ClubInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClubInfo::getClubName, dto.getClubName());
        if (clubInfoMapper.selectCount(wrapper) > 0) {
            throw new ConflictException(ErrorCode.CLUB_ALREADY_EXISTS);
        }

        // 创建社团
        ClubInfo club = new ClubInfo();
        club.setClubName(dto.getClubName());
        club.setCategory(dto.getCategory());
        club.setDescription(dto.getDescription());
        club.setCharter(dto.getCharter());
        club.setPresidentId(userId);
        club.setApprovalStatus(ClubApprovalStatus.PENDING.getCode());
        club.setMemberCount(1);
        clubInfoMapper.insert(club);

        // 创建社长成员记录
        ClubMember member = new ClubMember();
        member.setClubId(club.getId());
        member.setUserId(userId);
        member.setRole(ClubMemberRole.PRESIDENT.name());
        member.setJoinedAt(LocalDateTime.now());
        member.setStatus(ActiveStatus.ACTIVE.getCode());
        clubMemberMapper.insert(member);

        // 返回 VO
        ClubVO vo = new ClubVO();
        vo.setId(club.getId());
        vo.setClubName(club.getClubName());
        vo.setCategory(club.getCategory());
        vo.setDescription(club.getDescription());
        vo.setApprovalStatus(club.getApprovalStatus());
        vo.setMemberCount(1);
        vo.setCreatedAt(club.getCreatedAt());
        return vo;
    }

    @Override
    public PageResult<ClubVO> listClubs(ClubQueryDTO dto) {
        LambdaQueryWrapper<ClubInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dto.getClubName())) {
            wrapper.like(ClubInfo::getClubName, dto.getClubName());
        }
        if (StringUtils.hasText(dto.getCategory())) {
            wrapper.eq(ClubInfo::getCategory, dto.getCategory());
        }
        if (dto.getApprovalStatus() != null) {
            wrapper.eq(ClubInfo::getApprovalStatus, dto.getApprovalStatus());
        }
        wrapper.orderByDesc(ClubInfo::getCreatedAt);

        Page<ClubInfo> page = clubInfoMapper.selectPage(
                new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);

        List<ClubInfo> clubs = page.getRecords();
        // 批量查询社长信息，避免 N+1
        Set<Long> presidentIds = clubs.stream()
                .map(ClubInfo::getPresidentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> presidentNameMap = new HashMap<>();
        if (!presidentIds.isEmpty()) {
            List<User> presidents = userMapper.selectBatchIds(presidentIds);
            presidents.forEach(u -> presidentNameMap.put(u.getId(), u.getRealName()));
        }

        List<ClubVO> voList = clubs.stream().map(club -> {
            ClubVO vo = new ClubVO();
            vo.setId(club.getId());
            vo.setClubName(club.getClubName());
            vo.setCategory(club.getCategory());
            vo.setDescription(club.getDescription());
            vo.setMemberCount(club.getMemberCount());
            vo.setApprovalStatus(club.getApprovalStatus());
            vo.setCreatedAt(club.getCreatedAt());
            vo.setPresidentName(presidentNameMap.get(club.getPresidentId()));
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(dto.getPageNum(), dto.getPageSize(), page.getTotal(), voList);
    }

    @Override
    public ClubDetailVO getClubDetail(Long id) {
        ClubInfo club = clubInfoMapper.selectById(id);
        if (club == null) {
            throw new NotFoundException(ErrorCode.CLUB_NOT_FOUND);
        }

        ClubDetailVO vo = new ClubDetailVO();
        vo.setId(club.getId());
        vo.setClubName(club.getClubName());
        vo.setCategory(club.getCategory());
        vo.setDescription(club.getDescription());
        vo.setCharter(club.getCharter());
        vo.setCoverImage(club.getCoverImage());
        vo.setMemberCount(club.getMemberCount());
        vo.setApprovalStatus(club.getApprovalStatus());
        vo.setCreatedAt(club.getCreatedAt());

        // 查询社长信息
        User president = userMapper.selectById(club.getPresidentId());
        if (president != null) {
            ClubDetailVO.PresidentVO presidentVO = new ClubDetailVO.PresidentVO();
            presidentVO.setId(president.getId());
            presidentVO.setRealName(president.getRealName());
            vo.setPresident(presidentVO);
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateClub(Long id, ClubUpdateDTO dto) {
        ClubInfo club = clubInfoMapper.selectById(id);
        if (club == null) {
            throw new NotFoundException(ErrorCode.CLUB_NOT_FOUND);
        }

        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!club.getPresidentId().equals(currentUserId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }

        if (dto.getDescription() != null) club.setDescription(dto.getDescription());
        if (dto.getCoverImage() != null) club.setCoverImage(dto.getCoverImage());
        if (dto.getCharter() != null) club.setCharter(dto.getCharter());

        clubInfoMapper.updateById(club);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveClub(Long id, ClubApprovalDTO dto) {
        ClubInfo club = clubInfoMapper.selectById(id);
        if (club == null) {
            throw new NotFoundException(ErrorCode.CLUB_NOT_FOUND);
        }

        if (club.getApprovalStatus() != ClubApprovalStatus.PENDING.getCode()) {
            throw new BusinessException(ErrorCode.CLUB_NOT_APPROVED);
        }

        club.setApprovalStatus(dto.getApproved() ? ClubApprovalStatus.APPROVED.getCode() : ClubApprovalStatus.REJECTED.getCode());
        club.setRejectReason(dto.getRejectReason());
        club.setApprovedBy(SecurityUtil.getCurrentUserId());
        club.setApprovedAt(LocalDateTime.now());
        clubInfoMapper.updateById(club);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void joinClub(Long clubId, Long userId) {
        ClubInfo club = clubInfoMapper.selectById(clubId);
        if (club == null) {
            throw new NotFoundException(ErrorCode.CLUB_NOT_FOUND);
        }

        if (club.getApprovalStatus() != ClubApprovalStatus.APPROVED.getCode()) {
            throw new BusinessException(ErrorCode.CLUB_NOT_APPROVED);
        }

        // 检查是否已是成员
        LambdaQueryWrapper<ClubMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClubMember::getClubId, clubId)
                .eq(ClubMember::getUserId, userId)
                .eq(ClubMember::getStatus, ActiveStatus.ACTIVE.getCode());
        if (clubMemberMapper.selectCount(wrapper) > 0) {
            throw new ConflictException(ErrorCode.MEMBER_ALREADY_EXISTS);
        }

        // 创建成员记录
        ClubMember member = new ClubMember();
        member.setClubId(clubId);
        member.setUserId(userId);
        member.setRole(ClubMemberRole.MEMBER.name());
        member.setJoinedAt(LocalDateTime.now());
        member.setStatus(ActiveStatus.ACTIVE.getCode());
        clubMemberMapper.insert(member);

        // 原子更新成员数量（避免并发先读后写丢失）
        clubInfoMapper.update(null, new LambdaUpdateWrapper<ClubInfo>()
                .eq(ClubInfo::getId, clubId)
                .setSql("member_count = member_count + 1"));

        // 发布事件
        eventPublisher.publishEvent(new MemberJoinedEvent(this, userId, clubId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leaveClub(Long clubId, Long userId) {
        LambdaQueryWrapper<ClubMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClubMember::getClubId, clubId)
                .eq(ClubMember::getUserId, userId)
                .eq(ClubMember::getStatus, ActiveStatus.ACTIVE.getCode());
        ClubMember member = clubMemberMapper.selectOne(wrapper);

        if (member == null) {
            throw new NotFoundException(ErrorCode.MEMBER_NOT_FOUND);
        }

        if (ClubMemberRole.PRESIDENT.name().equals(member.getRole())) {
            throw new BusinessException(ErrorCode.CLUB_PRESIDENT_CANNOT_LEAVE);
        }

        // 逻辑删除成员
        member.setStatus(ActiveStatus.INACTIVE.getCode());
        clubMemberMapper.updateById(member);

        // 原子更新成员数量
        clubInfoMapper.update(null, new LambdaUpdateWrapper<ClubInfo>()
                .eq(ClubInfo::getId, clubId)
                .setSql("member_count = GREATEST(0, member_count - 1)"));

        // 发布事件
        eventPublisher.publishEvent(new MemberLeftEvent(this, userId, clubId));
    }

    @Override
    public PageResult<MemberVO> listMembers(Long clubId, MemberQueryDTO dto) {
        LambdaQueryWrapper<ClubMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClubMember::getClubId, clubId)
                .eq(ClubMember::getStatus, ActiveStatus.ACTIVE.getCode());
        if (StringUtils.hasText(dto.getRole())) {
            wrapper.eq(ClubMember::getRole, dto.getRole());
        }
        wrapper.orderByAsc(ClubMember::getJoinedAt);

        Page<ClubMember> page = clubMemberMapper.selectPage(
                new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);

        List<ClubMember> members = page.getRecords();
        // 批量查询成员姓名，避免 N+1
        Set<Long> userIds = members.stream()
                .map(ClubMember::getUserId)
                .collect(Collectors.toSet());
        Map<Long, String> userNameMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            users.forEach(u -> userNameMap.put(u.getId(), u.getRealName()));
        }

        List<MemberVO> voList = members.stream().map(member -> {
            MemberVO vo = new MemberVO();
            vo.setUserId(member.getUserId());
            vo.setRole(member.getRole());
            vo.setJoinedAt(member.getJoinedAt());
            vo.setRealName(userNameMap.get(member.getUserId()));
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(dto.getPageNum(), dto.getPageSize(), page.getTotal(), voList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long clubId, Long userId) {
        LambdaQueryWrapper<ClubMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClubMember::getClubId, clubId)
                .eq(ClubMember::getUserId, userId)
                .eq(ClubMember::getStatus, ActiveStatus.ACTIVE.getCode());
        ClubMember member = clubMemberMapper.selectOne(wrapper);

        if (member == null) {
            throw new NotFoundException(ErrorCode.MEMBER_NOT_FOUND);
        }

        member.setStatus(ActiveStatus.INACTIVE.getCode());
        clubMemberMapper.updateById(member);

        // 原子更新成员数量
        clubInfoMapper.update(null, new LambdaUpdateWrapper<ClubInfo>()
                .eq(ClubInfo::getId, clubId)
                .setSql("member_count = GREATEST(0, member_count - 1)"));

        // 发布事件（与 leaveClub 保持一致）
        eventPublisher.publishEvent(new MemberLeftEvent(this, userId, clubId));
    }
}
