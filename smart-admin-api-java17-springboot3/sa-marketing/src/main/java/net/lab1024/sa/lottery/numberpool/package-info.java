/**
 * 【已停用】彩票号池方案，仅作算法学习材料保留，新链路禁止引用。
 *
 * <h3>为什么停用</h3>
 * 号池方案是「先把一期的全部号码生成好灌进 t_lottery_number_pool，用户来了发一条」。
 * 已被 FPE 发号方案取代：Redis 维护一个连续游标，游标经格式保留加密（FPE）变成
 * 一个不可预测但保证不重复的号码，<b>不需要预生成任何号码</b>。
 * 表 {@code t_lottery_number_pool} 已在 {@code 数据库SQL脚本/lottery.sql} 中永久移除，
 * 菜单与权限点在 {@code v3.39.0.sql} 中清理。
 *
 * <h3>当前状态：能编译、能读，但不进 Spring 容器</h3>
 * 本包所有类都已摘掉 Spring / MyBatis 注解（Dao 的 {@code @Mapper}、
 * Service 与 Manager 的 {@code @Service}、Entity 的 {@code @TableName}），
 * Controller 与 Mapper.xml 已删除。保留的 {@code @Data} / {@code @RequiredArgsConstructor}
 * 是 Lombok 编译期注解，与容器无关。
 *
 * <p><b>⚠️ 不要给本包里的任何类加回 Spring 注解。</b>
 * {@code AdminApplication} 的扫描配置是 {@code @MapperScan(annotationClass = Mapper.class)}，
 * 只注册带 {@code @Mapper} 的接口。加回 {@code @Service} 却没有 {@code @Mapper}，
 * 启动期会直接抛 {@code NoSuchBeanDefinitionException}；而反过来加回 {@code @Mapper}，
 * 由于 Mapper.xml 已删、{@code queryPage} 又只有接口声明，启动期会抛
 * {@code Invalid bound statement}。<b>两个方向都会炸，而且 {@code mvn compile} 全都能通过。</b>
 *
 * <h3>留着它是为了看什么</h3>
 * {@link net.lab1024.sa.lottery.numberpool.service.DynamicNumbersGenerator} 里的容量路由判断：
 * 按「需求量 / 号码空间」的比值在两种策略间切换 —— 需求密集时用进制洗牌法（生成全域再 shuffle 截取，
 * 内存换确定性），需求稀疏时用哈希去重法（随机试探 + Set 去重，避免为几百个号生成上百万条）。
 *
 * <p><b>⚠️ 它的 charset 参数是「字面字符列表」</b>（如 {@code "01258"} 表示只用这 5 个数字），
 * 与 {@code t_lottery_config.number_charset} 存的区间表达式（{@code "0-9"}）不是一回事。
 * 把 {@code "0-9"} 直接喂进来会被当成 {'0','-','9'} 三进制，号码空间从 10^5 塌缩到 243。
 * 新引擎固定十进制、根本不接受 charset 参数，正是为了从源头消灭这类歧义。
 *
 * @author weolwo
 */
package net.lab1024.sa.lottery.numberpool;
