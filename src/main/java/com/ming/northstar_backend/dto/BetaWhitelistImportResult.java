package com.ming.northstar_backend.dto;

import java.util.ArrayList;
import java.util.List;

/** 批量导入白名单的结果统计。 */
public class BetaWhitelistImportResult {

    /** 新增条数。 */
    private int created;
    /** 覆盖更新条数（QQ 已存在）。 */
    private int updated;
    /** 跳过条数（如重复行、内容为空）。 */
    private int skipped;
    /** 失败条数（格式非法）。 */
    private int failed;
    /** 失败明细，最多保留 50 条，格式 {@code 第3行(qq=abc): QQ 号格式无效}。 */
    private List<String> errors = new ArrayList<>();

    public void addError(int line, String qq, String message) {
        failed++;
        if (errors.size() < 50) {
            errors.add("第" + line + "行(qq=" + qq + "): " + message);
        }
    }

    public int getCreated() { return created; }
    public void setCreated(int created) { this.created = created; }
    public void setUpdated(int updated) { this.updated = updated; }
    public int getUpdated() { return updated; }
    public int getSkipped() { return skipped; }
    public void setSkipped(int skipped) { this.skipped = skipped; }
    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }
    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
}
