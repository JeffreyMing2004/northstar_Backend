package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.BridgeRoomReport;
import com.ming.northstar_backend.entity.Room;
import com.ming.northstar_backend.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BridgeRoomSyncService {
    private final RoomRepository roomRepo;

    public BridgeRoomSyncService(RoomRepository roomRepo) {
        this.roomRepo = roomRepo;
    }

    @Transactional
    public void syncRooms(String serverId, List<BridgeRoomReport> reports) {
        validateReports(reports);

        Map<String, Room> existingRooms = roomRepo.findByServerId(serverId).stream()
                .filter(room -> room.getExternalId() != null)
                .collect(Collectors.toMap(Room::getExternalId, Function.identity()));

        Set<String> reportedIds = new HashSet<>();
        for (BridgeRoomReport report : reports) {
            reportedIds.add(report.getExternalId());
            Room room = existingRooms.get(report.getExternalId());
            if (room == null) {
                room = new Room();
                room.setServerId(serverId);
                room.setExternalId(report.getExternalId());
            }
            apply(room, report);
            roomRepo.save(room);
        }

        List<Room> removedRooms = existingRooms.values().stream()
                .filter(room -> !reportedIds.contains(room.getExternalId()))
                .toList();
        roomRepo.deleteAll(removedRooms);
        roomRepo.deleteAll(roomRepo.findByServerIdIsNull());
    }

    private void validateReports(List<BridgeRoomReport> reports) {
        Set<String> externalIds = new HashSet<>();
        for (BridgeRoomReport report : reports) {
            if (!externalIds.add(report.getExternalId())) {
                throw new IllegalArgumentException("插件提交了重复的房间 ID：" + report.getExternalId());
            }
            if (report.getCurrentPlayers() > report.getMaxPlayers()) {
                throw new IllegalArgumentException("房间人数不能超过最大人数：" + report.getExternalId());
            }
        }
    }

    private void apply(Room room, BridgeRoomReport report) {
        room.setName(report.getName());
        room.setMode(report.getMode());
        room.setModeKey(report.getModeKey());
        room.setMap(report.getMap());
        room.setMapKey(report.getMapKey());
        room.setHost(report.getHost());
        room.setMaxPlayers(report.getMaxPlayers());
        room.setCurrentPlayers(report.getCurrentPlayers());
        room.setPing(report.getPing());
        room.setStatus(report.getStatus());
    }
}
