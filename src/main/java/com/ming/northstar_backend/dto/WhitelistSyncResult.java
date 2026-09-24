package com.ming.northstar_backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 「按账号重建白名单」的对账结果。
 *
 * <p>用于把两条不变式重新执行一遍：已获批的账号必须有白名单条目，
 * 已失去资格的账号不能还留着系统同步的条目。人工录入的条目完全不参与。</p>
 */
public class WhitelistSyncResult {

    /** 当前处于「已通过」状态的账号数。 */
    private int approvedPlayers;

    /** 新建的条目数。 */
    private int created;

    /** 因 QQ / 游戏 ID 变化而重建的条目数。 */
    private int refreshed;

    /** 本来就一致的条目数。 */
    private int unchanged;

    /** 同步失败的账号数（缺 QQ 或格式非法）。 */
    private int failed;

    /** 清理掉的孤儿条目数（账号已无内测资格）。 */
    private int removed;

    /** 失败明细，最多保留 20 条，便于在后台直接看到是哪几个玩家。 */
    private final List<String> problems = new ArrayList<>();

    public void countApprovedPlayer() {
        approvedPlayers++;
    }

    public void countCreated() {
        created++;
    }

    public void countRefreshed() {
        refreshed++;
    }

    public void countUnchanged() {
        unchanged++;
    }

    /** 记一条失败明细；每调用一次失败数 +1。 */
    public void addProblem(String detail) {
        failed++;
        if (problems.size() < 20) {
            problems.add(detail);
        }
    }

    public void setRemoved(int removed) {
        this.removed = removed;
    }

    public int getApprovedPlayers() { return approvedPlayers; }
    public int getCreated() { return created; }
    public int getRefreshed() { return refreshed; }
    public int getUnchanged() { return unchanged; }
    public int getFailed() { return failed; }
    public int getRemoved() { return removed; }
    public List<String> getProblems() { return problems; }
}
