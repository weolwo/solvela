package net.lab1024.sa.base.common.json.deserializer;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件key反序列化<br>
 * 由于前端接收到的是序列化过的字段, 这边入库需要进行反序列化操作比较方便处理
 *
 * @Author 1024创新实验室: 胡克
 * @Date 2022-11-24 17:15:23
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Slf4j
public class FileKeyVoDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) {
        List<FileVO> list = new ArrayList<>();
        // Jackson 3 移除了 ObjectCodec / JsonParser.getCodec()，读树与树转对象统一走 DeserializationContext
        JsonNode listOrObjectNode = deserializationContext.readTree(jsonParser);
        String deserialize = "";
        try {
            if (listOrObjectNode.isArray()) {
                for (JsonNode node : listOrObjectNode) {
                    list.add(deserializationContext.readTreeAsValue(node, FileVO.class));
                }
            } else {
                list.add(deserializationContext.readTreeAsValue(listOrObjectNode, FileVO.class));
            }
            deserialize = list.stream().map(FileVO::getStorageKey).collect(Collectors.joining(","));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            deserialize = listOrObjectNode.asText();
        }
        return deserialize;
    }


}
