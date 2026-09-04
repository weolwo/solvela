package solvela.admin.module.system.datatracer.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import solvela.base.util.SolvelaCaseFormat;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import solvela.base.util.SolvelaBigDecimalUtil;
import solvela.base.util.SolvelaCollectionUtil;
import solvela.base.util.SolvelaEnumUtil;
import solvela.base.util.SolvelaStringUtil;
import solvela.base.util.SolvelaDateFormatterEnum;
import solvela.base.util.SolvelaLocalDateUtil;
import solvela.admin.module.system.datatracer.annotation.*;
import solvela.admin.module.system.datatracer.constant.DataTracerConst;
import solvela.admin.module.system.datatracer.domain.bo.DataTracerContentBO;
import solvela.admin.module.system.dict.domain.vo.DictDataVO;
import solvela.admin.module.system.dict.service.DictService;
import solvela.base.json.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

/**
 * 数据变更内容
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-07-23 19:38:52
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Slf4j
@Service
public class DataTracerChangeContentService {

    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private DictService dictService;
    /**
     * 字段描述缓存
     */
    private final ConcurrentHashMap<String, String> fieldDescCacheMap = new ConcurrentHashMap<>();

    /**
     * 类 加注解字段缓存
     */
    private final ConcurrentHashMap<Class<?>, List<Field>> fieldMap = new ConcurrentHashMap<>();

    /**
     * 数据批量对比
     *
     * @param oldObjectList 原始对象列表
     * @param newObjectList 新的对象列表
     * @param <T>           Class类型
     * @return 变更内容
     */
    public <T> String getChangeContent(List<T> oldObjectList, List<T> newObjectList) {
        boolean valid = this.valid(oldObjectList, newObjectList);
        if (!valid) {
            return "";
        }
        String operateType = this.getOperateType(oldObjectList, newObjectList);
        String operateContent = "";
        if (DataTracerConst.INSERT.equals(operateType) || DataTracerConst.DELETE.equals(operateType)) {
            operateContent = this.getObjectListContent(newObjectList);
            if (StringUtils.isEmpty(operateContent)) {
                return "";
            }
            return operateType + ":" + operateContent;
        }
        if (DataTracerConst.UPDATE.equals(operateType)) {
            return this.getUpdateContentList(oldObjectList, newObjectList);
        }
        return operateContent;
    }


    /**
     * 解析多个对象的变更，删除，新增
     * oldObject 为空 ，newObject 不为空 为新增
     * oldObject 不为空 ，newObject 不空 为删除
     * 都不为空为编辑
     *
     * @param oldObject 原始对象
     * @param newObject 新对象
     * @return 变更内容
     */
    public String getChangeContent(Object oldObject, Object newObject) {
        boolean valid = this.valid(oldObject, newObject);
        if (!valid) {
            return "";
        }
        String operateType = this.getOperateType(oldObject, newObject);
        String operateContent = "";
        if (DataTracerConst.INSERT.equals(operateType) || DataTracerConst.DELETE.equals(operateType)) {
            operateContent = this.getAddDeleteContent(newObject);
        }
        if (DataTracerConst.UPDATE.equals(operateType)) {
            operateContent = this.getUpdateContent(oldObject, newObject);
        }
        if (StringUtils.isEmpty(operateContent)) {
            return "";
        }
        return operateContent;
    }

    /**
     * 解析单个bean的内容
     *
     * @param object 普通对象
     * @return 单个内容
     */
    public String getChangeContent(Object object) {
        return this.getAddDeleteContent(object);
    }

    // ---------------------------- 以下 是 私有private 方法 ----------------------------

    /**
     * 获取新增或删除操作内容
     *
     * @param object 新增或删除的对象
     */
    private String getAddDeleteContent(Object object) {
        List<Field> fields = this.getField(object);
        Map<String, DataTracerContentBO> beanParseMap = this.fieldParse(object, fields);
        return this.getAddDeleteContent(beanParseMap);
    }

    /**
     * 单个对象变动内容
     *
     * @param oldObjectList 旧的对象列表
     * @param newObjectList 新的对象列表
     * @return 拼接后的内容
     */
    private <T> String getUpdateContentList(List<T> oldObjectList, List<T> newObjectList) {
        String oldContent = this.getObjectListContent(oldObjectList);
        String newContent = this.getObjectListContent(newObjectList);
        if (oldContent.equals(newContent)) {
            return "";
        }
        if (StringUtils.isEmpty(oldContent) && StringUtils.isEmpty(newContent)) {
            return "";
        }
        return "【原数据】:<br/>" + oldContent + "<br/>" + "【新数据】:<br/>" + newContent;
    }

    /**
     * 解析批量bean的内容
     *
     * @param objectList 对象列表
     * @return 单个内容
     */
    public  <T> String  getChangeContent(List<T> objectList) {
        return this.getObjectListContent(objectList);
    }

    /**
     * 获取一个对象的内容信息
     *
     * @param objectList 对象列表
     * @param <T>        类型
     * @return 内容
     */
    private <T> String getObjectListContent(List<T> objectList) {
        if (SolvelaCollectionUtil.isEmpty(objectList)) {
            return "";
        }
        List<Field> fields = this.getField(objectList.get(0));
        List<String> contentList = new ArrayList<>();
        for (Object objItem : objectList) {
            Map<String, DataTracerContentBO> beanParseMap = this.fieldParse(objItem, fields);
            contentList.add(this.getAddDeleteContent(beanParseMap));
        }
        return StringUtils.join(contentList, "<br/>");
    }

    private String getAddDeleteContent(Map<String, DataTracerContentBO> beanParseMap) {
        List<String> contentList = new ArrayList<>();
        for (Entry<String, DataTracerContentBO> entry : beanParseMap.entrySet()) {
            DataTracerContentBO dataTracerContentBO = entry.getValue();
            boolean jsonFlag = isJsonShaped(dataTracerContentBO.getFieldContent());
            String filedDesc = dataTracerContentBO.getFieldDesc();
            if (jsonFlag) {
                contentList.add(filedDesc + "(请进入详情查看)");
            } else {
                contentList.add(dataTracerContentBO.getFieldDesc() + ":" + dataTracerContentBO.getFieldContent());
            }
        }
        String operateContent = StringUtils.join(contentList, "<br/>");
        if (StringUtils.isEmpty(operateContent)) {
            return "";
        }
        return operateContent;
    }


    /**
     * 获取更新操作内容
     *
     * @param oldObject 原始对象
     * @param newObject 新对象
     * @return
     */
    private <T> String getUpdateContent(T oldObject, T newObject) {
        List<Field> fields = this.getField(oldObject);
        List<String> contentList = new ArrayList<>();
        Map<String, DataTracerContentBO> oldBeanParseMap = this.fieldParse(oldObject, fields);
        Map<String, DataTracerContentBO> newBeanParseMap = this.fieldParse(newObject, fields);
        //oldBeanParseMap与newBeanParseMap size一定相同
        for (Entry<String, DataTracerContentBO> entry : oldBeanParseMap.entrySet()) {
            String fieldName = entry.getKey();
            // 新旧对象内容
            DataTracerContentBO oldContentBO = entry.getValue();
            DataTracerContentBO newContentBO = newBeanParseMap.get(fieldName);
            // fieldContent
            String oldContent = oldContentBO == null || oldContentBO.getFieldContent() == null ? "" : oldContentBO.getFieldContent();
            String newContent = newContentBO == null || newContentBO.getFieldContent() == null ? "" : newContentBO.getFieldContent();

            if (oldContent.equals(newContent)) {
                continue;
            }
            String fieldDesc = oldContentBO.getFieldDesc();
            boolean jsonFlag = isJsonShaped(oldContent) || isJsonShaped(newContent);
            if (jsonFlag) {
                String content = fieldDesc + "【进入详情查看】";
                contentList.add(content);
                continue;
            }
            String content = fieldDesc + ":" + "由【" + oldContent + "】变更为【" + newContent + "】";
            contentList.add(content);
        }
        if (SolvelaCollectionUtil.isEmpty(contentList)) {
            return "";
        }
        String operateContent = StringUtils.join(contentList, "<br/>");
        if (StringUtils.isEmpty(operateContent)) {
            return "";
        }
        return operateContent;
    }


    /**
     * 接bean对象
     *
     * @param object 对象
     * @param fields 字段
     * @return <desc,value></>
     */
    private Map<String, DataTracerContentBO> fieldParse(Object object, List<Field> fields) {
        if (fields == null || fields.isEmpty()) {
            return new HashMap<>();
        }
        //对象解析结果
        Map<String, DataTracerContentBO> objectParse = new HashMap<>(16);
        for (Field field : fields) {
            field.setAccessible(true);
            String desc = this.getFieldDesc(field);
            if (StringUtils.isEmpty(desc)) {
                continue;
            }
            DataTracerContentBO dataTracerContentBO = this.getFieldValue(field, object);
            if (dataTracerContentBO != null) {
                dataTracerContentBO.setFieldDesc(desc);
                objectParse.put(field.getName(), dataTracerContentBO);
            }
        }
        return objectParse;
    }

    /**
     * 取一个字段的值，并把它渲染成变更日志里给人看的那句话。
     *
     * @return null 表示这个字段不进变更日志（读不出来，或者值本来就是 null）
     */
    private DataTracerContentBO getFieldValue(Field field, Object object) {
        Object fieldValue = readValue(field, object);
        if (fieldValue == null) {
            return null;
        }
        DataTracerContentBO dataTracerContentBO = new DataTracerContentBO();
        dataTracerContentBO.setField(field);
        dataTracerContentBO.setFieldValue(fieldValue);
        dataTracerContentBO.setFieldContent(renderContent(field, fieldValue));
        return dataTracerContentBO;
    }

    /**
     * 走 getter 而不是 {@code field.setAccessible(true)} 直读：Lombok 生成的 getter
     * 可能带逻辑，而变更日志要记的是「对外表现出来的值」。
     *
     * <p>读失败只记日志不抛：一个字段读不出来不该让整条变更日志写不成。
     */
    private Object readValue(Field field, Object object) {
        try {
            PropertyDescriptor pd = new PropertyDescriptor(field.getName(), object.getClass());
            Method get = pd.getReadMethod();
            return get.invoke(object);
        } catch (Exception e) {
            log.error("bean operate log: reflect field value error " + field.getName());
            return null;
        }
    }

    /**
     * 值 -> 人话。分支顺序即优先级：<b>注解声明的翻译方式压过类型默认渲染</b> ——
     * 一个 Integer 字段标了 {@code @DataTracerFieldEnum}，运营要看的是「已启用」而不是 1。
     *
     * <p>⚠️ BigDecimal 那一支只有标了 {@code @DataTracerFieldBigDecimal} 才有输出，
     * 没标的会渲染成空串（历史行为，此处保持不变）—— 金额字段记得加注解，
     * 否则它在变更日志里是一片空白。
     */
    private String renderContent(Field field, Object fieldValue) {
        DataTracerFieldEnum dataTracerFieldEnum = field.getAnnotation(DataTracerFieldEnum.class);
        DataTracerFieldSql dataTracerFieldSql = field.getAnnotation(DataTracerFieldSql.class);
        DataTracerFieldDict dataTracerFieldDict = field.getAnnotation(DataTracerFieldDict.class);

        String fieldContent = "";
        if (dataTracerFieldEnum != null) {
            fieldContent = fieldValue instanceof Collection
                    ? SolvelaEnumUtil.getEnumDescByValueList((Collection) fieldValue, dataTracerFieldEnum.enumClass())
                    : SolvelaEnumUtil.getEnumDescByValue(fieldValue, dataTracerFieldEnum.enumClass());
        } else if (dataTracerFieldDict != null) {
            DictDataVO dictData = dictService.getDictData(dataTracerFieldDict.dictCode(), fieldValue.toString());
            fieldContent = dictData == null ? fieldValue.toString() : dictData.getDataLabel();
        } else if (dataTracerFieldSql != null) {
            fieldContent = this.getRelateDisplayValue(fieldValue, dataTracerFieldSql);
        } else if (fieldValue instanceof Date) {
            fieldContent = SolvelaLocalDateUtil.format(SolvelaLocalDateUtil.toLocalDateTime((Date) fieldValue), SolvelaDateFormatterEnum.YMD_HMS);
        } else if (fieldValue instanceof LocalDateTime) {
            fieldContent = SolvelaLocalDateUtil.format((LocalDateTime) fieldValue, SolvelaDateFormatterEnum.YMD_HMS);
        } else if (fieldValue instanceof LocalDate) {
            fieldContent = SolvelaLocalDateUtil.format((LocalDate) fieldValue, SolvelaDateFormatterEnum.YMD);
        } else if (fieldValue instanceof BigDecimal) {
            DataTracerFieldBigDecimal dataTracerFieldBigDecimal = field.getAnnotation(DataTracerFieldBigDecimal.class);
            if (dataTracerFieldBigDecimal != null) {
                fieldContent = SolvelaBigDecimalUtil.setScale((BigDecimal) fieldValue,
                        dataTracerFieldBigDecimal.scale()).toString();
            }
        } else {
            fieldContent = JsonUtils.toJson(fieldValue);
        }
        return fieldContent;
    }

    /**
     * 只看「长得像不像 JSON」：去掉首尾空白后被 {} 或 [] 包住即算。
     *
     * 这里刻意不做真解析 —— 用途只是决定变更日志里要不要写成「进入详情查看」，
     * 一段畸形 JSON 也照样该收起来，为此去 try-catch 一次反序列化是白花开销。
     * 与原 JSONUtil.isTypeJSON 的判定口径一致。
     */
    private boolean isJsonShaped(String str) {
        if (SolvelaStringUtil.isBlank(str)) {
            return false;
        }
        String trimmed = str.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    /**
     * 获取关联字段的显示值
     */
    private String getRelateDisplayValue(Object fieldValue, DataTracerFieldSql dataTracerFieldSql) {
        Class<? extends BaseMapper> relateMapper = dataTracerFieldSql.relateMapper();
        BaseMapper<?> mapper = applicationContext.getBean(relateMapper);
        if (mapper == null) {
            return "";
        }
        String relateFieldValue = fieldValue.toString();
        QueryWrapper qw = new QueryWrapper();
        qw.select(SolvelaCaseFormat.UPPER_CAMEL.to(SolvelaCaseFormat.LOWER_UNDERSCORE, dataTracerFieldSql.relateDisplayColumn()));
        qw.in(SolvelaCaseFormat.UPPER_CAMEL.to(SolvelaCaseFormat.LOWER_UNDERSCORE, dataTracerFieldSql.relateColumn()), relateFieldValue);
        List<Object> displayValue = mapper.selectObjs(qw);
        if (SolvelaCollectionUtil.isEmpty(displayValue)) {
            return "";
        }
        return SolvelaStringUtil.join(",", displayValue);
    }

    /**
     * 获取字段描述信息 优先 OperateField 没得话swagger判断
     */
    private String getFieldDesc(Field field) {
        // 根据字段名称 从缓存中查询
        String fieldName = field.toGenericString();
        String desc = fieldDescCacheMap.get(fieldName);
        if (null != desc) {
            return desc;
        }
        DataTracerFieldLabel operateField = field.getAnnotation(DataTracerFieldLabel.class);
        if (operateField != null) {
            return operateField.value();
        }
        fieldDescCacheMap.put(fieldName, desc);
        return desc;
    }

    /**
     * 获取操作类型
     */
    private String getOperateType(Object oldObject, Object newObject) {
        if (oldObject == null && newObject != null) {
            return DataTracerConst.INSERT;
        }
        if (oldObject != null && newObject == null) {
            return DataTracerConst.DELETE;
        }
        return DataTracerConst.UPDATE;
    }

    /**
     * 校验是否进行比对
     */
    private boolean valid(Object oldObject, Object newObject) {
        if (oldObject == null && newObject == null) {
            return false;
        }
        if (oldObject == null) {
            return true;
        }
        if (newObject == null) {
            return true;
        }
        String oldClass = oldObject.getClass().getName();
        String newClass = newObject.getClass().getName();
        return oldClass.equals(newClass);
    }


    /**
     * 校验
     */
    private <T> boolean valid(List<T> oldObjectList, List<T> newObjectList) {
        if (SolvelaCollectionUtil.isEmpty(oldObjectList) && SolvelaCollectionUtil.isEmpty(newObjectList)) {
            return false;
        }
        if (SolvelaCollectionUtil.isEmpty(oldObjectList) && SolvelaCollectionUtil.isNotEmpty(newObjectList)) {
            return true;
        }
        if (SolvelaCollectionUtil.isNotEmpty(oldObjectList) && SolvelaCollectionUtil.isEmpty(newObjectList)) {
            return true;
        }
        if (SolvelaCollectionUtil.isNotEmpty(oldObjectList) && SolvelaCollectionUtil.isNotEmpty(newObjectList)) {
            T oldObject = oldObjectList.get(0);
            T newObject = newObjectList.get(0);
            String oldClass = oldObject.getClass().getName();
            String newClass = newObject.getClass().getName();
            return oldClass.equals(newClass);
        }
        return true;
    }

    /**
     * 查询 包含 file key 注解的字段
     * 使用缓存
     */
    private List<Field> getField(Object obj) {
        // 从缓存中查询
        Class<?> tClass = obj.getClass();
        List<Field> fieldList = fieldMap.get(tClass);
        if (null != fieldList) {
            return fieldList;
        }

        // 这一段递归代码 是为了 从父类中获取属性
        Class<?> tempClass = tClass;
        fieldList = new ArrayList<>();
        while (tempClass != null) {
            Field[] declaredFields = tempClass.getDeclaredFields();
            for (Field field : declaredFields) {
                // 过虑出有注解字段
                if (!field.isAnnotationPresent(DataTracerFieldLabel.class)) {
                    continue;
                }
                field.setAccessible(true);
                fieldList.add(field);
            }
            tempClass = tempClass.getSuperclass();
        }
        fieldMap.put(tClass, fieldList);
        return fieldList;
    }


}
