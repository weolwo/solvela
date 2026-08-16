package sa.base.module.support.message.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import sa.base.module.support.message.dao.MessageDao;
import sa.base.module.support.message.domain.MessageEntity;
import org.springframework.stereotype.Service;

/**
 * 消息manager
 *
 * @author luoyi
 * @date 2024/06/22 20:20
 */
@Service
public class MessageManager extends ServiceImpl<MessageDao, MessageEntity> {


}
