package com.ming.northstar_backend.dto;

/**
 * 批量导入白名单的请求体。
 *
 * <pre>{@code
 * {
 *   "replace": false,
 *   "items": [
 *     { "qq": "123456789", "nickname": "明明", "remark": "一期内测" },
 *     { "qq": "987654321" }
 *   ]
 * }
 * }</pre>
 *
 * <p>只支持对象写法；如需按行导入纯文本 QQ 名单，请使用
 * {@code POST /api/admin/beta/whitelist/import-csv}。</p>
 */
public class BetaWhitelistImportRequest {

    /** true 表示先清空现有白名单再导入（危险，默认 false）。 */
    private Boolean replace = Boolean.FALSE;

    private java.util.List<BetaWhitelistRequest> items = java.util.List.of();

    public BetaWhitelistImportRequest() {
    }

    public Boolean getReplace() { return replace; }
    public void setReplace(Boolean replace) { this.replace = replace; }
    public java.util.List<BetaWhitelistRequest> getItems() { return items; }
    public void setItems(java.util.List<BetaWhitelistRequest> items) { this.items = items; }
}
