package solvela.dispatch;

/**
 * 资产/奖品下发的结果。
 *
 * <h3>它取代的是什么</h3>
 * 这条链路（{@code AssetDispatchEngine} → {@code IAssetHandler}、
 * {@code PrizeDispatchHandler} → {@code IPrizeHandler}）以前用 {@code ResponseDTO} 传结果。
 * 那是<b>HTTP 响应体</b>：带 code / level / dataType，会被 {@code EncryptResponseAdvice} 加密，
 * 会被 {@code OperateLogAspect} 当成接口返回值记进操作日志。而这里根本没有 HTTP ——
 * 调用方是 MQ 消费者和定时任务，没有人会把它写进 response。
 *
 * <p>用它当内部结果，代价是共享层必须认识 HTTP 契约（solvela-base 依赖 web 概念），
 * 以及「这个 ResponseDTO 到底会不会被下发到前端」永远要读全链路才能回答。
 *
 * <h3>为什么不是抛异常</h3>
 * 下发失败是<b>预期内的终态</b>，不是异常：引擎要拿失败原因写进
 * {@code t_proposal_record.remark} 和 {@code t_prize_log.fail_reason}，然后继续跑下一条。
 * 用异常表达它，等于要求每个调用点都写 try/catch 才能把原因取出来。
 *
 * @param ok         是否下发成功
 * @param failReason 失败原因，成功时为 null；会落库给运营看，别写堆栈
 * @param proposalId 会员服务返回的提案 id，落进 {@code t_prize_log.proposal_id} 供人工排查。
 *                   走不到提案那一步（如 0 值奖品）或幂等重复请求时为 null
 */
public record DispatchOutcome(boolean ok, String failReason, Long proposalId) {

    private static final DispatchOutcome SUCCESS = new DispatchOutcome(true, null, null);

    /** 工厂名是 success 而不是 ok：{@code ok} 已经是这个 record 的访问器名，重名过不了编译 */
    public static DispatchOutcome success() {
        return SUCCESS;
    }

    public static DispatchOutcome success(Long proposalId) {
        return new DispatchOutcome(true, null, proposalId);
    }

    public static DispatchOutcome failed(String failReason) {
        return new DispatchOutcome(false, failReason, null);
    }
}
