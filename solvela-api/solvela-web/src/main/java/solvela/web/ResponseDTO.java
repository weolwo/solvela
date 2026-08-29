package solvela.web;


import lombok.Data;
import solvela.code.ErrorCode;
import solvela.code.UserErrorCode;
import org.apache.commons.lang3.StringUtils;

/**
 * 管理端的 HTTP 响应体：永远 200，业务成败看 {@code code} / {@code ok}。
 *
 * <h3>为什么住在 solvela-web，而不是共享层</h3>
 * 它是<b>一种 HTTP 契约</b>：带 code / level / dataType，会被 {@code EncryptResponseAdvice}
 * 加密、被 {@code OperateLogAspect} 记进操作日志。放在 solvela-base 的后果不是「多了个 import」——
 * 是七个业务模块的 service 全都拿它当返回值，于是
 * <ul>
 *   <li>每个调用方都要判一次 {@code getOk()}，漏判就是「明明失败了却按成功往下走」
 *       （发奖链路真的这么把没入账的记录标成过「发货成功」）；</li>
 *   <li>在 {@code @Transactional} 方法里用 return 表达失败<b>不会回滚</b>，
 *       半截写入照常提交；</li>
 *   <li>MQ 消费者、定时任务这些根本没有 HTTP 的地方，也在生产一个「响应体」。</li>
 * </ul>
 * 现在业务层一律「返回裸值 / void，失败抛 {@code BusinessException}」，
 * 翻译成本结构只发生在 Controller 与 {@code GlobalExceptionHandler} 两处。
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2021-10-31 21:06:11
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Data
public class ResponseDTO<T> {

    public static final int OK_CODE = 0;

    public static final String OK_MSG = "操作成功";

    /** 返回码 */
    private Integer code;

    /** 级别 */
    private String level;

    private String msg;

    private Boolean ok;

    /** 返回数据 */
    private T data;

    /** 数据类型，取值见 {@link DataTypeEnum} */
    private Integer dataType;

    public ResponseDTO(Integer code, String level, boolean ok, String msg, T data) {
        this.code = code;
        this.level = level;
        this.ok = ok;
        this.msg = msg;
        this.data = data;
        this.dataType = DataTypeEnum.NORMAL.getValue();
    }

    public ResponseDTO(Integer code, String level, boolean ok, String msg) {
        this.code = code;
        this.level = level;
        this.ok = ok;
        this.msg = msg;
        this.dataType = DataTypeEnum.NORMAL.getValue();
    }

    public ResponseDTO(ErrorCode errorCode, boolean ok, String msg, T data) {
        this.code = errorCode.getCode();
        this.level = errorCode.getLevel();
        this.ok = ok;
        if (StringUtils.isNotBlank(msg)) {
            this.msg = msg;
        } else {
            this.msg = errorCode.getMsg();
        }
        this.data = data;
        this.dataType = DataTypeEnum.NORMAL.getValue();
    }

    public static <T> ResponseDTO<T> ok() {
        return new ResponseDTO<>(OK_CODE, null, true, OK_MSG, null);
    }

    public static <T> ResponseDTO<T> ok(T data) {
        return new ResponseDTO<>(OK_CODE, null, true, OK_MSG, data);
    }

    public static <T> ResponseDTO<T> okMsg(String msg) {
        return new ResponseDTO<>(OK_CODE, null, true, msg, null);
    }

    // -------------------------------------------- 最常用的 用户参数 错误码 --------------------------------------------

    public static <T> ResponseDTO<T> userErrorParam() {
        return new ResponseDTO<>(UserErrorCode.PARAM_ERROR, false, null, null);
    }


    public static <T> ResponseDTO<T> userErrorParam(String msg) {
        return new ResponseDTO<>(UserErrorCode.PARAM_ERROR, false, msg, null);
    }

    // -------------------------------------------- 错误码 --------------------------------------------

    public static <T> ResponseDTO<T> error(ErrorCode errorCode) {
        return new ResponseDTO<>(errorCode, false, null, null);
    }

    public static <T> ResponseDTO<T> error(ErrorCode errorCode, boolean ok) {
        return new ResponseDTO<>(errorCode, ok, null, null);
    }

    public static <T>  ResponseDTO<T> error(ResponseDTO<?> responseDTO) {
        return new ResponseDTO<>(responseDTO.getCode(), responseDTO.getLevel(), responseDTO.getOk(), responseDTO.getMsg(), null);
    }

    public static <T> ResponseDTO<T> error(ErrorCode errorCode, String msg) {
        return new ResponseDTO<>(errorCode, false, msg, null);
    }

    public static <T> ResponseDTO<T> errorData(ErrorCode errorCode, T data) {
        return new ResponseDTO<>(errorCode, false, null, data);
    }


}
