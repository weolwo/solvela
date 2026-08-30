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
     * 各分类的文件数。<b>一次 GROUP BY 出全部</b>，不是每个分类查一次 ——
     * 分类卡片页一次要展示几十张卡，逐个 count 就是 N+1，而这是运营每天进的页面。
     *
     * @return categoryId -> count
     */
    List<Map<String, Object>> countGroupByCategory();

}
