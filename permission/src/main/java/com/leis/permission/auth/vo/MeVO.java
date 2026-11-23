package com.leis.permission.auth.vo;

import java.util.Set;

public record MeVO (String id, String username, String deptId, boolean admin, Set<String> permissions){
}
