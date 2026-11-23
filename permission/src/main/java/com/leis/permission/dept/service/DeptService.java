package com.leis.permission.dept.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.leis.common.common.BusinessException;
import com.leis.permission.auth.CurrentActor;
import com.leis.permission.auth.PermissionChecker;
import com.leis.permission.dept.entity.Dept;
import com.leis.permission.dept.mapper.DeptMapper;
import com.leis.permission.dept.vo.DeptCreateRequest;
import com.leis.permission.dept.vo.DeptUpdateRequest;
import com.leis.permission.dept.vo.DeptVO;
import com.leis.permission.log.service.AuditService;
import com.leis.permission.support.VersionGuard;
import com.leis.permission.user.entity.User;
import com.leis.permission.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DeptService {
    private final DeptMapper deptMapper;
    private final UserMapper userMapper;
    private final DeptQueryService query;
    private final CurrentActor current;
    private final PermissionChecker permissions;
    private final AuditService audit;


    /**
     * 按数据范围查出部门并补齐祖先以供建树，仅将范围内且祖先链启用的节点标为可选。
     */
    public List<DeptVO> list() {
        var actor = current.get();
        var all = deptMapper.selectList(new QueryWrapper<Dept>().orderByAsc("sort_order", "id"));
        var selectable = new HashSet<Long>(actor.deptIds());
        if (actor.includeSelf()) selectable.add(actor.deptId());
        if (actor.all()) all.forEach(row -> selectable.add(row.getId()));
        var visible = new HashSet<Long>(selectable);
        var byId = new HashMap<Long, Dept>();
        all.forEach(row -> byId.put(row.getId(), row));
        for (Long selected : selectable) {
            Dept row = byId.get(selected);
            var visited = new HashSet<Long>();
            while (row != null && row.getParentId() != null && visited.add(row.getId())) {
                visible.add(row.getParentId());
                row = byId.get(row.getParentId());
            }
        }
        return all.stream().filter(row -> visible.contains(row.getId())).map(row -> DeptVO.from(row,
                selectable.contains(row.getId()) && query.activeDept(row.getId()))).toList();
    }

    /**
     * 锁定组织树后校验父部门，创建启用部门并记录审计。
     */
    @Transactional
    public DeptVO create(DeptCreateRequest request) {
        permissions.requireAdmin();
        deptMapper.lockTree();
        validateParent(null, request.parentId());
        if (!query.activeDept(request.parentId())) {
            throw new BusinessException(400, "父部门需要启用");
        }
        Dept row = new Dept();
        row.setParentId(request.parentId());
        row.setName(request.name());
        row.setSortOrder(request.sortOrder());
        row.setStatus("ENABLED");
        row.setVersion(0);
        deptMapper.insert(row);
        audit.record("DEPT_CREATE", row.getId());
        return DeptVO.from(row, true);
    }


    /**
     * 校验版本、父子关系及停用条件，避免更改结构时影响仍在使用的部门。
     */
    @Transactional
    public DeptVO update(Long id, DeptUpdateRequest request) {
        permissions.requireAdmin();
        deptMapper.lockTree();
        Dept row = deptMapper.selectById(id);
        if (row == null) throw new BusinessException(404, "部门不存在");
        VersionGuard.check(row.getVersion(), request.version());
        validateParent(id, request.parentId());
        if (id.equals(1L) && !"ENABLED".equals(request.status())) {
            throw new BusinessException(400, "根部门保持启用");
        }
        if ("DISABLED".equals(request.status()) && userMapper.selectCount(new QueryWrapper<User>().in("dept_id",
                query.subtree(id)).eq("status", "ENABLED")) > 0) {
            throw new BusinessException(409, "部门子树仍有启用用户");
        }
        if ("ENABLED".equals(request.status()) && request.parentId() != null && !query.activeDept(request.parentId())) {
            throw new BusinessException(400, "父部门需要启用");
        }
        row.setParentId(request.parentId());
        row.setName(request.name());
        row.setSortOrder(request.sortOrder());
        row.setStatus(request.status());
        VersionGuard.updated(deptMapper.updateById(row));
        audit.record("DEPT_UPDATE", id);
        return DeptVO.from(row, query.activeDept(id));
    }

    /**
     * 锁定组织树后删除非根且无子部门的节点，数据库约束继续检查在用引用。
     */
    @Transactional
    public void delete(Long id, Integer version) {
        permissions.requireAdmin();
        deptMapper.lockTree();
        Dept row = deptMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(404, "部门不存在");
        }
        VersionGuard.check(row.getVersion(), version);
        if (id.equals(1L)) {
            throw new BusinessException(400, "根部门不能删除");
        }
        if (deptMapper.selectCount(new QueryWrapper<Dept>().eq("parent_id", id)) > 0) {
            throw new BusinessException(409, "请先删除子部门");
        }
        VersionGuard.updated(deptMapper.delete(new QueryWrapper<Dept>().eq("id", id).eq("version", version)));
        audit.record("DEPT_DELETE", id);
    }

    /**
     * 校验上级部门存在，并约束根节点位置以及自身或后代不能成为父级。
     */
    private void validateParent(Long id, Long parentId) {
        if (Objects.equals(id, 1L)) {
            if (parentId != null) {
                throw new BusinessException(400, "根部门保持根节点");
            }
            return;
        }
        if (parentId == null || deptMapper.selectById(parentId) == null) {
            throw new BusinessException(400, "请选择父部门");
        }
        if (id != null && query.subtree(id).contains(parentId)) {
            throw new BusinessException(400, "不能选择自身或后代");
        }
    }
}
