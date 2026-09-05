package solvela.base.module.file.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import solvela.base.module.file.domain.vo.FileVO;
import solvela.base.module.file.domain.entity.FileEntity;
import solvela.base.module.file.domain.form.FileQueryForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 文件服务
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2019年10月11日 15:34:47
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Mapper
public interface FileDao extends BaseMapper<FileEntity> {

    /**
     * 文件key单个查询
     *
     * @param fileKey
     * @return
     */
    FileVO getByFileKey(@Param("fileKey") String fileKey);


    /**
     * 批量获取
     */
    List<FileVO> selectByFileKeyList(@Param("fileKeyList") Collection<String> fileKeyList);

    /**
     * 分页 查询
     *
     * @param page
     * @param queryForm
     * @return
     */
    List<FileVO> queryPage(Page page, @Param("queryForm") FileQueryForm queryForm);

    /**
     * 按 storageKey 取<b>公开分类下</b>的文件，给免登录读取端点用。
     *
     * <p>🔴 <b>公开性判断做在 SQL 里，不在 Java 里。</b>
     * 分成「先查文件、再查分类、再 if」三步的话，那个 if 是可以被忘掉的 ——
     * 而忘掉的表现是免登录端点吐出私有文件，且没有任何报错。
     * 写成一条 join，调用方拿到 null 就只有一个含义：<b>不存在或不该给</b>。
     *
     * <p>顺带保住了「不给探测者任何区分信号」那条：
     * 「文件不存在」和「文件存在但分类不公开」返回的都是 null，
     * 端点一律 404，攻击者无法用响应差异枚举出哪些 key 是有效的。
     *
     * @return 命中且分类公开时返回文件，否则 null
     */
    FileEntity selectPublicByStorageKey(@Param("storageKey") String storageKey);

    /**
     * 各分类的文件数。<b>一次 GROUP BY 出全部</b>，不是每个分类查一次 ——
     * 分类卡片页一次要展示几十张卡，逐个 count 就是 N+1，而这是运营每天进的页面。
     *
     * @return categoryId -> count
     */
    List<Map<String, Object>> countGroupByCategory();

}
