package com.club.management.activity.dto;

import lombok.Data;

@Data
public class ActivityUpdateDTO {

    private String title;

    private String description;

    private String location;

    private Integer maxParticipants;

    private String coverImage;
}
