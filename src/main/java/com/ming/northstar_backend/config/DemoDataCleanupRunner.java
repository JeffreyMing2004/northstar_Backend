package com.ming.northstar_backend.config;

import com.ming.northstar_backend.entity.Room;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.BetaApplicationRepository;
import com.ming.northstar_backend.repository.MatchRecordRepository;
import com.ming.northstar_backend.repository.RoomRepository;
import com.ming.northstar_backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DemoDataCleanupRunner implements CommandLineRunner {
    private static final String DEMO_PASSWORD = "123456";
    private static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser("DarkKnight_MC", "dark@mc.com"),
            new DemoUser("PixelStorm", "pixel@mc.com"),
            new DemoUser("BlockHunter", "block@mc.com"),
            new DemoUser("EndWarrior", "end@mc.com"),
            new DemoUser("CreepMaster", "creep@mc.com"),
            new DemoUser("NetherKing", "nether@mc.com"),
            new DemoUser("VoidWalker", "void@mc.com"),
            new DemoUser("DiamondAce", "diamond@mc.com"),
            new DemoUser("Redstone_Pro", "red@mc.com"),
            new DemoUser("SkyfallMC", "sky@mc.com")
    );
    private static final List<DemoRoom> DEMO_ROOMS = List.of(
            new DemoRoom("午夜竞技场 - 高手进", "tdm", "desert", "DarkKnight_MC"),
            new DemoRoom("像素风暴 - 娱乐局", "dom", "end", "PixelStorm"),
            new DemoRoom("钢铁丛林 - 排位赛", "br", "jungle", "BlockHunter"),
            new DemoRoom("极地突袭 - 新手友好", "ctf", "ice", "EndWarrior"),
            new DemoRoom("暗影之夜 - 竞速赛", "tdm", "nether", "CreepMaster"),
            new DemoRoom("沙漠风暴 - 随意玩", "dom", "desert", "NetherKing")
    );

    private final UserRepository userRepo;
    private final RoomRepository roomRepo;
    private final MatchRecordRepository matchRepo;
    private final BetaApplicationRepository betaRepo;
    private final PasswordEncoder passwordEncoder;

    public DemoDataCleanupRunner(
            UserRepository userRepo,
            RoomRepository roomRepo,
            MatchRecordRepository matchRepo,
            BetaApplicationRepository betaRepo,
            PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.roomRepo = roomRepo;
        this.matchRepo = matchRepo;
        this.betaRepo = betaRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int deletedUsers = cleanupDemoUsers();
        int deletedRooms = cleanupDemoRooms();
        if (deletedUsers > 0 || deletedRooms > 0) {
            System.out.println("[NorthStar] Demo data removed: "
                    + deletedUsers + " users, " + deletedRooms + " rooms");
        }
    }

    private int cleanupDemoUsers() {
        int deleted = 0;
        for (DemoUser demoUser : DEMO_USERS) {
            List<User> users = userRepo.findByUsernameAndEmail(demoUser.username(), demoUser.email()).stream()
                    .filter(user -> passwordEncoder.matches(DEMO_PASSWORD, user.getPassword()))
                    .toList();
            for (User user : users) {
                matchRepo.deleteByUserId(user.getId());
                betaRepo.deleteByUserId(user.getId());
                userRepo.delete(user);
                deleted++;
            }
        }
        return deleted;
    }

    private int cleanupDemoRooms() {
        int deleted = 0;
        for (DemoRoom demoRoom : DEMO_ROOMS) {
            List<Room> rooms = roomRepo.findByName(demoRoom.name()).stream()
                    .filter(demoRoom::matches)
                    .toList();
            roomRepo.deleteAll(rooms);
            deleted += rooms.size();
        }
        return deleted;
    }

    private record DemoUser(String username, String email) {
    }

    private record DemoRoom(String name, String modeKey, String mapKey, String host) {
        boolean matches(Room room) {
            return modeKey.equals(room.getModeKey())
                    && mapKey.equals(room.getMapKey())
                    && host.equalsIgnoreCase(room.getHost());
        }
    }
}
