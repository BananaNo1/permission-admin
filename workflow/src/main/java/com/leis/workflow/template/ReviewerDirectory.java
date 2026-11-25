package com.leis.workflow.template;

import com.leis.common.common.BusinessException;
import com.leis.workflow.access.BusinessAccess;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ReviewerDirectory {
    public record Reviewer(long id, String name, String username, boolean enabled) {}

    private final BusinessAccess access;
    private final NamedParameterJdbcTemplate db;

    public ReviewerDirectory(BusinessAccess access, NamedParameterJdbcTemplate db) {
        this.access = access;
        this.db = db;
    }

    public List<Reviewer> find(String business, String keyword, List<Long> ids) {
        access.require(business, BusinessAccess.Capability.VIEW_DRAFT);
        if (keyword.length() > 64 || (ids != null && ids.size() > 50)) {
            throw new BusinessException(400, "审批人查询参数超出范围");
        }
        String base = "select id,nickname,username,status from sys_user";
        String order = " order by nickname,username,id";
        String sql;
        Map<String, ?> parameters;
        if (ids != null && !ids.isEmpty()) {
            sql = base + " where id in (:ids)" + order;
            parameters = Map.of("ids", ids);
        } else {
            sql = base + """
                     where status='ENABLED' and (position(lower(:keyword) in lower(nickname)) > 0
                         or position(lower(:keyword) in lower(username)) > 0)
                    """ + order + " limit 30";
            parameters = Map.of("keyword", keyword.strip());
        }
        return db.query(sql, parameters, (row, index) -> new Reviewer(row.getLong("id"),
                row.getString("nickname"), row.getString("username"), "ENABLED".equals(row.getString("status"))));
    }
}
