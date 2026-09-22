package com.ming.northstar_backend.config;

import com.ming.northstar_backend.entity.Room;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.entity.MatchRecord;
import com.ming.northstar_backend.repository.RoomRepository;
import com.ming.northstar_backend.repository.UserRepository;
import com.ming.northstar_backend.repository.MatchRecordRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepo;
    private final RoomRepository roomRepo;
    private final MatchRecordRepository matchRepo;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository userRepo, RoomRepository roomRepo,
                           MatchRecordRepository matchRepo, PasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.roomRepo = roomRepo;
        this.matchRepo = matchRepo;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (userRepo.count() > 0) return;
        Random rng = new Random(42);

        String[][] players = {
            {"DarkKnight_MC", "dark@mc.com", "2847", "钻石III", "approved"},
            {"PixelStorm", "pixel@mc.com", "2756", "钻石II", "approved"},
            {"BlockHunter", "block@mc.com", "2691", "钻石I", "approved"},
            {"EndWarrior", "end@mc.com", "2634", "铂金III", "none"},
            {"CreepMaster", "creep@mc.com", "2589", "铂金II", "approved"},
            {"NetherKing", "nether@mc.com", "2543", "铂金I", "none"},
            {"VoidWalker", "void@mc.com", "2498", "黄金III", "denied"},
            {"DiamondAce", "diamond@mc.com", "2467", "黄金II", "approved"},
            {"Redstone_Pro", "red@mc.com", "2412", "黄金I", "none"},
            {"SkyfallMC", "sky@mc.com", "2389", "白银III", "none"}
        };

        for (String[] p : players) {
            User u = new User();
            u.setUsername(p[0]);
            u.setPassword(encoder.encode("123456"));
            u.setEmail(p[1]);
            u.setScore(Integer.parseInt(p[2]));
            u.setRank(p[3]);
            u.setBetaStatus(p[4]);
            int wins = 100 + rng.nextInt(250);
            int losses = 50 + rng.nextInt(150);
            u.setWins(wins);
            u.setLosses(losses);
            u.setTotalKills(wins * (2 + rng.nextInt(4)) + losses * rng.nextInt(2));
            u.setTotalDeaths(losses * (2 + rng.nextInt(3)));
            userRepo.save(u);
        }

        String[][] roomData = {
            {"午夜竞技场 - 高手进", "团队死斗", "tdm", "沙漠要塞", "desert", "DarkKnight_MC", "12", "16", "23", "playing"},
            {"像素风暴 - 娱乐局", "据点争夺", "dom", "末地城", "end", "PixelStorm", "8", "16", "35", "waiting"},
            {"钢铁丛林 - 排位赛", "生存竞技", "br", "丛林神殿", "jungle", "BlockHunter", "28", "32", "18", "playing"},
            {"极地突袭 - 新手友好", "夺旗战", "ctf", "冰刺平原", "ice", "EndWarrior", "6", "12", "42", "waiting"},
            {"暗影之夜 - 竞速赛", "团队死斗", "tdm", "下界要塞", "nether", "CreepMaster", "16", "16", "28", "playing"},
            {"沙漠风暴 - 随意玩", "据点争夺", "dom", "沙漠要塞", "desert", "NetherKing", "5", "16", "55", "waiting"}
        };

        for (String[] rd : roomData) {
            Room r = new Room();
            r.setName(rd[0]);
            r.setMode(rd[1]);
            r.setModeKey(rd[2]);
            r.setMap(rd[3]);
            r.setMapKey(rd[4]);
            r.setHost(rd[5]);
            r.setCurrentPlayers(Integer.parseInt(rd[6]));
            r.setMaxPlayers(Integer.parseInt(rd[7]));
            r.setPing(Integer.parseInt(rd[8]));
            r.setStatus(rd[9]);
            roomRepo.save(r);
        }

        User first = userRepo.findByUsername("DarkKnight_MC").orElse(null);
        if (first != null) {
            boolean[] wins = {true, false, true, true, false};
            String[] mmodes = {"团队死斗", "据点争夺", "生存竞技", "夺旗战", "团队死斗"};
            String[] mmapNames = {"沙漠要塞", "末地城", "丛林神殿", "冰刺平原", "下界要塞"};
            int[][] kd = {{24,8,6},{15,18,10},{32,4,2},{11,9,14},{19,21,7}};

            for (int i = 0; i < 5; i++) {
                MatchRecord m = new MatchRecord();
                m.setUserId(first.getId());
                m.setMode(mmodes[i]);
                m.setMapName(mmapNames[i]);
                m.setWin(wins[i]);
                m.setKills(kd[i][0]);
                m.setDeaths(kd[i][1]);
                m.setAssists(kd[i][2]);
                m.setScoreChange(wins[i] ? 25 + rng.nextInt(15) : -10 - rng.nextInt(10));
                matchRepo.save(m);
            }
        }

        System.out.println("[NorthStar] Seed data loaded: " + userRepo.count() + " users, " + roomRepo.count() + " rooms");
    }
}
