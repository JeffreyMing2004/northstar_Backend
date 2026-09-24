package com.ming.northstar_backend.config;

import com.ming.northstar_backend.entity.Room;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.BetaApplicationRepository;
import com.ming.northstar_backend.repository.MatchRecordRepository;
import com.ming.northstar_backend.repository.RoomRepository;
import com.ming.northstar_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoDataCleanupRunnerTest {

    @Test
    void removesOnlyAccountsMatchingUsernameEmailAndPassword() {
        UserRepository userRepo = mock(UserRepository.class);
        RoomRepository roomRepo = mock(RoomRepository.class);
        MatchRecordRepository matchRepo = mock(MatchRecordRepository.class);
        BetaApplicationRepository betaRepo = mock(BetaApplicationRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        User demoUser = user(10L, "encrypted-demo-password");
        User changedUser = user(11L, "real-password");
        when(userRepo.findByUsernameAndEmail("DarkKnight_MC", "dark@mc.com"))
                .thenReturn(List.of(demoUser, changedUser));
        when(passwordEncoder.matches("123456", demoUser.getPassword())).thenReturn(true);
        when(passwordEncoder.matches("123456", changedUser.getPassword())).thenReturn(false);
        when(userRepo.findByUsernameAndEmail("PixelStorm", "pixel@mc.com")).thenReturn(List.of());
        when(userRepo.findByUsernameAndEmail("BlockHunter", "block@mc.com")).thenReturn(List.of());
        when(userRepo.findByUsernameAndEmail("EndWarrior", "end@mc.com")).thenReturn(List.of());
        when(userRepo.findByUsernameAndEmail("CreepMaster", "creep@mc.com")).thenReturn(List.of());
        when(userRepo.findByUsernameAndEmail("NetherKing", "nether@mc.com")).thenReturn(List.of());
        when(userRepo.findByUsernameAndEmail("VoidWalker", "void@mc.com")).thenReturn(List.of());
        when(userRepo.findByUsernameAndEmail("DiamondAce", "diamond@mc.com")).thenReturn(List.of());
        when(userRepo.findByUsernameAndEmail("Redstone_Pro", "red@mc.com")).thenReturn(List.of());
        when(userRepo.findByUsernameAndEmail("SkyfallMC", "sky@mc.com")).thenReturn(List.of());

        Room demoRoom = room("tdm", "desert", "DarkKnight_MC");
        when(roomRepo.findByName("午夜竞技场 - 高手进")).thenReturn(List.of(demoRoom));
        when(roomRepo.findByName("像素风暴 - 娱乐局")).thenReturn(List.of());
        when(roomRepo.findByName("钢铁丛林 - 排位赛")).thenReturn(List.of());
        when(roomRepo.findByName("极地突袭 - 新手友好")).thenReturn(List.of());
        when(roomRepo.findByName("暗影之夜 - 竞速赛")).thenReturn(List.of());
        when(roomRepo.findByName("沙漠风暴 - 随意玩")).thenReturn(List.of());

        DemoDataCleanupRunner runner = new DemoDataCleanupRunner(
                userRepo, roomRepo, matchRepo, betaRepo, passwordEncoder
        );
        runner.run();

        verify(matchRepo).deleteByUserId(10L);
        verify(betaRepo).deleteByUserId(10L);
        verify(userRepo).delete(demoUser);
        verify(userRepo, never()).delete(changedUser);
        verify(roomRepo).deleteAll(List.of(demoRoom));
    }

    private User user(Long id, String password) {
        User user = new User();
        user.setId(id);
        user.setUsername("DarkKnight_MC");
        user.setEmail("dark@mc.com");
        user.setPassword(password);
        return user;
    }

    private Room room(String modeKey, String mapKey, String host) {
        Room room = new Room();
        room.setName("午夜竞技场 - 高手进");
        room.setModeKey(modeKey);
        room.setMapKey(mapKey);
        room.setHost(host);
        return room;
    }
}
