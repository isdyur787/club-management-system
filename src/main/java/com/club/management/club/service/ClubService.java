package com.club.management.club.service;

import com.club.management.club.dto.*;
import com.club.management.club.vo.ClubDetailVO;
import com.club.management.club.vo.ClubVO;
import com.club.management.club.vo.MemberVO;
import com.club.management.common.response.PageResult;

public interface ClubService {

    ClubVO createClub(Long userId, ClubCreateDTO dto);

    PageResult<ClubVO> listClubs(ClubQueryDTO dto);

    ClubDetailVO getClubDetail(Long id);

    void updateClub(Long id, ClubUpdateDTO dto);

    void approveClub(Long id, ClubApprovalDTO dto);

    void joinClub(Long clubId, Long userId);

    void leaveClub(Long clubId, Long userId);

    PageResult<MemberVO> listMembers(Long clubId, MemberQueryDTO dto);

    void removeMember(Long clubId, Long userId);
}
