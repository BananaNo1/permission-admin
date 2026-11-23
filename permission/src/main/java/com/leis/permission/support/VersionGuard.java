package com.leis.permission.support;


import com.leis.common.common.BusinessException;

import java.util.Objects;

public final class VersionGuard {

    /**
     * 禁止实例化版本校验工具类。
     */
    private VersionGuard() {}

    /**
     * 比较提交版本与当前记录版本，缺失或不一致时返回 409。
     */
    public static void check(Integer current, Integer expected) {
        if (expected == null || !Objects.equals(current, expected))
            throw new BusinessException(409, "记录已变化，请重新加载后提交");
    }

    /**
     * 要求写操作恰好影响一行，否则按记录冲突返回 409。
     */
    public static void updated(int rows) {
        if (rows != 1) throw new BusinessException(409, "记录已变化，请重新加载后提交");
    }
}
