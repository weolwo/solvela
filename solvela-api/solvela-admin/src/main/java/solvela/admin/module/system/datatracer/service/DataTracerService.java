package solvela.admin.module.system.datatracer.service;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import solvela.base.common.domain.PageResult;
import solvela.base.common.domain.RequestUser;
import solvela.base.common.domain.ResponseDTO;
import solvela.base.common.util.SolvelaBeanUtil;
import solvela.base.common.util.SolvelaCollectionUtil;
import solvela.base.common.util.SolvelaIpUtil;
import solvela.base.common.util.SolvelaPageUtil;
import solvela.base.common.util.SolvelaRequestUtil;
import solvela.admin.module.system.datatracer.constant.DataTracerConst;
import solvela.admin.module.system.datatracer.constant.DataTracerTypeEnum;
import solvela.admin.module.system.datatracer.dao.DataTracerDao;
import solvela.admin.module.system.datatracer.domain.entity.DataTracerEntity;
import solvela.admin.module.system.datatracer.domain.form.DataTracerForm;
import solvela.admin.module.system.datatracer.domain.form.DataTracerQueryForm;
import solvela.admin.module.system.datatracer.domain.vo.DataTracerVO;
import solvela.admin.module.system.datatracer.manager.DataTracerManger;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据变动记录 Service
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-07-23 19:38:52
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Slf4j
@Service
public class DataTracerService {

    @Resource
    private DataTracerDao dataTracerDao;

    @Resource
    private DataTracerManger dataTracerManger;

    @Resource
    private DataTracerChangeContentService dataTracerChangeContentService;

    /**
     * 获取变更内容
     *
     * @param object
     * @return
     */
    public String getChangeContent(Object object) {
        return dataTracerChangeContentService.getChangeContent(object);
    }

    /**
     * 获取变更内容
     */
    public String getChangeContent(Object oldObject, Object newObject) {
        return dataTracerChangeContentService.getChangeContent(oldObject, newObject);
    }


    /**
     * 获取变更内容
     */
    public <T> String getChangeContent(List<T> oldObjectList, List<T> newObjectList) {
        return dataTracerChangeContentService.getChangeContent(oldObjectList, newObjectList);
    }


    /**
     * 保存【修改】数据变动记录
     *
     * @param dataId
     * @param type
     */
    public void update(Long dataId, DataTracerTypeEnum type, Object oldObject, Object newObject) {
        DataTracerForm form = DataTracerForm.builder()
                .dataId(dataId)
                .type(type)
                .content(dataTracerChangeContentService.getChangeContent(oldObject, newObject))
                .build();
        this.addTrace(form);
    }


    /**
     * 保存【新增】数据变动记录
     *
     * @param dataId
     * @param type
     */
    public void insert(Long dataId, DataTracerTypeEnum type) {
        DataTracerForm form = DataTracerForm.builder().dataId(dataId).type(type).content(DataTracerConst.INSERT).build();
        this.addTrace(form);
    }

    /**
     * 保存【删除】数据变动记录
     *
     * @param dataId
     * @param type
     */
    public void delete(Long dataId, DataTracerTypeEnum type) {
        DataTracerForm form = DataTracerForm.builder().dataId(dataId).type(type).content(DataTracerConst.DELETE).build();
        this.addTrace(form);
    }

    /**
     * 保存【删除】数据变动记录
     *
     * @param dataId
     * @param type
     */
    public void delete(Long dataId, DataTracerTypeEnum type, Object object) {
        DataTracerForm form = DataTracerForm.builder().dataId(dataId).type(type).content(DataTracerConst.DELETE).build();
        this.addTrace(form);
    }

    /**
     * 保存【批量删除】数据变动记录
     *
     * @param dataIdList
     * @param type
     */
    public void batchDelete(List<Long> dataIdList, DataTracerTypeEnum type) {
        if (SolvelaCollectionUtil.isEmpty(dataIdList)) {
            return;
        }

        this.addTraceList(dataIdList.stream().map(e -> DataTracerForm.builder()
                        .dataId(e)
                        .type(type)
                        .content(DataTracerConst.DELETE)
                        .build())
                .collect(Collectors.toList())
        );
    }

    /**
     * 保存数据变动记录
     *
     * @param dataId
     * @param type
     * @param content
     */
    public void addTrace(Long dataId, DataTracerTypeEnum type, String content) {
        DataTracerForm form = DataTracerForm.builder().dataId(dataId).type(type).content(content).build();
        this.addTrace(form);
    }

    /**
     * 保存数据变动记录
     */
    public void addTrace(DataTracerForm tracerForm) {
        RequestUser requestUser = SolvelaRequestUtil.getRequestUser();
        this.addTrace(tracerForm, requestUser);
    }


    /**
     * 保存数据变动记录
     */
    public void addTrace(DataTracerForm tracerForm, RequestUser requestUser) {
        DataTracerEntity tracerEntity = SolvelaBeanUtil.copy(tracerForm, DataTracerEntity.class);
        tracerEntity.setType(tracerForm.getType().getValue());
        if (requestUser != null) {
            tracerEntity.setIp(requestUser.getIp());
            tracerEntity.setIpRegion(SolvelaIpUtil.getRegion(requestUser.getIp()));
            tracerEntity.setUserAgent(requestUser.getUserAgent());
            tracerEntity.setUserId(requestUser.getUserId());
            tracerEntity.setUserType(requestUser.getUserType().getValue());
            tracerEntity.setUserName(requestUser.getUserName());
        }
        dataTracerManger.save(tracerEntity);
    }

    /**
     * 批量保存数据变动记录
     */
    public void addTraceList(List<DataTracerForm> tracerFormList) {
        RequestUser requestUser = SolvelaRequestUtil.getRequestUser();
        this.addTraceList(tracerFormList, requestUser);
    }

    /**
     * 批量保存数据变动记录
     */
    public void addTraceList(List<DataTracerForm> tracerFormList, RequestUser requestUser) {
        if (SolvelaCollectionUtil.isEmpty(tracerFormList)) {
            return;
        }

        List<DataTracerEntity> tracerEntityList = tracerFormList.stream().map(e -> {
            DataTracerEntity tracerEntity = SolvelaBeanUtil.copy(e, DataTracerEntity.class);
            tracerEntity.setType(e.getType().getValue());
            tracerEntity.setIp(requestUser.getIp());
            tracerEntity.setIpRegion(SolvelaIpUtil.getRegion(requestUser.getIp()));
            tracerEntity.setUserAgent(requestUser.getUserAgent());
            tracerEntity.setUserId(requestUser.getUserId());
            tracerEntity.setUserType(requestUser.getUserType().getValue());
            tracerEntity.setUserName(requestUser.getUserName());
            return tracerEntity;
        }).collect(Collectors.toList());
        dataTracerManger.saveBatch(tracerEntityList);
    }


    /**
     * 分页查询
     *
     * @param queryForm
     * @return
     */
    public ResponseDTO<PageResult<DataTracerVO>> query(DataTracerQueryForm queryForm) {
        Page page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<DataTracerVO> list = dataTracerDao.query(page, queryForm);
        PageResult<DataTracerVO> pageResult = SolvelaPageUtil.convert2PageResult(page, list);
        return ResponseDTO.ok(pageResult);
    }

}
