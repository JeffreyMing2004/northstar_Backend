package com.ming.northstar_backend.repository;

import com.ming.northstar_backend.entity.BetaWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BetaWhitelistRepository extends JpaRepository<BetaWhitelist, Long> {

    /**
     * 按 QQ 取全部条目。同一 QQ 可以绑定多个游戏 ID，因此返回列表而不是单条。
     *
     * <p>注意：不能用 {@code findAllByQq} 直接比较 {@code mcId} 是否为 null，
     * 空串与 null 都需要在服务层归一化后再比较。</p>
     */
    List<BetaWhitelist> findAllByQqOrderByCreatedAtAsc(String qq);

    List<BetaWhitelist> findAllByOrderByCreatedAtDesc();

    long countByStatus(Integer status);
}
