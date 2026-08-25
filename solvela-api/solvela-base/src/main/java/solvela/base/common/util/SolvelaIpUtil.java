package solvela.base.common.util;

import lombok.extern.slf4j.Slf4j;
import solvela.base.common.constant.StringConst;
import org.lionsoul.ip2region.xdb.Header;
import org.lionsoul.ip2region.xdb.LongByteArray;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * IP工具类
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2023/9/14 15:35:11
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>，Since 2012
 */
@Slf4j
public class SolvelaIpUtil {

    private static Searcher IP_SEARCHER;

    /**
     * xdb 数据文件的格式版本（取自文件头，非 jar 的版本号）。
     * <p>
     * ⚠️ ip2region 的**数据文件字段布局在 v3 变过，而且是「段数不变、位置错位」的隐蔽变化**：
     * <pre>
     *   v2 数据： 国家 | 区域 | 省份 | 城市 | ISP                 例：中国|0|江苏省|南京市|0
     *   v3 数据： 国家 | 省份 | 城市 | ISP  | iso-alpha2-code     例：中国|江苏省|南京市|电信|CN
     * </pre>
     * 两者都是 5 段，换数据文件既不会报错也不会长度异常，只会**静默把「省份」读成「区域」**。
     * 所以这里按文件头版本号决定字段下标，而不是按位置硬编码。
     */
    private static int XDB_VERSION = 2;

    private static final int XDB_VERSION_V2 = 2;

    /** 数据里表示「无此项」的占位符，需要剔除，否则会落库成 `中国|0|江苏省|南京市|0` 这种脏值 */
    private static final String UNKNOWN_PLACEHOLDER = "0";

    /**
     * 初始化数据
     *
     * @param filePath
     */
    public static void init(String filePath) {

        try {
            // ip2region 3.x 为了支持 IPv6 改了加载 API：
            // loadContentFromFile 的返回值从 byte[] 变成 LongByteArray（大文件不再受 int 长度限制），
            // newWithBuffer 也必须显式传入 IP 版本。本项目的 ip2region.xdb 是 IPv4 库。
            Header header = Searcher.loadHeaderFromFile(filePath);
            XDB_VERSION = header.version;

            LongByteArray cBuff = Searcher.loadContentFromFile(filePath);
            IP_SEARCHER = Searcher.newWithBuffer(Version.IPv4, cBuff);
            log.info("ip2region.xdb 加载完成，数据格式版本:[{}]", XDB_VERSION);

        } catch (Throwable e) {
            log.error("初始化ip2region.xdb文件失败,报错信息:[{}]", e.getMessage(), e);
            throw new RuntimeException("系统异常!");
        }
    }


    /**
     * 解析 ip 地址，返回归一化后的地区分段
     * <p>
     * 无论 xdb 数据文件是 v2 还是 v3 格式，都统一返回 [国家, 省份, 城市, 运营商]，
     * 并已剔除占位符，调用方可以放心按下标取。
     *
     * @param ipStr ipStr
     * @return 返回结果例 [中国, 河南省, 洛阳市, 电信]
     */
    public static List<String> getRegionList(String ipStr) {
        List<String> regionList = new ArrayList<>();
        try {
            if (SolvelaStringUtil.isEmpty(ipStr) || IP_SEARCHER == null) {
                return regionList;
            }
            String region = IP_SEARCHER.search(ipStr.trim());
            if (SolvelaStringUtil.isEmpty(region)) {
                return regionList;
            }

            String[] split = region.split("\\|", -1);
            // 按数据文件的格式版本取字段，不按位置硬编码（两个版本段数相同但含义错位，见 XDB_VERSION 注释）
            if (XDB_VERSION <= XDB_VERSION_V2) {
                // 国家 | 区域 | 省份 | 城市 | ISP —— 「区域」字段在 v2 数据里恒为占位符，直接丢弃
                addIfPresent(regionList, split, 0);
                addIfPresent(regionList, split, 2);
                addIfPresent(regionList, split, 3);
                addIfPresent(regionList, split, 4);
            } else {
                // 国家 | 省份 | 城市 | ISP | iso-alpha2-code —— 国家代码与「国家」重复，不进展示串
                addIfPresent(regionList, split, 0);
                addIfPresent(regionList, split, 1);
                addIfPresent(regionList, split, 2);
                addIfPresent(regionList, split, 3);
            }
        } catch (Exception e) {
            log.error("解析ip地址出错", e);
        }
        return regionList;
    }

    /**
     * 解析 ip 地址，返回归一化后的地区字符串（落库用）
     * <p>
     * 归一化的意义：换 xdb 数据文件时，落库内容的**含义和形状保持不变**，
     * 不会因为数据格式升级而让历史数据与新数据对不上。
     *
     * @param ipStr ipStr
     * @return 返回结果例 中国|河南省|洛阳市|电信
     */
    public static String getRegion(String ipStr) {
        List<String> regionList = getRegionList(ipStr);
        return regionList.isEmpty() ? StringConst.EMPTY : String.join(StringConst.VERTICAL_BAR, regionList);
    }

    /**
     * 取下标字段，跳过越界、空串与占位符
     */
    private static void addIfPresent(List<String> target, String[] split, int index) {
        if (index >= split.length) {
            return;
        }
        String value = split[index].trim();
        if (SolvelaStringUtil.isEmpty(value) || UNKNOWN_PLACEHOLDER.equals(value)) {
            return;
        }
        target.add(value);
    }

    /**
     * 获取本机第一个ip
     *
     * @return
     */
    public static String getLocalFirstIp() {
        List<String> list = getLocalIp();
        return list.size() > 0 ? list.get(0) : null;
    }

    /**
     * 获取本机ip
     *
     * @return
     */
    public static List<String> getLocalIp() {
        List<String> ipList = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    // 排除回环地址和IPv6地址
                    if (!inetAddress.isLoopbackAddress() && !inetAddress.getHostAddress().contains(StringConst.COLON)) {
                        ipList.add(inetAddress.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return ipList;
    }
}
