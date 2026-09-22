package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.CreateRoomRequest;
import com.ming.northstar_backend.dto.RoomDto;
import com.ming.northstar_backend.entity.Room;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.RoomRepository;
import com.ming.northstar_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoomService {

    private final RoomRepository roomRepo;
    private final UserRepository userRepo;

    public RoomService(RoomRepository roomRepository, UserRepository userRepository) {
        this.roomRepo = roomRepository;
        this.userRepo = userRepository;
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
            .filter(room -> !"closed".equals(room.getStatus()))
            .map(RoomDto::from)
            .collect(Collectors.toList());
    }

    public List<RoomDto> getHotRooms() {
        return roomRepo.findTop4ByStatusOrderByCurrentPlayersDesc("playing")
                .stream().map(RoomDto::from).collect(Collectors.toList());
    }

    public RoomDto createRoom(Long userId, CreateRoomRequest req) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        Room room = new Room();
        room.setName(req.getName());
        room.setMode(req.getMode());
        room.setModeKey(req.getModeKey());
        room.setMap(req.getMap());
        room.setMapKey(req.getMapKey());
        room.setMaxPlayers(req.getMaxPlayers());
        room.setHost(user.getUsername());
        room.setHostUserId(userId);
        room.setCurrentPlayers(1);
        room = roomRepo.save(room);
        return RoomDto.from(room);
    }

    public RoomDto joinRoom(Long roomId, Long userId) {
        Room room = roomRepo.findById(roomId)
            .orElseThrow(() -> new RuntimeException("房间不存在"));
        if ("closed".equals(room.getStatus())) {
            throw new RuntimeException("房间已关闭");
        }
        if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
            throw new RuntimeException("房间已满");
        }
        room.setCurrentPlayers(room.getCurrentPlayers() + 1);
        room = roomRepo.save(room);
        return RoomDto.from(room);
    }
}
