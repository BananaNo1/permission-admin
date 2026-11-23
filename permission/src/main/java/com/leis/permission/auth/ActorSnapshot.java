package com.leis.permission.auth;

import java.util.Set;

public record ActorSnapshot(Long id, String username, Long deptId, boolean admin,
                            Set<String> permissions, boolean all,
                            Set<Long> deptIds, boolean includeSelf) {
}