package solvela.app.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在接口方法上：<b>在不受信任的设备上调用它，要先过一次二次验证</b>。
 * 由 {@link StepUpInterceptor} 执行，灰度档位见 {@link StepUpProperties}。
 *
 * <h3>只标「指定新的收件方」的接口</h3>
 * 本站资产被带出账号只有两条路：往地址簿加一个新地址（之后兑换、补填发货都只收 addressId），
 * 和充话费到任意手机号。所以标在：
 * <ul>
 *   <li>{@code POST /address}、{@code PUT /address/{id}}</li>
 *   <li>{@code POST /recharge/order}</li>
 * </ul>
 * 用已有地址兑换、抽奖、签到<b>不标</b>：它们拿不走任何东西（兑到的东西寄回受害者家里），
 * 标了只会让每个换了新手机的用户在最常用的操作上被打扰。
 *
 * <p>🔴 新增一个「能把资产送到账号之外」的接口时，要问一句它该不该标。
 * 与 {@link Anonymous} 同样的道理：注解是一个方法一个决定，漏标在 code review 里看得见。
 *
 * @Date 2026-09-26
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StepUpRequired {
}
