package sa.base.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 替换 commons-text StringSubstitutor 的口径固化测试。
 *
 * <p>下面每一条期望值都不是拍脑袋写的：摘掉 commons-text 之前，用真的 {@code StringSubstitutor}
 * 跑过同一组语料逐条比对（见 {@code 与StringSubstitutor逐条一致} 那条临时用例），全部一致才固化下来。
 *
 * @Date 2026-08-08
 */
public class SmartTemplateUtilTest {

    /**
     * 语料：每一条都是 StringSubstitutor 会遇到的形态。
     */
    static final List<String> CORPUS = List.of(
            "您有一个订单等待审批，订单号【${orderNumber}】",
            "${a}-${b}",
            "没有占位符",
            "${unknown} 保留原样",
            "${nullValue} 也保留原样",
            "$${orderNumber} 是转义",
            "价格 $100，不是占位符",
            "$$x 后面不跟大括号",
            "${没闭合",
            "${} 空 key",
            "前缀${a}后缀",
            "",
            "${a}${a}${a}");

    static Map<String, Object> params() {
        Map<String, Object> params = new HashMap<>();
        params.put("orderNumber", "SO20260808");
        params.put("a", "甲");
        params.put("b", "乙");
        params.put("nullValue", null);
        return params;
    }

    @Test
    public void 基础替换() {
        assertEquals("您有一个订单等待审批，订单号【SO20260808】",
                SmartTemplateUtil.render("您有一个订单等待审批，订单号【${orderNumber}】", params()));
        assertEquals("甲-乙", SmartTemplateUtil.render("${a}-${b}", params()));
        assertEquals("甲甲甲", SmartTemplateUtil.render("${a}${a}${a}", params()));
    }

    @Test
    public void 解析不到的占位符原样保留() {
        // 模板是外部传进来的字符串，里面出现非参数的 ${...} 很正常，不能吞成空串
        assertEquals("${unknown} 保留原样", SmartTemplateUtil.render("${unknown} 保留原样", params()));
        assertEquals("${nullValue} 也保留原样", SmartTemplateUtil.render("${nullValue} 也保留原样", params()));
    }

    @Test
    public void 转义与非占位符的美元符号() {
        assertEquals("${orderNumber} 是转义", SmartTemplateUtil.render("$${orderNumber} 是转义", params()));
        assertEquals("价格 $100，不是占位符", SmartTemplateUtil.render("价格 $100，不是占位符", params()));
        assertEquals("$$x 后面不跟大括号", SmartTemplateUtil.render("$$x 后面不跟大括号", params()));
    }

    @Test
    public void 畸形输入不炸() {
        assertEquals("${没闭合", SmartTemplateUtil.render("${没闭合", params()));
        assertEquals("${} 空 key", SmartTemplateUtil.render("${} 空 key", params()));
        assertEquals("", SmartTemplateUtil.render("", params()));
        assertNull(SmartTemplateUtil.render(null, params()));
        assertEquals("${a}", SmartTemplateUtil.render("${a}", null), "params 为 null 时原样保留");
        assertEquals("${a}", SmartTemplateUtil.render("${a}", Map.of()));
    }

    @Test
    public void 刻意不对替换后的值做递归解析() {
        // 🔴 这是相对 StringSubstitutor 唯一有意的口径收紧。
        // StringSubstitutor 默认会把替换进去的值再扫一遍，值里带 ${...} 会被继续替换；
        // 而模板参数常常来自用户数据，递归解析等于给了一条模板注入的路。
        Map<String, Object> params = new HashMap<>();
        params.put("outer", "${inner}");
        params.put("inner", "被注入的内容");

        assertEquals("${inner}", SmartTemplateUtil.render("${outer}", params),
                "值里的 ${inner} 必须原样输出，不能再被解析一次");
    }
}
