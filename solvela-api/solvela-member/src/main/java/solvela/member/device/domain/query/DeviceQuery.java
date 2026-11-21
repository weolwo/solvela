package solvela.member.device.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;
import solvela.enums.DeviceTypeEnum;

import java.time.LocalDate;

/**
 * 设备列表的查询条件。
 *
 * <h3>为什么筛选项是这几个</h3>
 * 它们对应的是运营真正会问的三个问题：
 * <ul>
 *   <li><b>这台设备是谁</b> —— 按 deviceId 精确查（从登录日志上复制过来）；</li>
 *   <li><b>这个 IP 领了多少台设备</b> —— 按 registerIp 查，这是识别批量领设备最直接的信号，
 *       {@code idx_dev_ip} 就是为它建的；</li>
 *   <li><b>现在有多少台在观察 / 被封</b> —— 按 status 查，走 {@code idx_dev_status}。</li>
 * </ul>
 *
 * <p>刻意<b>没有</b>按 model / osVersion 筛：那三列是客户端自报、不验的，
 * 拿它们当筛选条件会让人以为那是可信数据。
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DeviceQuery extends PageParam {

    /** 设备号：精确匹配。32 位 hex，运营是复制粘贴过来的，不需要模糊 */
    private String deviceId;

    /** 签发时 IP：精确匹配 */
    private String registerIp;

    private DeviceTypeEnum deviceType;

    /** 处置档：0-正常 1-观察 2-封禁 */
    private Integer status;

    private LocalDate createTimeBegin;

    private LocalDate createTimeEnd;
}
