package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.service.RoomService;
import org.springframework.http.ResponseEntity;
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

}
