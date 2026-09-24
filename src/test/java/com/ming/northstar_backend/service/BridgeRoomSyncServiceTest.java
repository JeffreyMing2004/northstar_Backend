package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.BridgeRoomReport;
import com.ming.northstar_backend.entity.Room;
import com.ming.northstar_backend.repository.RoomRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BridgeRoomSyncServiceTest {

    @Test
    void replacesTheSubmittedServersRoomSnapshot() {
        RoomRepository roomRepo = mock(RoomRepository.class);
        Room staleRoom = room("stale");
        Room updatedRoom = room("arena-1");
        when(roomRepo.findByServerId("server-1")).thenReturn(List.of(staleRoom, updatedRoom));

        BridgeRoomSyncService service = new BridgeRoomSyncService(roomRepo);
        service.syncRooms("server-1", List.of(report("arena-1", 4)));

        assertEquals("Arena One", updatedRoom.getName());
        assertEquals(4, updatedRoom.getCurrentPlayers());
        verify(roomRepo).save(updatedRoom);
        verify(roomRepo).deleteAll(List.of(staleRoom));
    }

    @Test
    void rejectsPlayersAboveRoomCapacity() {
        RoomRepository roomRepo = mock(RoomRepository.class);
        when(roomRepo.findByServerId("server-1")).thenReturn(List.of());
        BridgeRoomSyncService service = new BridgeRoomSyncService(roomRepo);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.syncRooms("server-1", List.of(report("arena-1", 5)))
        );
    }

    private BridgeRoomReport report(String externalId, int currentPlayers) {
        BridgeRoomReport report = new BridgeRoomReport();
        report.setExternalId(externalId);
        report.setName("Arena One");
        report.setMode("Team Deathmatch");
        report.setModeKey("tdm");
        report.setMap("Desert");
        report.setMapKey("desert");
        report.setHost("NorthStar");
        report.setMaxPlayers(4);
        report.setCurrentPlayers(currentPlayers);
        report.setPing(20);
        report.setStatus(currentPlayers > 0 ? "playing" : "waiting");
        return report;
    }

    private Room room(String externalId) {
        Room room = new Room();
        room.setServerId("server-1");
        room.setExternalId(externalId);
        room.setName("Old Name");
        room.setMaxPlayers(8);
        room.setCurrentPlayers(0);
        return room;
    }
}
