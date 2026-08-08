package net.lab1024.sa.base.module.support.codegenerator.service;

import cn.hutool.core.bean.BeanUtil;
import net.lab1024.sa.base.common.util.SmartCaseFormat;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.util.SmartDateFormatterEnum;
import net.lab1024.sa.base.common.util.SmartLocalDateUtil;
import net.lab1024.sa.base.common.util.SmartRandomUtil;
import net.lab1024.sa.base.common.util.SmartStringUtil;
import net.lab1024.sa.base.module.support.codegenerator.domain.entity.CodeGeneratorConfigEntity;
import net.lab1024.sa.base.module.support.codegenerator.domain.form.CodeGeneratorConfigForm;
import net.lab1024.sa.base.module.support.codegenerator.domain.model.*;
import net.lab1024.sa.base.module.support.codegenerator.service.variable.CodeGenerateBaseVariableService;
import net.lab1024.sa.base.module.support.codegenerator.service.variable.backend.*;
import net.lab1024.sa.base.module.support.codegenerator.service.variable.backend.domain.*;
import net.lab1024.sa.base.module.support.codegenerator.service.variable.front.ApiVariableService;
import net.lab1024.sa.base.module.support.codegenerator.service.variable.front.ConstVariableService;
import net.lab1024.sa.base.module.support.codegenerator.service.variable.front.FormVariableService;
import net.lab1024.sa.base.module.support.codegenerator.service.variable.front.ListVariableService;
import net.lab1024.sa.base.module.support.codegenerator.util.CodeGeneratorTool;
import net.lab1024.sa.base.common.util.JsonUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.velocity.Template;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.tools.ToolContext;
import org.apache.velocity.tools.ToolManager;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 代码生成器 模板 Service
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-06-30 22:15:38
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */

@Service
@Slf4j
public class CodeGeneratorTemplateService {


    private Map<String, CodeGenerateBaseVariableService> map = new HashMap<>();

    @PostConstruct
    public void init() {
        // 后端
        map.put("java/domain/entity/Entity.java", new EntityVariableService());
        map.put("java/domain/form/AddForm.java", new AddFormVariableService());
        map.put("java/domain/form/UpdateForm.java", new UpdateFormVariableService());
        map.put("java/domain/form/QueryForm.java", new QueryFormVariableService());
        map.put("java/domain/vo/VO.java", new VOVariableService());
        map.put("java/controller/Controller.java", new ControllerVariableService());
        map.put("java/service/Service.java", new ServiceVariableService());
        map.put("java/manager/Manager.java", new ManagerVariableService());
        map.put("java/dao/Dao.java", new DaoVariableService());
        map.put("java/mapper/Mapper.xml", new MapperVariableService());
        // 菜单 SQL
        map.put("java/sql/Menu.sql", new MenuVariableService());
        // 前端
        map.put("js/api.js", new ApiVariableService());
        map.put("js/const.js", new ConstVariableService());
        map.put("js/list.vue", new ListVariableService());
        map.put("js/form.vue", new FormVariableService());
        // ts前端
        map.put("ts/api.ts", new ApiVariableService());
        map.put("ts/const.ts", new ConstVariableService());
        map.put("ts/list.vue", new ListVariableService());
        map.put("ts/form.vue", new FormVariableService());
    }

    public void zipGeneratedFiles(OutputStream outputStream, String tableName, CodeGeneratorConfigEntity codeGeneratorConfigEntity) {
        String uuid = SmartRandomUtil.simpleUuid();
        File dir = new File(uuid);

        // 1、生产文件
        CodeBasic basic = JsonUtils.parseObject(codeGeneratorConfigEntity.getBasic(), CodeBasic.class);
        String moduleName = basic.getModuleName();

        for (Map.Entry<String, CodeGenerateBaseVariableService> entry : map.entrySet()) {
            try {
                String templateFile = entry.getKey();
                String upperCamel = new CodeGeneratorTool().lowerCamel2UpperCamel(moduleName);
                String lowerHyphen = new CodeGeneratorTool().lowerCamel2LowerHyphen(moduleName);
                String[] templateSplit = templateFile.split("/");
                String fileName = templateFile.startsWith("java") ? upperCamel + (templateSplit[templateSplit.length - 1].contains("Entity")?".java":templateSplit[templateSplit.length - 1]) : lowerHyphen + "-" + templateSplit[templateSplit.length - 1];
                String fullPathFileName = templateFile.replaceAll(templateSplit[templateSplit.length - 1], fileName);
                fullPathFileName = fullPathFileName.replaceAll("java/", "java/" + basic.getModuleName().toLowerCase() + "/");
                fullPathFileName = fullPathFileName.replaceAll("js/", "js/" + lowerHyphen + "/");

                String fileContent = generate(tableName, templateFile, codeGeneratorConfigEntity);
                File file = new File(uuid + "/" + fullPathFileName);
                file.getParentFile().mkdirs();
                appendUtf8(file, fileContent);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }

        // 2、后端的枚举文件
        List<CodeField> fields = JsonUtils.parseList(codeGeneratorConfigEntity.getFields(), CodeField.class);
        if (CollectionUtils.isNotEmpty(fields)) {
            List<CodeField> enumFiledList = fields.stream().filter(e -> SmartStringUtil.isNotBlank(e.getEnumName())).collect(Collectors.toList());
            for (CodeField codeField : enumFiledList) {
                Map<String, Object> variablesMap = new HashMap<>();

                String enumName = SmartCaseFormat.LOWER_CAMEL.to(SmartCaseFormat.UPPER_CAMEL, codeField.getEnumName());
                if (!enumName.endsWith("Enum")) {
                    enumName = enumName + "Enum";
                }
                variablesMap.put("enumName", enumName);
                variablesMap.put("enumDesc", codeField.getColumnComment());
                variablesMap.put("enumJavaType", codeField.getJavaType());
                variablesMap.put("basic", basic);
                variablesMap.put("packageName", basic.getJavaPackageName() + ".constant");

                String fileContent = render("code-generator-template/java/constant/enum.java.vm", variablesMap);
                File file = new File(uuid + "/java/" + basic.getModuleName().toLowerCase() + "/constant/" + enumName + ".java");
                file.getParentFile().mkdirs();
                try {
                    appendUtf8(file, fileContent);
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }


        try {
            zipDirectoryContent(outputStream, dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * 生成时间戳，null 时返回 null —— 模板里 basic.frontDate 允许没配
     */
    private static String formatDateTime(java.time.LocalDateTime time) {
        return time == null ? null : SmartLocalDateUtil.format(time, SmartDateFormatterEnum.YMD_HMS);
    }

    /**
     * 以 UTF-8 追加写入，文件不存在则创建（原 FileUtil.appendUtf8String）
     */
    static void appendUtf8(File file, String content) throws IOException {
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * 把目录里的内容打成 zip 写进 outputStream —— 是「内容」，不含 dir 这一层目录本身
     * （对应原 ZipUtil.zip 的 withSrcDir=false），所以解压出来直接就是 java/、js/ 这些目录。
     *
     * 这里必须关掉 ZipOutputStream：中央目录是在 close 时才写的，不关就是一个打不开的空包。
     * 调用方传进来的是 ByteArrayOutputStream，关它没有副作用，toByteArray 照常可用。
     */
    static void zipDirectoryContent(OutputStream outputStream, File dir) throws IOException {
        Path root = dir.toPath();
        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream, StandardCharsets.UTF_8);
             Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                // zip 里一律用正斜杠，Windows 下 Path 的分隔符是反斜杠，直接拼会生成一个畸形包
                String entryName = root.relativize(path).toString().replace(File.separatorChar, '/');
                zipOut.putNextEntry(new ZipEntry(entryName));
                Files.copy(path, zipOut);
                zipOut.closeEntry();
            }
        }
    }

    /**
     * 递归删除临时目录（原 FileUtil.del）。删不掉只记日志，不能让它盖掉正常返回。
     *
     * 包级可见而非 private：这三个方法是从 hutool FileUtil/ZipUtil 手抄回来的，
     * 必须能被同包测试直接打，见 CodeGeneratorTemplateZipTest。
     */
    static void deleteRecursively(File dir) {
        if (!dir.exists()) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            // 倒序：先文件后目录，否则非空目录删不掉
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("删除代码生成临时文件失败：{}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("清理代码生成临时目录失败：{}", dir, e);
        }
    }


    public String generate(String tableName, String file, CodeGeneratorConfigEntity codeGeneratorConfigEntity) {

        // -------------------- 1 校验不支持的代码生成，比如增加、删除等 --------------------

        String finalFile = file;
        Optional<String> optional = map.keySet().stream().filter(e -> e.contains(finalFile)).findFirst();
        if (!optional.isPresent()) {
            return "不存在此模板！";
        }

        file = optional.get();
        CodeGenerateBaseVariableService codeGenerateBaseVariableService = map.get(file);
        if (codeGenerateBaseVariableService == null) {
            return "代码生成Service不存在，请检查相关代码！";
        }

        CodeBasic basic = JsonUtils.parseObject(codeGeneratorConfigEntity.getBasic(), CodeBasic.class);
        List<CodeField> fields = JsonUtils.parseList(codeGeneratorConfigEntity.getFields(), CodeField.class);
        CodeInsertAndUpdate insertAndUpdate = JsonUtils.parseObject(codeGeneratorConfigEntity.getInsertAndUpdate(), CodeInsertAndUpdate.class);
        CodeDelete deleteInfo = JsonUtils.parseObject(codeGeneratorConfigEntity.getDeleteInfo(), CodeDelete.class);
        List<CodeQueryField> queryFields = JsonUtils.parseList(codeGeneratorConfigEntity.getQueryFields(), CodeQueryField.class);
        List<CodeTableField> tableFields = JsonUtils.parseList(codeGeneratorConfigEntity.getTableFields(), CodeTableField.class);
        tableFields.forEach(e -> e.setWidth(e.getWidth() == null ? 0 : e.getWidth()));

        CodeGeneratorConfigForm form = CodeGeneratorConfigForm.builder().basic(basic).fields(fields).insertAndUpdate(insertAndUpdate).deleteInfo(deleteInfo).queryFields(queryFields).tableFields(tableFields).deleteInfo(deleteInfo).build();
        form.setTableName(tableName);
        if (!codeGenerateBaseVariableService.isSupport(form)) {
            return "业务不需要此功能，故没有生成代码；";
        }

        // -------------------- 2 通用模板的变量 --------------------
        Map<String, Object> variablesMap = new HashMap<>();


        Map<String, Object> basicMap = BeanUtil.beanToMap(basic);
        basicMap.put("frontDate", formatDateTime(basic.getFrontDate()));
        basicMap.put("backendDate", formatDateTime(basic.getBackendDate()));

        variablesMap.put("basic", basicMap);
        variablesMap.put("fields", fields);
        variablesMap.put("insertAndUpdate", insertAndUpdate);
        variablesMap.put("deleteInfo", deleteInfo);
        variablesMap.put("queryFields", queryFields);
        variablesMap.put("tableFields", tableFields);
        variablesMap.put("tableName", tableName);

        //名词的大写开头和小写开头
        HashMap<String, String> names = new HashMap<>();
        names.put("lowerCamel", SmartCaseFormat.UPPER_CAMEL.to(SmartCaseFormat.LOWER_CAMEL, basic.getModuleName()));
        names.put("upperCamel", SmartCaseFormat.UPPER_CAMEL.to(SmartCaseFormat.UPPER_CAMEL, basic.getModuleName()));
        names.put("lowerHyphenCamel", SmartCaseFormat.UPPER_CAMEL.to(SmartCaseFormat.LOWER_HYPHEN, basic.getModuleName()));
        variablesMap.put("name", names);

        //主键字段名称和java类型
        CodeField primaryKeycodeField = fields.stream().filter(e -> e.getPrimaryKeyFlag()).findFirst().get();
        if (primaryKeycodeField != null) {
            variablesMap.put("primaryKeyJavaType", primaryKeycodeField.getJavaType());
            variablesMap.put("primaryKeyFieldName", primaryKeycodeField.getFieldName());
            variablesMap.put("primaryKeyColumnName", primaryKeycodeField.getColumnName());
        }

        // -------------------- 3、针对此 模板 的特殊变量 --------------------

        Map<String, Object> specialVariables = codeGenerateBaseVariableService.getInjectVariablesMap(form);
        variablesMap.putAll(specialVariables);

        // -------------------- 4、模板 生成代码 --------------------

        return render("code-generator-template/" + file + ".vm", variablesMap);
    }

    /**
     * 渲染
     *
     * @param templateFile
     * @param variablesMap
     * @return
     */
    private String render(String templateFile, Map<String, Object> variablesMap) {
        VelocityEngine engine = new VelocityEngine();
        engine.setProperty(Velocity.FILE_RESOURCE_LOADER_CACHE, true);
        engine.setProperty(Velocity.INPUT_ENCODING, "UTF-8");
        engine.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        engine.init();
        Template template = engine.getTemplate(templateFile);

        //加载tools.xml配置文件
        ToolManager toolManager = new ToolManager();
        toolManager.configure("code-generator-template/tools.xml");

        //注入变量
        ToolContext context = toolManager.createContext();
        context.putAll(variablesMap);

        StringWriter sw = new StringWriter();
        template.merge(context, sw);
        return sw.toString();
    }

}
