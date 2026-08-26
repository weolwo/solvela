package solvela.lottery;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

/**
 * 彩票号码池 实体类
 *
 * @Author weolwo
 * @Date 2026-04-19 11:31:09
 * @Copyright weolwo
 */

@Data
public class LotteryNumberPool {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 彩票编码
     */
    private String lotteryCode;

    /**
     * 彩票号码
     */
    private String ticketNumber;

    /**
     * 发号序列号
     */
    private Integer sequenceNo;

}
