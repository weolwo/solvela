package net.lab1024.sa.stat.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.stat.dao.MarketingStatDao;
import net.lab1024.sa.stat.domain.vo.ParticipationByTypeVO;
import net.lab1024.sa.stat.domain.vo.ParticipationTrendVO;
import net.lab1024.sa.stat.domain.vo.ParticipationVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 营销统计 Service
 *
 * @Author weolwo
 * @Date 2026-08-02
 */
@Service
@RequiredArgsConstructor
public class MarketingStatService {

    /** 有参与行为的玩法类型。BASIC 没有参与行为，不出现在图上 */
    private static final List<String> GAMEPLAY_TYPES = List.of("DRAW", "TASK", "LOTTERY");

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 90;

    private final MarketingStatDao marketingStatDao;

    public ParticipationVO participation(Integer days) {
        int span = normalizeDays(days);
        // 从 span 天前的 0 点开始，含今天
        LocalDate fromDate = LocalDate.now().minusDays(span - 1L);
        LocalDateTime fromTime = fromDate.atStartOfDay();

        ParticipationVO vo = new ParticipationVO();
        vo.setDays(span);
        vo.setDateList(buildDateList(fromDate, span));
        vo.setTrendList(fillTrend(marketingStatDao.participationTrend(fromTime), vo.getDateList()));
        vo.setByTypeList(fillByType(marketingStatDao.participationByType(fromTime)));
        return vo;
    }

    private int normalizeDays(Integer days) {
        if (days == null || days <= 0) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }

    private List<String> buildDateList(LocalDate from, int span) {
        List<String> list = new ArrayList<>(span);
        for (int i = 0; i < span; i++) {
            list.add(from.plusDays(i).toString());
        }
        return list;
    }

    /**
     * 补齐缺失的「日期 × 类型」组合。
     * <p>没有参与的那天 SQL 不会返回行，前端拿到稀疏数据画出来的折线会断，必须补 0。
     */
    private List<ParticipationTrendVO> fillTrend(List<ParticipationTrendVO> rows, List<String> dateList) {
        Map<String, Integer> indexed = rows.stream()
                .collect(Collectors.toMap(r -> r.getStatDate() + "|" + r.getActivityType(),
                        ParticipationTrendVO::getMemberCount, (a, b) -> a));
        List<ParticipationTrendVO> result = new ArrayList<>(dateList.size() * GAMEPLAY_TYPES.size());
        for (String date : dateList) {
            for (String type : GAMEPLAY_TYPES) {
                ParticipationTrendVO item = new ParticipationTrendVO();
                item.setStatDate(date);
                item.setActivityType(type);
                item.setMemberCount(indexed.getOrDefault(date + "|" + type, 0));
                result.add(item);
            }
        }
        return result;
    }

    /** 三种玩法都要出现，没有数据的补 0，否则柱子会时有时无 */
    private List<ParticipationByTypeVO> fillByType(List<ParticipationByTypeVO> rows) {
        Map<String, Integer> indexed = rows.stream()
                .collect(Collectors.toMap(ParticipationByTypeVO::getActivityType,
                        ParticipationByTypeVO::getMemberCount, (a, b) -> a));
        return GAMEPLAY_TYPES.stream().map(type -> {
            ParticipationByTypeVO item = new ParticipationByTypeVO();
            item.setActivityType(type);
            item.setMemberCount(indexed.getOrDefault(type, 0));
            return item;
        }).collect(Collectors.toList());
    }
}
