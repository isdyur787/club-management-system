package com.club.management.signout.controller;

import com.club.management.common.annotation.OperationLog;
import com.club.management.common.annotation.RequirePermission;
import com.club.management.common.response.R;
import com.club.management.signout.dto.SignOutDTO;
import com.club.management.signout.service.SignOutService;
import com.club.management.signout.vo.SignOutVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/signout")
public class SignOutController {

    @Autowired
    private SignOutService signOutService;

    @PostMapping
    @RequirePermission("signout:do")
    @OperationLog(module = "签退", operation = "签退")
    public R<SignOutVO> signOut(@RequestBody @Valid SignOutDTO dto) {
        return R.ok(signOutService.signOut(dto));
    }
}
