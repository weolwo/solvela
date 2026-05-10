package net.lab1024.sa.lottery.prizerule.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PrizeDetailItem {
    private Integer prizeLevel;
    private String prizeName;
    private Integer matchCount;
    private String prizeCode;
    private BigDecimal prizeValue;

    /**
     * 匹配模式，0,前匹配，1后匹配
     */
    private Integer patternMode;
    /**
     * 开奖个数
     */
    private Integer winCount;

    // ==========================================
    // 【新增】：截流开奖时间范围 (非必填)
    // ==========================================
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}