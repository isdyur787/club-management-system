package com.club.management.signout.service;

import com.club.management.signout.dto.SignOutDTO;
import com.club.management.signout.vo.SignOutVO;

public interface SignOutService {

    SignOutVO signOut(SignOutDTO dto);
}
