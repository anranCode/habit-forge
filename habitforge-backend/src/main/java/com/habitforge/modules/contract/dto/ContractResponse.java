package com.habitforge.modules.contract.dto;

import com.habitforge.modules.contract.entity.Contract;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ContractResponse {

    private String id;
    private String habitId;
    /** 附带习惯名（前端直接渲染, 不必再查一次） */
    private String habitName;
    private String partnerName;
    private String penalty;
    private Boolean isPublic;
    private String status;
    private LocalDateTime signedAt;
    private LocalDateTime updatedAt;

    public static ContractResponse of(Contract ct, String habitName) {
        return ContractResponse.builder()
                .id(ct.getId())
                .habitId(ct.getHabitId())
                .habitName(habitName)
                .partnerName(ct.getPartnerName())
                .penalty(ct.getPenalty())
                .isPublic(ct.getIsPublic() != null && ct.getIsPublic() == 1)
                .status(ct.getStatus() == null ? Contract.STATUS_ACTIVE : ct.getStatus())
                .signedAt(ct.getSignedAt())
                .updatedAt(ct.getUpdatedAt())
                .build();
    }
}
