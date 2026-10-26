package com.club.management.resource.service;

import com.club.management.resource.dto.BorrowDTO;

public interface EquipmentBorrowService {
    void borrowEquipment(Long userId, BorrowDTO dto);
    void returnEquipment(Long borrowId);
}
