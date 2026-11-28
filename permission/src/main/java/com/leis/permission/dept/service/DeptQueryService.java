package com.leis.permission.dept.service;

import com.leis.common.common.BusinessException;
import com.leis.permission.dept.mapper.DeptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 查询部门子树和上级链，为数据范围与部门有效性判断提供支持。
 */
@Service
@RequiredArgsConstructor
public class DeptQueryService {
    private final DeptMapper deptMapper;

    /**
     * 按层收集根部门及全部后代的 ID，遇到重复节点时拒绝循环结构。
     */
    public Set<Long> subtree(Long root) {
        var result = new HashSet<Long>();
        var all = deptMapper.selectList(null);
        var queue = new ArrayDeque<Long>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Long next = queue.remove();
            if (!result.add(next)) {
                throw new BusinessException(409, "部门树存在循环");
            }
            all.stream().filter(row -> Objects.equals(row.getParentId(), next))
                    .forEach(row -> queue.add(row.getId()));
        }
        return result;
    }

    /**
     * 沿父级链检查部门状态，缺失、禁用或循环的节点链均无法通过。
     */
    public boolean activeDept(Long id) {
        var visited = new HashSet<Long>();
        while (id != null) {
            if (!visited.add(id)) {
                return false;
            }
            var row = deptMapper.selectById(id);
            if (row == null || !"ENABLED".equals(row.getStatus())) {
                return false;
            }
            id = row.getParentId();
        }
        return true;
    }

    /** 查询启用部门配置的负责人标识。 */
    public Long managerId(Long deptId) {
        if (deptId == null || !activeDept(deptId)) {
            return null;
        }
        var dept = deptMapper.selectById(deptId);
        return dept == null ? null : dept.getManagerUserId();
    }
}
