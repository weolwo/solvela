package net.lab1024.sa.base.module.support.serialnumber.service.impl;

import net.lab1024.sa.base.module.support.serialnumber.domain.SerialNumberEntity;
import net.lab1024.sa.base.module.support.serialnumber.domain.SerialNumberGenerateResultBO;
import net.lab1024.sa.base.module.support.serialnumber.domain.SerialNumberInfoBO;
import net.lab1024.sa.base.module.support.serialnumber.domain.SerialNumberLastGenerateBO;
import net.lab1024.sa.base.module.support.serialnumber.service.SerialNumberBaseService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单据序列号 基于内存锁实现
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-03-25 21:46:07
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Service
public class SerialNumberInternService extends SerialNumberBaseService {

    /**
     * 按照 serialNumberId 进行锁。
     *
     * <p>🔴 <b>这里必须拿到「同一个 id 对应同一个对象」，不能直接 synchronized(serialNumberId)。</b>
     * Integer 只在 -128~127 之间有缓存，超出这个范围每次装箱都是<b>新对象</b>，
     * 两个线程锁的根本不是同一把锁 —— 互斥静默失效，表现是并发下发重号。
     * 这正是原先用 Guava {@code Interners.newStrongInterner()} 的原因。
     *
     * <p>去掉 Guava 后改用 {@code ConcurrentHashMap.computeIfAbsent} 做同样的事：
     * 每个 id 规范化成唯一的一把锁。与 strong interner 一样不回收 ——
     * 发号器 id 是有限的几个，不存在内存增长问题。
     */
    private static final ConcurrentHashMap<Integer, Object> LOCK_POOL = new ConcurrentHashMap<>();

    /**
     * 取该 serialNumberId 的唯一锁对象
     */
    private static Object lockOf(Integer serialNumberId) {
        return LOCK_POOL.computeIfAbsent(serialNumberId, id -> new Object());
    }


    private ConcurrentHashMap<Integer, SerialNumberLastGenerateBO> serialNumberLastGenerateMap = new ConcurrentHashMap<>();

    @Override
    public void initLastGenerateData(List<SerialNumberEntity> serialNumberEntityList) {
        if (serialNumberEntityList == null) {
            return;
        }

        for (SerialNumberEntity serialNumberEntity : serialNumberEntityList) {
            SerialNumberLastGenerateBO lastGenerateBO = SerialNumberLastGenerateBO
                    .builder()
                    .serialNumberId(serialNumberEntity.getSerialNumberId())
                    .lastNumber(serialNumberEntity.getLastNumber())
                    .lastTime(serialNumberEntity.getLastTime())
                    .build();
            serialNumberLastGenerateMap.put(serialNumberEntity.getSerialNumberId(), lastGenerateBO);
        }
    }

    @Override
    public List<String> generateSerialNumberList(SerialNumberInfoBO serialNumberInfo, int count) {
        SerialNumberGenerateResultBO serialNumberGenerateResult = null;
        synchronized (lockOf(serialNumberInfo.getSerialNumberId())) {

            // 获取上次的生成结果
            SerialNumberLastGenerateBO lastGenerateBO = serialNumberLastGenerateMap.get(serialNumberInfo.getSerialNumberId());

            // 生成
            serialNumberGenerateResult = super.loopNumberList(lastGenerateBO, serialNumberInfo, count);

            // 将生成信息保存的内存和数据库
            lastGenerateBO.setLastNumber(serialNumberGenerateResult.getLastNumber());
            lastGenerateBO.setLastTime(serialNumberGenerateResult.getLastTime());
            serialNumberDao.updateLastNumberAndTime(serialNumberInfo.getSerialNumberId(),
                    serialNumberGenerateResult.getLastNumber(),
                    serialNumberGenerateResult.getLastTime());

            // 把生成过程保存到数据库里
            super.saveRecord(serialNumberGenerateResult);
        }

        return formatNumberList(serialNumberGenerateResult, serialNumberInfo);
    }


}
