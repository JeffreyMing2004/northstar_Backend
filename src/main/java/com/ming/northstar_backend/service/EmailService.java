package com.ming.northstar_backend.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redis;
    private static final String CODE_PREFIX = "northstar:email:code:";
    private static final long CODE_TTL = 5;

    public EmailService(JavaMailSender mailSender, StringRedisTemplate redis) {
        this.mailSender = mailSender;
        this.redis = redis;
    }

    public void sendVerificationCode(String email) {
        String cooldownKey = CODE_PREFIX + "cooldown:" + email;
        if (Boolean.TRUE.equals(redis.hasKey(cooldownKey))) {
            throw new RuntimeException("\u53d1\u9001\u8fc7\u4e8e\u9891\u7e41\uff0c\u8bf760\u79d2\u540e\u518d\u8bd5");
        }

        String code = String.format("%06d", new Random().nextInt(1000000));

        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("northstar2026@yeah.net", "NorthStar MC\u7ade\u6280\u5e73\u53f0");
            helper.setTo(email);
            helper.setSubject("\u3010NorthStar\u3011\u90ae\u7bb1\u9a8c\u8bc1\u7801");
            helper.setText(buildHtml(code), true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("\u90ae\u4ef6\u53d1\u9001\u5931\u8d25: " + e.getMessage());
        }

        redis.opsForValue().set(CODE_PREFIX + email, code, Duration.ofMinutes(CODE_TTL));
        redis.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(60));
    }

    public boolean verifyCode(String email, String code) {
        if (email == null || code == null) return false;
        String stored = redis.opsForValue().get(CODE_PREFIX + email);
        if (stored != null && stored.equals(code)) {
            redis.delete(CODE_PREFIX + email);
            return true;
        }
        return false;
    }

    public void sendBetaApprovalEmail(String email, String displayName) {
        String name = displayName == null || displayName.isBlank() ? "玩家" : displayName.trim();
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("northstar2026@yeah.net", "NorthStar MC竞技平台");
            helper.setTo(email);
            helper.setSubject("【NorthStar】内测资格已通过");
            helper.setText(buildBetaApprovalHtml(name), true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("内测通知邮件发送失败: " + e.getMessage());
        }
    }

    private String buildHtml(String code) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"max-width:480px;margin:0 auto;background:#161a16;border:1px solid rgba(255,140,0,0.15);padding:40px;font-family:sans-serif;color:#e8e8e8;\">");
        sb.append("<div style=\"text-align:center;margin-bottom:32px;\">");
        sb.append("<div style=\"display:inline-block;width:56px;height:56px;background:#ff8c00;color:#000;font-weight:900;font-size:28px;line-height:56px;clip-path:polygon(50% 0%,100% 25%,100% 75%,50% 100%,0% 75%,0% 25%);font-family:monospace;\">N</div>");
        sb.append("<h1 style=\"margin:16px 0 0;font-size:22px;color:#fff;\">NorthStar \u90ae\u7bb1\u9a8c\u8bc1</h1></div>");
        sb.append("<p style=\"color:#8a8a8a;font-size:14px;line-height:1.8;\">\u4f60\u597d\uff0c\u4f60\u6b63\u5728\u6ce8\u518c NorthStar MC\u7ade\u6280\u5e73\u53f0 \u8d26\u53f7\u3002\u8bf7\u4f7f\u7528\u4ee5\u4e0b\u9a8c\u8bc1\u7801\u5b8c\u6210\u90ae\u7bb1\u9a8c\u8bc1\uff1a</p>");
        sb.append("<div style=\"text-align:center;margin:32px 0;\">");
        sb.append("<span style=\"display:inline-block;background:rgba(255,140,0,0.12);border:1px solid rgba(255,140,0,0.3);color:#ff8c00;font-size:32px;font-weight:800;letter-spacing:8px;padding:16px 32px;font-family:monospace;\">").append(code).append("</span></div>");
        sb.append("<p style=\"color:#555;font-size:12px;line-height:1.6;\">\u9a8c\u8bc1\u7801 <b style=\"color:#ff8c00;\">5 \u5206\u949f</b> \u5185\u6709\u6548\u3002\u5982\u679c\u8fd9\u4e0d\u662f\u4f60\u7684\u64cd\u4f5c\uff0c\u8bf7\u5ffd\u7565\u6b64\u90ae\u4ef6\u3002</p>");
        sb.append("<hr style=\"border:none;border-top:1px solid rgba(255,140,0,0.1);margin:24px 0;\">");
        sb.append("<p style=\"color:#555;font-size:11px;text-align:center;\">\u00a9 NorthStar MC\u7ade\u6280\u5e73\u53f0</p></div>");
        return sb.toString();
    }

    private String buildBetaApprovalHtml(String displayName) {
        return "<div style=\"max-width:520px;margin:0 auto;background:#161a16;border:1px solid rgba(255,140,0,0.15);padding:40px;font-family:sans-serif;color:#e8e8e8;\">"
            + "<div style=\"text-align:center;margin-bottom:32px;\">"
            + "<div style=\"display:inline-block;width:56px;height:56px;background:#ff8c00;color:#000;font-weight:900;font-size:28px;line-height:56px;font-family:monospace;\">N</div>"
            + "<h1 style=\"margin:16px 0 0;font-size:22px;color:#fff;\">NorthStar 内测资格已通过</h1></div>"
            + "<p style=\"color:#8a8a8a;font-size:14px;line-height:1.8;\">你好 "
            + escapeHtml(displayName)
            + "，你的 NorthStar MC竞技平台内测申请已通过。</p>"
            + "<div style=\"border:1px solid rgba(76,175,80,0.3);background:rgba(76,175,80,0.08);padding:18px;margin:24px 0;\">"
            + "<p style=\"margin:0 0 8px;color:#4caf50;font-weight:700;\">标准内测资格</p>"
            + "<p style=\"margin:0;color:#8a8a8a;font-size:13px;\">可体验模式：全部竞技模式</p>"
            + "<p style=\"margin:8px 0 0;color:#8a8a8a;font-size:13px;\">有效期至：2026-12-31</p></div>"
            + "<p style=\"color:#8a8a8a;font-size:14px;line-height:1.8;\">登录 NorthStar 后即可进入房间大厅参与内测。资格信息可在“内测资格”页面查询。</p>"
            + "<hr style=\"border:none;border-top:1px solid rgba(255,140,0,0.1);margin:24px 0;\">"
            + "<p style=\"color:#555;font-size:11px;text-align:center;\">© NorthStar MC竞技平台</p></div>";
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
