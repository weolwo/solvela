package solvela.admin.module.system.role.service;

import solvela.exception.BusinessException;
import jakarta.annotation.Resource;
import solvela.admin.module.system.role.dao.RoleDao;
import solvela.admin.module.system.role.dao.RoleEmployeeDao;
import solvela.admin.module.system.role.dao.RoleMenuDao;
import solvela.admin.module.system.role.domain.entity.RoleEntity;
import solvela.admin.module.system.role.domain.form.RoleAddForm;
import solvela.admin.module.system.role.domain.form.RoleUpdateForm;
import solvela.admin.module.system.role.domain.vo.RoleVO;
import solvela.code.UserErrorCode;
import solvela.base.util.SolvelaBeanUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色
 *
 * @Author 1024创新实验室: 胡克
 * @Date 2021-08-16 20:19:22
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Service
public class RoleService {

    @Resource
    private RoleDao roleDao;

    @Resource
    private RoleMenuDao roleMenuDao;

    @Resource
    private RoleEmployeeDao roleEmployeeDao;

    /**
     * 新增添加角色
     */
    public void addRole(RoleAddForm roleAddForm) {
        RoleEntity existRoleEntity = roleDao.getByRoleName(roleAddForm.getRoleName());
        if (null != existRoleEntity) {
            throw new BusinessException("角色名称重复");
        }

        existRoleEntity = roleDao.getByRoleCode(roleAddForm.getRoleCode());
        if (null != existRoleEntity) {
            throw new BusinessException("角色编码重复，重复的角色为：" + existRoleEntity.getRoleName());
        }

        RoleEntity roleEntity = SolvelaBeanUtil.copy(roleAddForm, RoleEntity.class);
        roleDao.insert(roleEntity);
    }

    /**
     * 根据角色id 删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId) {
        RoleEntity roleEntity = roleDao.selectById(roleId);
        if (null == roleEntity) {
            throw new BusinessException(UserErrorCode.DATA_NOT_EXIST);
        }
        // 当没有员工绑定这个角色时才可以删除
        Integer exists = roleEmployeeDao.existsByRoleId(roleId);
        if (exists != null) {
            throw new BusinessException(UserErrorCode.ALREADY_EXIST, "该角色下存在员工，无法删除");
        }
        roleDao.deleteById(roleId);
        roleMenuDao.deleteByRoleId(roleId);
        roleEmployeeDao.deleteByRoleId(roleId);
    }

    /**
     * 更新角色
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(RoleUpdateForm roleUpdateForm) {
        if (null == roleDao.selectById(roleUpdateForm.getRoleId())) {
            throw new BusinessException(UserErrorCode.DATA_NOT_EXIST);
        }

        RoleEntity existRoleEntity = roleDao.getByRoleName(roleUpdateForm.getRoleName());
        if (null != existRoleEntity && !existRoleEntity.getRoleId().equals(roleUpdateForm.getRoleId())) {
            throw new BusinessException("角色名称重复");
        }

        existRoleEntity = roleDao.getByRoleCode(roleUpdateForm.getRoleCode());
        if (null != existRoleEntity && !existRoleEntity.getRoleId().equals(roleUpdateForm.getRoleId())) {
            throw new BusinessException("角色编码重复，重复的角色为：" + existRoleEntity.getRoleName());
        }

        RoleEntity roleEntity = SolvelaBeanUtil.copy(roleUpdateForm, RoleEntity.class);
        roleDao.updateById(roleEntity);
    }

    /**
     * 根据id获取角色数据
     */
    public RoleVO getRoleById(Long roleId) {
        RoleEntity roleEntity = roleDao.selectById(roleId);
        if (null == roleEntity) {
            throw new BusinessException(UserErrorCode.DATA_NOT_EXIST);
        }
        RoleVO role = SolvelaBeanUtil.copy(roleEntity, RoleVO.class);
        return role;
    }

    /**
     * 获取所有角色列表
     */
    public List<RoleVO> getAllRole() {
        List<RoleEntity> roleEntityList = roleDao.selectList(null);
        List<RoleVO> roleList = SolvelaBeanUtil.copyList(roleEntityList, RoleVO.class);
        return roleList;
    }
}
