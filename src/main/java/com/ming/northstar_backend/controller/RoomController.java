package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.service.RoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoomDto>>> listRooms(
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(roomService.listRooms(mode, status)));
    }

    @GetMapping("/hot")
    public ResponseEntity<ApiResponse<List<RoomDto>>> getHotRooms() {
        return ResponseEntity.ok(ApiResponse.ok(roomService.getHotRooms()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoomDto>> createRoom(
            Authentication auth, @RequestBody CreateRoomRequest req) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.ok("房间创建成功", roomService.createRoom(userId, req)));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<ApiResponse<RoomDto>> joinRoom(Authentication auth, @PathVariable Long id) {
        Long userId = (Long) auth.getPrincipal();
        try {
            return ResponseEntity.ok(ApiResponse.ok("加入成功", roomService.joinRoom(id, userId)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}