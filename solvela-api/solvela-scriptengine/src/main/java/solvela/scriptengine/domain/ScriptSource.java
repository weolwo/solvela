package solvela.scriptengine.domain;

/**
 * 脚本内容的来源，决定了引擎能对它做哪些优化与放宽。
 *
 * <p><b>为什么建模「来源」而不是直接建模「是否缓存」：</b>
 * 缓存是某个具体引擎的实现细节（QLExpress 有编译缓存，换成 Aviator 未必有同样的东西），
 * 把它写进脚本模型就是抽象泄漏 —— 上层业务代码不该知道底下有没有缓存这回事。
 * 而「这段脚本是不是我们自己 git 管理的」是一个<b>业务事实</b>，任何引擎都成立，
 * 各引擎自己决定要据此做什么优化。
 *
 * <p>没有提供默认值：`trusted` 与 `untrusted` 的差别有安全与内存后果，
 * 必须在每个调用点显式写出来。留一个短名默认工厂，正是不受控内容混进受控通道的典型路径。
 */
public enum ScriptSource {

    /**
     * 受控来源：项目内 git 管理的脚本文件，经过 code review 与发版流程。
     *
     * <p>关键性质是<b>集合有界</b> —— 全项目脚本数量是可数的，且内容稳定。
     */
    TRUSTED("受控脚本（项目内 git 管理）"),

    /**
     * 不受控来源：后台在线试跑、前端动态拼装、任何由请求内容直接决定的脚本。
     *
     * <p>关键性质是<b>集合无界</b> —— 内容随每次请求变化，不存在「跑第二次还是同一段」这回事。
     * 引擎对这类脚本必须放弃一切以脚本原文为 key 的缓存，否则就是稳定的内存泄漏。
     */
    UNTRUSTED("不受控脚本（在线试跑 / 动态拼装）"),
    ;

    private final String description;

    ScriptSource(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
