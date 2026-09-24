package com.ming.northstar_backend.repository;

import com.ming.northstar_backend.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByStatus(String status);
    List<Room> findByModeKey(String modeKey);
    List<Room> findByStatusAndModeKey(String status, String modeKey);
    List<Room> findTop4ByStatusOrderByCurrentPlayersDesc(String status);
    List<Room> findAllByOrderByCreatedAtDesc();
    List<Room> findByName(String name);
    List<Room> findByServerId(String serverId);
    List<Room> findByServerIdIsNull();
    long countByStatusIn(List<String> statuses);
}
