package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.RoomDto;
import com.ming.northstar_backend.entity.Room;
import com.ming.northstar_backend.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoomService {

    private final RoomRepository roomRepo;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepo = roomRepository;
    }

    public List<RoomDto> listRooms(String mode, String status) {
        List<Room> rooms;
        if (mode != null && status != null) {
            rooms = roomRepo.findByStatusAndModeKey(status, mode);
        } else if (mode != null) {
            rooms = roomRepo.findByModeKey(mode);
        } else if (status != null) {
            rooms = roomRepo.findByStatus(status);
        } else {
            rooms = roomRepo.findAll();
        }
        return rooms.stream()
            .filter(this::isBridgeRoom)
            .filter(room -> !"closed".equals(room.getStatus()))
            .map(RoomDto::from)
            .collect(Collectors.toList());
    }

    public List<RoomDto> getHotRooms() {
        return roomRepo.findTop4ByStatusOrderByCurrentPlayersDesc("playing")
                .stream()
                .filter(this::isBridgeRoom)
                .map(RoomDto::from)
                .collect(Collectors.toList());
    }

    private boolean isBridgeRoom(Room room) {
        return room.getServerId() != null && room.getExternalId() != null;
    }
}
