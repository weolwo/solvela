package solvela.base.json.serializer;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;
import jakarta.annotation.Resource;
import solvela.base.module.file.service.FileAssetService;
import org.apache.commons.lang3.StringUtils;

/**
 * 文件key进行序列化对象
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2020/8/15 22:06
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
public class FileKeySerializer extends ValueSerializer<String> {

    @Resource
    private FileAssetService fileAssetService;


    @Override
    public void serialize(String value, JsonGenerator jsonGenerator, SerializationContext serializerProvider) {
        if (StringUtils.isEmpty(value)) {
            jsonGenerator.writeString(value);
            return;
        }
        if (fileAssetService == null) {
            jsonGenerator.writeString(value);
            return;
        }
        String url = fileAssetService.urlByStorageKeys(value);
        // 查不到就原样回 storageKey，比吐一个空串更容易排查
        jsonGenerator.writeString(url.isEmpty() ? value : url);
    }
}
