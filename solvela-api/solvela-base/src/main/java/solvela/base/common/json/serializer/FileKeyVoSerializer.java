package solvela.base.common.json.serializer;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;
import jakarta.annotation.Resource;
import solvela.base.module.support.file.domain.vo.FileVO;
import solvela.base.module.support.file.service.FileAssetService;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * 文件key进行序列化对象
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2020/8/15 22:06
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
public class FileKeyVoSerializer extends ValueSerializer<String> {

    @Resource
    private FileAssetService fileAssetService;


    @Override
    public void serialize(String value, JsonGenerator jsonGenerator, SerializationContext serializerProvider) {
        if (StringUtils.isEmpty(value)) {
            jsonGenerator.writePOJO(new ArrayList<>());
            return;
        }
        if(fileAssetService == null){
            jsonGenerator.writeString(value);
            return;
        }
        String[] fileKeyArray = value.split(",");
        List<String> fileKeyList = Arrays.asList(fileKeyArray);
        List<FileVO> fileKeyVOList = fileAssetService.listByStorageKeys(fileKeyList);
        jsonGenerator.writePOJO(fileKeyVOList);
    }
}
