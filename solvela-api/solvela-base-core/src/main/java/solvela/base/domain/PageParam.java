package solvela.base.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.util.List;

/**
 * 分页基础参数
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2020/04/28 16:19
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Data
public class PageParam {

    /** 页码(不能为空) */
    @NotNull(message = "分页参数不能为空")
    private Long pageNum;

    /** 每页数量(不能为空) */
    @NotNull(message = "每页数量不能为空")
    @Max(value = 1000, message = "每页最大为1000")
    private Long pageSize;

    /** 是否查询总条数 */
    protected Boolean searchCount;

    /** 排序字段集合 */
    @Size(max = 10, message = "排序字段最多10")
    @Valid
    private List<SortItem> sortItemList;

    /**
     * 排序DTO类
     */
    @Data
    public static class SortItem {

        /** true正序|false倒序 */
        @NotNull(message = "排序规则不能为空")
        private Boolean isAsc;

        /** 排序字段 */
        @NotBlank(message = "排序字段不能为空")
        @Length(max = 30, message = "排序字段最多30")
        private String column;
    }
}
