package com.myriad.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.myriad.combat.DamageCalc;
import com.myriad.network.NetworkSetup;
import com.myriad.network.LobbyNetworking;
import com.myriad.player.PlayerDataProvider;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;

/** Synthetic maps only. Production commands/receivers run with in-process fixture players.
 * Dedicated restart checks are a separate process from GameTest, never claimed as real-client coverage.
 */
public final class ArenaFixtureChecks implements ModInitializer {
    private static final String[] CONFIGS = {"myriad_lane_layout.json", "myriad_monster_spawns.json",
            "myriad_brush_zones.json", "myriad_vein_spawns.json", "myriad_minimap.json"};
    private static final List<String> FILES = new ArrayList<>();
    static {
        for (String file : CONFIGS) { FILES.add(file); FILES.add("myriad_arenas/3v3/" + file); }
        FILES.add("myriad_lobby.json"); FILES.add("myriad_arena_selection.json");
    }
    private static MinecraftServer pendingDedicated;
    private static int trainingWarmupTicks;
    private static boolean trainingForcedChunk;
    @Override public void onInitialize() {
        Registry.register(BuiltInRegistries.TEST_FUNCTION, Identifier.parse("myriad:arena_fixture"), ArenaFixtureChecks::run);
        for(String mode:List.of("3v3","5v5")) Registry.register(BuiltInRegistries.TEST_FUNCTION,
                Identifier.parse("myriad:arena_tower_"+mode), helper -> towerProbe(helper,mode));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!System.getProperty("myriad.arenaFixtureStage", "").isEmpty()) pendingDedicated=server;
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if(pendingDedicated!=server)return;
            pendingDedicated=null;
            String stage=System.getProperty("myriad.arenaFixtureStage");
            if (!server.isDedicatedServer()) throw new IllegalStateException("Fixture restart runner requires dedicated server");
            var suite = new Suite(server);
            try {
                Path manifest=suite.world.toAbsolutePath().normalize().getParent().resolve("arena-fixture-manifest.json");
                if(!Files.isRegularFile(manifest) || !"synthetic-only".equals(JsonParser.parseString(
                        Files.readString(manifest)).getAsJsonObject().get("kind").getAsString()))
                    throw new IllegalStateException("Dedicated fixture stages require the generator's new synthetic instance");
                switch (stage) {
                    case "training" -> {
                        var training = server.getLevel(TrainingGroundMap.KEY);
                        if (training == null) throw new IllegalStateException("Training dimension missing from dedicated server");
                        var spawnChunk = net.minecraft.world.level.ChunkPos.containing(TrainingGroundMap.SPAWN);
                        if (trainingWarmupTicks++ == 0)
                            trainingForcedChunk = training.setChunkForced(spawnChunk.x(), spawnChunk.z(), true);
                        if (!training.areEntitiesActuallyLoadedAndTicking(spawnChunk)) {
                            if (trainingWarmupTicks > 300) throw new IllegalStateException("Training spawn did not become entity ticking");
                            pendingDedicated = server;
                            return;
                        }
                        String mode = System.getProperty("myriad.arenaFixtureMode", "3v3");
                        suite.check(MatchArena.select(server, mode), "Configured synthetic arena available for concurrent training");
                        TrainingGroundCombatChecks.run(server, suite::check);
                    }
                    case "write" -> { suite.scenarios(); suite.begin(System.getProperty("myriad.arenaFixtureMode","3v3")); suite.facilities(); MatchProgress.save(server); }
                    case "read" -> suite.restored();
                    case "read-off" -> {
                        String mode=System.getProperty("myriad.arenaFixtureMode","3v3");
                        suite.check(!ModState.isEnabled() && GameManager.get().getPhase()==GameManager.Phase.WAITING && MatchArena.active().id.equals(mode),"saved disabled checkpoint reopens WAITING and disabled in its selected mode");
                        for(String configured:List.of("5v5","3v3")){suite.command("myriad arena "+configured);suite.verifyImportedCoordinates();}
                        suite.command("myriad arena "+mode);
                    }
                    default -> throw new IllegalArgumentException("Unknown fixture stage: " + stage);
                }
                suite.report(stage, null);
            } catch (Throwable error) {
                suite.report(stage, error);
                com.myriad.Myriad.LOGGER.error("ARENA_FIXTURE_FAILED", error);
                throw new IllegalStateException(error);
            } finally {
                if (pendingDedicated == null) {
                    if (trainingForcedChunk) {
                        var chunk = net.minecraft.world.level.ChunkPos.containing(TrainingGroundMap.SPAWN);
                        server.getLevel(TrainingGroundMap.KEY).setChunkForced(chunk.x(), chunk.z(), false);
                    }
                    trainingWarmupTicks = 0;
                    trainingForcedChunk = false;
                    suite.removePlayers();
                    server.halt(false);
                }
            }
        });
    }
    private static void run(GameTestHelper helper) {
        Suite suite = new Suite(helper.getLevel().getServer());
        try {
            suite.install(); suite.scenarios();
            suite.smallQueueGames();
            suite.begin("3v3");
            MatchProgress.endHostSession(suite.server);
            suite.check(GameManager.get().getPhase() == GameManager.Phase.WAITING,
                    "production local/LAN stop handler ends its current match");
            MatchProgress.openHostSession(suite.server);
            suite.check(!ModState.isEnabled() && GameManager.get().getPhase() == GameManager.Phase.WAITING,
                    "production local/LAN open handler cannot resume even a new checkpoint");
            suite.report("gametest", null); helper.succeed();
        } catch (Throwable error) { suite.report("gametest", error); throw new IllegalStateException(error); }
        finally { suite.removePlayers(); }
    }
    private static void towerProbe(GameTestHelper helper,String mode) {
        Suite suite=new Suite(helper.getLevel().getServer());
        try {
            suite.install();suite.begin(mode);
            var invader=suite.players.getFirst();var defender=suite.opponent(invader);
            var faction=GameManager.get().factionOf(defender);
            var tower=LaneSystem.towerPoint(faction,LaneSystem.Lane.MID,1);
            invader.setPos(Vec3.atBottomCenterOf(tower).add(-7,0,0));
            defender.setPos(Vec3.atBottomCenterOf(tower).add(7,0,0));
            float invaderHp=invader.getHealth(),defenderHp=defender.getHealth();
            helper.runAfterDelay(65,() -> {
                try {
                    suite.check(invader.getHealth()<invaderHp,"actual world ticks let the configured tower acquire and damage its enemy");
                    suite.check(defender.getHealth()==defenderHp,"the same configured tower does not damage its own faction");
                    suite.report("tower-"+mode,null);helper.succeed();
                }catch(Throwable error){suite.report("tower-"+mode,error);throw new IllegalStateException(error);}
                finally{GameManager.get().resetToLobby(suite.server,false);suite.removePlayers();}
            });
        }catch(Throwable error){suite.removePlayers();throw new IllegalStateException(error);}
    }
    private static final class Suite {
        final MinecraftServer server;
        final Path world;
        final List<String> passed = new ArrayList<>();
        final List<ServerPlayer> players = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>();
        Suite(MinecraftServer server) { this.server = server; world = server.getWorldPath(LevelResource.ROOT); }
        void check(boolean condition, String label) {
            if (!condition) throw new AssertionError(label);
            passed.add(MatchArena.active().id + ": " + label);
        }
        void install() throws Exception {
            if(!Boolean.getBoolean("myriad.combatTests"))
                throw new IllegalStateException("Fixture import requires the isolated GameTest launch flag");
            for (String file : FILES) {
                Path path = world.resolve(file); Files.createDirectories(path.getParent());
                try (var stream = ArenaFixtureChecks.class.getResourceAsStream("/arena-fixture/" + file)) {
                    if (stream == null) throw new IllegalStateException("Missing fixture " + file);
                    Files.copy(stream, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.deleteIfExists(world.resolve("myriad_fabric_match.json"));
            PlayerDataProvider.onServerStopped(server); PlayerDataProvider.onServerStarting(server); PlayerDataProvider.onServerStarted(server);
            // Test world has no imported terrain. Place walking support, not an original-map reconstruction.
            for (int ox : new int[]{0,256}) for (int z=0; z<=128; z++)
                for (int x=0; x<=160; x++)
                    server.overworld().setBlockAndUpdate(new BlockPos(ox+x,63,z), Blocks.STONE.defaultBlockState());
        }
        ServerPlayer player(int index) {
            var profile = new GameProfile(UUID.nameUUIDFromBytes(("myriad-arena-fixture-"+index).getBytes(java.nio.charset.StandardCharsets.UTF_8)), "ArenaFixture"+index);
            ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channels.add(new EmbeddedChannel(connection));
            player.connection = new ServerGamePacketListenerImpl(server, connection, player,
                    CommonListenerCookie.createInitial(profile, false)) {
                @Override public void send(Packet<?> packet) { }
            };
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
            server.getPlayerList().deop(new net.minecraft.server.players.NameAndId(profile));
            server.getPlayerList().getPlayers().add(player);
            server.getPlayerList().getPlayersByUUID().put(player.getUUID(), player);
            server.overworld().addNewPlayer(player);
            players.add(player); GameManager.get().onPlayerJoin(player);
            return player;
        }
        void removePlayers() {
            for (var player : players) {
                server.getPlayerList().getPlayers().remove(player);
                server.getPlayerList().getPlayersByUUID().remove(player.getUUID());
                server.overworld().players().remove(player); player.discard();
            }
            players.clear(); channels.forEach(EmbeddedChannel::finishAndReleaseAll); channels.clear();
        }
        int command(String command) throws Exception { return server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack()); }
        void receive(ServerPlayer player, CustomPacketPayload payload) {
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            try {
                ServerboundCustomPayloadPacket.STREAM_CODEC.encode(buf, new ServerboundCustomPayloadPacket(payload));
                player.connection.handleCustomPayload(ServerboundCustomPayloadPacket.STREAM_CODEC.decode(buf));
                check(!buf.isReadable(), "registered C2S codec consumes " + payload.type().id());
            } finally { buf.release(); }
        }
        ServerPlayer opponent(ServerPlayer player) {
            var faction = GameManager.get().factionOf(player);
            return players.stream().filter(p -> GameManager.get().factionOf(p) != null && GameManager.get().factionOf(p) != faction).findFirst().orElseThrow();
        }
        void lobby(ServerPlayer player, String action, String role) {
            var view = QueueLobby.snapshot(player);
            long token = switch (action) {
                case "propose" -> view.proposalToken();
                case "lock", "preselect" -> view.pickToken();
                default -> view.queueToken();
            };
            receive(player, new LobbyNetworking.Action(action, role, token));
        }
        void clock(long tick) { ((ServerLevelData)server.overworld().getLevelData()).setGameTime(tick); }
        void tick() { PlayerDataProvider.onServerTick(server); }
        void scenarios() throws Exception {
            check(!Files.exists(world.resolve("myriad_fabric_match.json")), "fixture imports map configuration, no new match checkpoint");
            Files.writeString(world.resolve("myriad_match.json"), "{\"enabled\":true,\"match\":{\"phase\":\"PLAYING\"}}");
            MatchProgress.load(server);
            check(!ModState.isEnabled() && GameManager.get().getPhase() == GameManager.Phase.WAITING, "legacy NeoForge checkpoint is not imported");
            for (String mode : List.of("5v5","3v3")) {
                begin(mode); facilities(); checkpointRoundTrip(); winAndRestart();
                command("myriad mod off");
                check(!ModState.isEnabled() && GameManager.get().getPhase() == GameManager.Phase.WAITING, "normal off command disables gameplay and ends the new round");
                check(lanes().isEmpty() && MonsterManager.snapshot().getAsJsonArray("alive").isEmpty(), "off removes generated lane and monster runtime");
                check(players.stream().allMatch(p -> PlayerDataProvider.get(p).getCharacterId() == null), "off clears selected character and match progress");
                removePlayers();
            }
        }
        void begin(String mode) throws Exception {
            check(GameManager.get().getPhase()==GameManager.Phase.WAITING,"next normal flow starts from WAITING");
            check(command("myriad arena " + mode)==1 && MatchArena.active().id.equals(mode), "normal mode command selects available profile while WAITING");
            int count = MatchArena.active().teamSize*2;
            if (players.isEmpty()) for(int i=0;i<count;i++) player(i);
            check(players.size()==count, "full " + count + "-participant fixture");
            var low = players.getFirst().createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS);
            boolean denied = false;
            try { server.getCommands().getDispatcher().execute("myriad arena " + (mode.equals("5v5")?"3v3":"5v5"),low); }
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied=true; }
            check(denied && MatchArena.active().id.equals(mode), "command dispatcher rejects non-admin mode selection");
            var before = MapFacilityEditor.snapshotEntries(server);
            receive(players.getFirst(),new NetworkSetup.MapFacilityActionPayload("crystal","destruction","","clear"));
            check(before.equals(MapFacilityEditor.snapshotEntries(server)), "non-admin facility payload cannot mutate map");
            command("myriad mod on");
            check(players.stream().allMatch(p -> p.position().distanceTo(new Vec3(-32.5,64,64.5))<.01), "both battlefields use the shared world lobby");
            check(LaneSystem.enabledLanes().size()==(mode.equals("5v5")?3:1), "profile restores its enabled lanes");
            configurationRoundTrip();
            var crystal=LaneSystem.crystalPoint(Faction.DESTRUCTION);
            String other=mode.equals("5v5")?"3v3":"5v5";
            byte[] otherLayout=Files.readAllBytes(MatchArena.configuration(world,MatchArena.Mode.byId(other),CONFIGS[0]));
            check(LaneSystem.setCrystalPoint(Faction.DESTRUCTION,crystal.offset(1,0,0)), "map edit is accepted before start");
            LaneSystem.initialize(server);
            check(LaneSystem.crystalPoint(Faction.DESTRUCTION).equals(crystal.offset(1,0,0)), "edited point reads back from the correct profile");
            LaneSystem.setCrystalPoint(Faction.DESTRUCTION,crystal);
            check(Arrays.equals(otherLayout,Files.readAllBytes(MatchArena.configuration(world,MatchArena.Mode.byId(other),CONFIGS[0]))), "editing one profile preserves the other profile bytes");
            MatchArena.shutdown(); MatchArena.initialize(server);
            check(MatchArena.active().id.equals(mode), "selected mode survives configuration lifecycle reload");
            for (ServerPlayer player : players) lobby(player, "join", "");
            check(GameManager.get().getPhase()==GameManager.Phase.PICKING, "full queue enters exactly one draft via its registered C2S receiver");
            check(players.stream().filter(p -> GameManager.get().factionOf(p)==Faction.GUARDIAN).count()==count/2, "queued teams are balanced at mode capacity");
            check(command("myriad team destruction ArenaFixture0")==0,"draft rejects administrator team-command changes");
            check(command("myriad arena " + other)==0 && !LaneSystem.setCrystalPoint(Faction.DESTRUCTION,crystal.offset(4,0,0)), "draft rejects mode switching and point edits");
            var order=pickOrder();
            String chosen="shuo";
            var wrong=players.stream().filter(p -> !p.getUUID().equals(order.getFirst())).findFirst().orElseThrow();
            lobby(wrong,"lock",chosen);
            check(PlayerDataProvider.get(wrong).getCharacterId()==null, "out-of-turn pick payload is rejected");
            var roles=new ArrayList<>(List.of(com.myriad.character.CharacterType.playableValues()));
            for(int i=0;i<order.size();i++) {
                ServerPlayer p=server.getPlayerList().getPlayer(order.get(i));
                if(i>0) { lobby(p,"lock",roles.getFirst().getId()); check(PlayerDataProvider.get(p).getCharacterId()==null,"already reserved character is rejected"); }
                lobby(p,"lock",roles.get(i).getId());
                check(roles.get(i).getId().equals(PlayerDataProvider.get(p).getCharacterId()),"current participant can pick a unique character");
            }
            check(GameManager.get().getPhase()==GameManager.Phase.PICKING && players.stream().allMatch(p -> p.distanceToSqr(Vec3.atBottomCenterOf(LaneSystem.fountainPoint(GameManager.get().factionOf(p))))<20), "complete draft enters the configured fountains while phase remains PICKING");
            long now=server.overworld().getGameTime(); clock(now+199);tick();
            check(GameManager.get().getPhase()==GameManager.Phase.PICKING,"preparation lasts the original 200 ticks");
            clock(now+200);tick();
            check(GameManager.get().getPhase()==GameManager.Phase.PLAYING,"normal match tick starts combat at the preparation deadline");
            check(command("myriad arena " + other)==0,"playing rejects mode selection");
        }
        void smallQueueGames() throws Exception {
            command("myriad mod on");
            command("myriad arena 3v3");
            var manager = GameManager.get();
            for (int i = 0; i < 5; i++) player(i);
            var first = players.get(0);
            var second = players.get(1);
            var late = players.get(4);
            checkQueueReset(first, second, late);
            for (int size : List.of(2, 4)) {
                for (var player : players.subList(0, size)) lobby(player, "join", "");
                var savedJoin = new LobbyNetworking.Action("join", "", QueueLobby.snapshot(first).queueToken());
                receive(first, savedJoin);
                check(QueueLobby.snapshot(first).count() == size, "duplicate join leaves one ticket");
                long obsoleteProposal = QueueLobby.snapshot(first).proposalToken();
                check(obsoleteProposal != 0, "the eligible roster has a live proposal token");
                lobby(first, "propose", "");
                check(QueueLobby.snapshot(second).voteSeconds() == 15, "one player starts a visible 15-second countdown");
                lobby(second, "leave", "");
                check(QueueLobby.snapshot(first).voteSeconds() < 0 && QueueLobby.snapshot(first).position() == 1,
                        "withdrawal cancels the proposal while the first player keeps their position");
                lobby(second, "join", "");
                receive(first, new LobbyNetworking.Action("propose", "", obsoleteProposal));
                check(QueueLobby.snapshot(first).voteSeconds() < 0, "expired proposal request cannot start a later vote");
                lobby(first, "propose", "");
                lobby(late, "join", "");
                check(QueueLobby.snapshot(first).voteSeconds() < 0 && !QueueLobby.snapshot(first).canPropose(),
                        "arrival cancels the old proposal and an odd roster cannot start");
                lobby(late, "leave", "");
                lobby(first, "propose", "");
                long proposed = server.overworld().getGameTime();
                clock(proposed + 299); QueueLobby.tick(server);
                check(manager.getPhase() == GameManager.Phase.WAITING, "small games wait all 300 ticks");
                clock(proposed + 300); QueueLobby.tick(server);
                var order = pickOrder();
                check(manager.getPhase() == GameManager.Phase.PICKING && order.size() == size,
                        "deadline locks exactly the proposed roster");
                var expected = size == 2 ? List.of(Faction.GUARDIAN, Faction.DESTRUCTION)
                        : List.of(Faction.GUARDIAN, Faction.DESTRUCTION, Faction.DESTRUCTION, Faction.GUARDIAN);
                check(order.stream().map(server.getPlayerList()::getPlayer).map(manager::factionOf).toList().equals(expected),
                        "small game uses the right blue-first snake sequence");
                var unpicked = server.getPlayerList().getPlayer(order.getLast());
                var opening = server.getPlayerList().getPlayer(order.getFirst());
                lobby(unpicked, "preselect", "shuo");
                check("shuo".equals(QueueLobby.snapshot(unpicked).preselected()) && QueueLobby.snapshot(opening).preselected().isEmpty(),
                        "early intent is visible only to its owner");
                var oldPick = new LobbyNetworking.Action("lock", "shuo", QueueLobby.snapshot(unpicked).pickToken());
                var roles = List.of("tianxing", "chixiao", "qingluo");
                for (int i = 0; i < order.size() - 1; i++) {
                    var picker = server.getPlayerList().getPlayer(order.get(i));
                    lobby(picker, "lock", roles.get(i));
                }
                receive(unpicked, oldPick);
                check(manager.queuePickedRole(unpicked.getUUID()).isEmpty(), "stale turn token cannot lock even for the new current picker");
                manager.onPlayerQuit(unpicked);
                server.getPlayerList().getPlayers().remove(unpicked);
                server.getPlayerList().getPlayersByUUID().remove(unpicked.getUUID());
                long deadline = proposed + 300 + 400;
                clock(deadline - 1); tick();
                check(manager.queuePickedRole(unpicked.getUUID()).isEmpty(), "offline player has their full selection window");
                clock(deadline); tick();
                check("shuo".equals(manager.queuePickedRole(unpicked.getUUID())), "offline timeout locks their private legal intent");
                lobby(late, "join", "");
                check(QueueLobby.snapshot(late).position() == 1 && "occupied".equals(QueueLobby.snapshot(late).reason()),
                        "late player queues for the next match");
                clock(deadline + 199); tick();
                check(manager.getPhase() == GameManager.Phase.PICKING && PlayerDataProvider.get(opening).getGold() == 0,
                        "preparation pays nothing for its full ten seconds");
                clock(deadline + 200); tick();
                check(manager.getPhase() == GameManager.Phase.PLAYING && PlayerDataProvider.get(opening).getGold() == 500,
                        "battle starts with 500 gold despite an offline seat");
                check(manager.factionOf(late) == null && PlayerDataProvider.get(late).getGold() == 0, "waiting player gets no faction or purse");
                server.getPlayerList().getPlayers().add(unpicked);
                server.getPlayerList().getPlayersByUUID().put(unpicked.getUUID(), unpicked);
                manager.onPlayerJoin(unpicked);
                check("shuo".equals(PlayerDataProvider.get(unpicked).getCharacterId())
                        && PlayerDataProvider.get(unpicked).getGold() == 500, "offline seat regains its character and one opening purse");
                PlayerDataProvider.get(unpicked).setGold(380);
                manager.onPlayerJoin(unpicked);
                check(PlayerDataProvider.get(unpicked).getGold() == 380, "a subsequent return never repays the opening purse");
                manager.resetToLobby(server, true);
                check(QueueLobby.snapshot(late).position() == 1, "round cleanup preserves the waiting line");
                lobby(late, "leave", "");
            }
            command("myriad mod off");
            removePlayers();
        }
        void checkQueueReset(ServerPlayer first, ServerPlayer second, ServerPlayer late) throws Exception {
            command("myriad arena 5v5");
            var inactive = players.get(2);
            lobby(inactive, "join", "");
            command("myriad arena 3v3");
            check(QueueLobby.snapshot(inactive).position() == 1
                    && "inactive".equals(QueueLobby.snapshot(inactive).reason()), "alternate map retains its waiting line");
            var staleJoin = new LobbyNetworking.Action("join", "", QueueLobby.snapshot(late).queueToken());
            lobby(first, "join", "");
            lobby(second, "join", "");
            var view = QueueLobby.snapshot(first);
            check(view.canPropose() && view.proposalToken() != 0, "pre-reset proposal is valid for the waiting pair");
            var staleProposal = new LobbyNetworking.Action("propose", "", view.proposalToken());
            var staleLeave = new LobbyNetworking.Action("leave", "", view.queueToken());
            receive(first, staleProposal);
            check(QueueLobby.snapshot(second).voteSeconds() == 15, "administrator resets an active countdown");
            check(command("myriad reset") == 1, "administrator full reset succeeds");
            check(QueueLobby.snapshot(first).count() == 0 && QueueLobby.snapshot(first).voteSeconds() < 0
                    && QueueLobby.snapshot(inactive).position() == 0, "reset clears both queues and the live countdown");
            receive(late, staleJoin);
            check(QueueLobby.snapshot(late).position() == 0, "pre-reset join cannot re-enrol the player");
            lobby(first, "join", "");
            lobby(second, "join", "");
            receive(first, staleLeave);
            receive(first, staleProposal);
            var fresh = QueueLobby.snapshot(first);
            check(fresh.count() == 2 && fresh.position() == 1 && fresh.canPropose() && fresh.voteSeconds() < 0,
                    "pre-reset withdrawal and proposal cannot alter a freshly rejoined identical roster");
            lobby(first, "leave", "");
            lobby(second, "leave", "");
        }

        void configurationRoundTrip() throws Exception {
            verifyImportedCoordinates();
            var originals=new HashMap<String,byte[]>();
            for(String file:CONFIGS) originals.put(file,Files.readAllBytes(MatchArena.configPath(server,file)));
            var before=new HashSet<>(MapFacilityEditor.snapshotEntries(server));
            int ox=MatchArena.active()==MatchArena.Mode.RIFT?0:256;
            check(LaneSystem.setPathPoint(LaneSystem.Lane.MID,1,new BlockPos(ox+17,64,64)),"lane path edits save through the production map API");
            check(LaneSystem.setTowerPoint(Faction.GUARDIAN,LaneSystem.Lane.MID,1,new BlockPos(ox+65,64,64)),"owned tower edits save");
            check(LaneSystem.setFountainPoint(Faction.GUARDIAN,new BlockPos(ox+9,64,72)),"owned fountain edits save");
            if(ox==0) check(LaneSystem.setLaneCorner(LaneSystem.Lane.TOP,new BlockPos(81,64,24)),"configured lane corner edits save");
            var brush=VisionSystem.brushInfos().getFirst();
            check(VisionSystem.setBrushCorner(brush.id(),1,brush.min().offset(1,0,0)),"brush corner edits save");
            var point=MonsterManager.spawnPointInfos().getFirst();
            check(MonsterManager.setSpawnPoint(point.id(),point.pos().offset(1,0,0),MonsterType.byId(point.typeId()),point.largeRandom()),"typed camp edits save");
            check(MapSystem.setCorner(1,MapSystem.corner(1).above()),"server minimap corner edits save");
            if(ox==0) {
                check(GameManager.get().setSpiritVeinSpawn(false,1,new BlockPos(49,64,16)),"small vein edits save");
                check(GameManager.get().setSpiritVeinSpawn(true,1,new BlockPos(49,64,120)),"retained large-vein candidate edits save");
            } else check(GameManager.get().setSpiritVeinSpawn(false,1,null),"saving empty 3v3 veins preserves their disabled mode setting");
            var edited=new HashSet<>(MapFacilityEditor.snapshotEntries(server));
            reloadConfiguration();
            check(!before.equals(edited) && edited.equals(new HashSet<>(MapFacilityEditor.snapshotEntries(server))),"all five map files read back the edited coordinates, types and ownership");
            for(var entry:originals.entrySet())Files.write(MatchArena.configPath(server,entry.getKey()),entry.getValue());
            reloadConfiguration();
            check(before.equals(new HashSet<>(MapFacilityEditor.snapshotEntries(server))),"fixture import and reread restore every original configured point without drift");
        }
        JsonObject config(String name) throws Exception {return JsonParser.parseString(Files.readString(MatchArena.configPath(server,name))).getAsJsonObject();}
        BlockPos position(JsonObject row) {return new BlockPos(row.get("x").getAsInt(),row.get("y").getAsInt(),row.get("z").getAsInt());}
        void verifyImportedCoordinates() throws Exception {
            var layout=config(CONFIGS[0]);
            for(var entry:layout.getAsJsonObject("paths").entrySet()) for(var value:entry.getValue().getAsJsonArray()) {
                var row=value.getAsJsonObject();
                check(position(row).equals(LaneSystem.pathPoint(LaneSystem.Lane.byId(entry.getKey()),row.get("slot").getAsInt())),"import preserves manual path lane, slot and coordinates");
            }
            for(var entry:layout.getAsJsonObject("laneCorners").entrySet())
                check(position(entry.getValue().getAsJsonObject()).equals(LaneSystem.laneCorner(LaneSystem.Lane.byId(entry.getKey()))),"import preserves lane-corner coordinates");
            for(var value:layout.getAsJsonArray("towers")) {
                var row=value.getAsJsonObject();
                check(position(row).equals(LaneSystem.towerPoint(Faction.byId(row.get("faction").getAsString()),LaneSystem.Lane.byId(row.get("lane").getAsString()),row.get("tier").getAsInt())),"import preserves tower faction, lane, tier and coordinates");
            }
            for(String category:List.of("fountains","crystals"))for(var value:layout.getAsJsonArray(category)) {
                var row=value.getAsJsonObject();var faction=Faction.byId(row.get("faction").getAsString());
                check(position(row).equals(category.equals("fountains")?LaneSystem.fountainPoint(faction):LaneSystem.crystalPoint(faction)),"import preserves owned " + category + " coordinates");
            }
            for(var value:config(CONFIGS[1]).getAsJsonArray("points")) {
                var row=value.getAsJsonObject();var point=MonsterManager.spawnPointInfos().stream().filter(p -> p.id().equals(row.get("id").getAsString())).findFirst().orElseThrow();
                check(point.pos().equals(position(row)) && (point.largeRandom()?"large_random":point.typeId()).equals(row.get("type").getAsString()),"import preserves camp id, type and coordinates");
            }
            for(var value:config(CONFIGS[2]).getAsJsonArray("brushes")) {
                var row=value.getAsJsonObject();var brush=VisionSystem.brushDraftInfos().stream().filter(p -> p.id().equals(row.get("id").getAsString())).findFirst().orElseThrow();
                check(brush.pos1().equals(new BlockPos(row.get("x1").getAsInt(),row.get("y1").getAsInt(),row.get("z1").getAsInt())) && brush.pos2().equals(new BlockPos(row.get("x2").getAsInt(),row.get("y2").getAsInt(),row.get("z2").getAsInt())),"import preserves both brush corners");
            }
            var veins=config(CONFIGS[3]);
            for(boolean large:new boolean[]{false,true})for(var entry:veins.getAsJsonObject(large?"large":"attribute").entrySet())
                check(position(entry.getValue().getAsJsonObject()).equals(GameManager.get().spiritVeinSpawnSnapshot(large).get(Integer.parseInt(entry.getKey()))),"import preserves resource size, slot and coordinates");
            for(var value:config(CONFIGS[4]).getAsJsonArray("corners")) {
                var row=value.getAsJsonObject();check(position(row).equals(MapSystem.corner(row.get("slot").getAsInt())),"import preserves server minimap corner and coordinates");
            }
            check(veins.get("enabled").getAsBoolean()==GameManager.get().spiritVeinsEnabled(),"import preserves the map resource enable setting");
        }
        void reloadConfiguration() {
            LaneSystem.initialize(server); MonsterManager.initialize(server);VisionSystem.initialize(server);MapSystem.initialize(server);GameManager.get().reloadArenaObjectives();
        }
        @SuppressWarnings("unchecked") List<UUID> pickOrder() throws Exception { return List.copyOf((List<UUID>)field(GameManager.get(),"pickOrder")); }
        @SuppressWarnings("unchecked") Map<UUID,Object> lanes() throws Exception { return (Map<UUID,Object>)field(LaneSystem.class,"UNITS"); }
        List<Mob> units(String kind, Faction faction) throws Exception {
            var result=new ArrayList<Mob>();
            for(var state:lanes().values()) if(field(state,"kind").toString().equals(kind) && (faction==null || field(state,"faction")==faction)) result.add((Mob)field(state,"entity"));
            return result;
        }
        void facilities() throws Exception {
            int expected=MatchArena.active()==MatchArena.Mode.RIFT?18:4;
            check(units("TOWER",null).size()==expected && units("CRYSTAL",null).size()==2,"configured owned towers and crystals materialize in the world");
            check(MapFacilityEditor.snapshotEntries(server).stream().filter(e -> e.category().equals("tower") && e.firstSet()).count()==expected,"editor snapshot exposes exactly the active profile towers");
            int ox=MatchArena.active()==MatchArena.Mode.RIFT?0:256;
            check(MapSystem.configured() && MapSystem.contains(ox+80,64) && !MapSystem.contains(ox+240,64),"server minimap bounds use this mode only");
            check(VisionSystem.brushInfos().size()==1 && VisionSystem.brushAt(new Vec3(ox+80,64,52))!=null && VisionSystem.brushAt(new Vec3((ox==0?256:0)+80,64,52))==null,"brush query uses this mode only");
            check(MonsterManager.spawnPointInfos().size()==3 && MapSystem.neutralCampMarkers().size()==3,"small, medium and random-boss camps route together");
            check(GameManager.get().spiritVeinSpawnSnapshot(false).size()==(ox==0?3:0) && GameManager.get().spiritVeinSpawnSnapshot(true).size()==(ox==0?2:0),"small vein and retained large candidate positions follow the baseline mode rules");
            var ally=players.getFirst(); var enemy=opponent(ally);
            ally.setPos(ox+75,64,52); enemy.setPos(ox+80,64,52);
            check(!VisionSystem.canSee(ally,enemy),"enemy in active brush is concealed outside the brush");
            ally.setPos(ox+79,64,52);check(VisionSystem.canSee(ally,enemy),"entering the same brush reveals the opponent");
            for(var p:players) p.setPos(Vec3.atBottomCenterOf(LaneSystem.fountainPoint(GameManager.get().factionOf(p))));
            var data=PlayerDataProvider.get(ally);ally.setHealth(data.getModMaxHealth()/2); data.setMana(0);
            long start=GameManager.get().getMatchStartTime(); clock((start/20+1)*20);tick();
            check(ally.getHealth()>data.getModMaxHealth()/2 && data.getMana()>0,"own fountain restores health and mana");
            ally.setPos(Vec3.atBottomCenterOf(LaneSystem.fountainPoint(GameManager.get().factionOf(enemy))));
            float hp=ally.getHealth(); ally.damageCooldownTime=0;clock((start/20+2)*20);tick();
            check(ally.getHealth()<hp,"opponent fountain deals its baseline true damage");
            ally.setPos(Vec3.atBottomCenterOf(LaneSystem.fountainPoint(GameManager.get().factionOf(ally))));
            clock(start+599);tick();check(MonsterManager.snapshot().getAsJsonArray("alive").isEmpty(),"small camps do not spawn before 30 seconds");
            clock(start+600);tick();check(MonsterManager.snapshot().getAsJsonArray("alive").size()==1,"small camp spawns at 30 seconds");
            clock(start+1200);tick();check(MonsterManager.snapshot().getAsJsonArray("alive").size()==2,"medium camp spawns at 60 seconds");
            clock(start+1399);tick();check(units("MELEE",null).isEmpty(),"no minion wave before 70 seconds");
            clock(start+1400);tick();
            int lanes=LaneSystem.enabledLanes().size();check(units("MELEE",null).size()==lanes*4 && units("RANGED",null).size()==lanes*4,"first wave creates four minions per team and lane");
            var minion=units("MELEE",Faction.GUARDIAN).getFirst();Vec3 before=minion.position();
            for(int i=1;i<=20;i++){clock(start+1400+i);tick();minion.move(MoverType.SELF,minion.getDeltaMovement());}
            check(minion.position().distanceTo(before)>.1,"spawned minion moves along its configured route with collision stepping");
            clock(start+9600);tick();check(MonsterManager.snapshot().getAsJsonArray("alive").size()==3,"random large boss spawns at its configured pit at eight minutes");
            if(ox==0) {
                // The outer veins lie within the corresponding team's tower range. Use the
                // central vein so a randomly assigned capturer can survive all 120 ticks.
                BlockPos vein = GameManager.get().spiritVeinSpawnSnapshot(false).get(2);
                ally.setPos(Vec3.atBottomCenterOf(vein).add(0, 0, -4));
                check(ally.isAlive() && !ally.isSpectator()
                        && players.stream().filter(p -> p.distanceToSqr(Vec3.atCenterOf(vein)) <= 8 * 8).count() == 1,
                        "one living participant occupies the uncontested central vein");
                for(int i=1;i<=120;i++){clock(start+9600+i);tick();}
                check(GameManager.get().ownedVeinStatus(ally,8)==1,"active small resource point captures through normal match ticks");
                check(GameManager.snapshot().getAsJsonObject().getAsJsonObject("spiritVeins").size()==3,"large resource candidate coordinates do not reactivate the baseline paused large system");
                ally.setPos(Vec3.atBottomCenterOf(LaneSystem.fountainPoint(GameManager.get().factionOf(ally))));
            } else check(GameManager.snapshot().getAsJsonObject().getAsJsonObject("spiritVeins").isEmpty(),"3v3 has no active resource objectives");
        }
        void checkpointRoundTrip() throws Exception {
            var manager=GameManager.snapshot();var lane=LaneSystem.snapshot();var monsters=MonsterManager.snapshot();
            GameManager.restore(manager,server);LaneSystem.restore(server.overworld(),lane);MonsterManager.restore(server.overworld(),monsters);
            check(GameManager.get().getPhase()==GameManager.Phase.PLAYING && LaneSystem.snapshot().getAsJsonArray("units").size()==lane.getAsJsonArray("units").size() && MonsterManager.snapshot().getAsJsonArray("alive").size()==monsters.getAsJsonArray("alive").size(),"new match snapshot restores phase, generated units and monster runtime without duplication");
            if(server.isDedicatedServer() && !Boolean.getBoolean("myriad.combatTests")) {MatchProgress.save(server);check(Files.isRegularFile(world.resolve("myriad_fabric_match.json")),"production checkpoint is written to the new Fabric filename");}
        }
        void winAndRestart() throws Exception {
            var attacker=players.getFirst();Faction defending=GameManager.get().factionOf(opponent(attacker));
            Mob crystal=units("CRYSTAL",defending).getFirst();float hp=LaneSystem.health(crystal);
            DamageCalc.dealBasicDamage(attacker,crystal,10_000_000,DamageCalc.Kind.TRUE);
            check(LaneSystem.health(crystal)==hp,"crystal is invulnerable while its lane towers stand");
            for(var tower:units("TOWER",defending)) {
                if(!tower.isAlive())continue;
                // Progress an actual lane in tier order; larger fixture damage only speeds up the same damage/death path.
                if(!tower.blockPosition().equals(LaneSystem.towerPoint(defending,LaneSystem.Lane.MID,1)))continue;
                for(int tier:LaneSystem.enabledTowerTiers()) {
                    BlockPos pos=LaneSystem.towerPoint(defending,LaneSystem.Lane.MID,tier);
                    Mob current=units("TOWER",defending).stream().filter(t -> t.blockPosition().equals(pos)).findFirst().orElseThrow();
                    DamageCalc.dealBasicDamage(attacker,current,10_000_000,DamageCalc.Kind.TRUE);
                    check(!current.isAlive(),"legal basic damage destroys vulnerable mid tower tier " + tier);
                }
                break;
            }
            crystal.damageCooldownTime=0;DamageCalc.dealBasicDamage(attacker,crystal,10_000_000,DamageCalc.Kind.TRUE);
            check(GameManager.get().getPhase()==GameManager.Phase.ENDED && GameManager.get().getWinner()==GameManager.get().factionOf(attacker),"destroying the unlocked enemy crystal declares the correct winner");
            tick();check(GameManager.get().getPhase()==GameManager.Phase.WAITING && lanes().isEmpty(),"next server tick returns everyone to lobby and clears generated lane entities");
            check(players.stream().allMatch(p -> p.position().distanceTo(new Vec3(-32.5,64,64.5))<.01),"ending the match uses the shared lobby rather than either battlefield");
            lobby(attacker, "requeue", "");
            check(QueueLobby.snapshot(attacker).position()==1 && players.stream().filter(p -> QueueLobby.snapshot(p).position()>0).count()==1,
                    "one result action requeues only its owner");
            lobby(attacker, "leave", "");
            check(GameManager.get().getPhase()==GameManager.Phase.WAITING, "leaving the next queue keeps the arena free");
        }
        void restored() throws Exception {
            String mode=System.getProperty("myriad.arenaFixtureMode","3v3");
            check(ModState.isEnabled() && MatchArena.active().id.equals(mode) && GameManager.get().getPhase()==GameManager.Phase.PLAYING,"actual dedicated restart restores selected arena, enabled flag and PLAYING checkpoint");
            check(units("TOWER",null).size()==(mode.equals("5v5")?18:4) && units("CRYSTAL",null).size()==2 && MonsterManager.snapshot().getAsJsonArray("alive").size()==3,"actual restart restores the active map facilities and monsters exactly once");
            for(int i=0;i<MatchArena.active().teamSize*2;i++)player(i);
            check(players.stream().allMatch(p -> GameManager.get().factionOf(p)!=null),"reconnected fixture identities retain their new match factions");
            command("myriad mod off");check(lanes().isEmpty() && MonsterManager.snapshot().getAsJsonArray("alive").isEmpty(),"off after restart removes restored map runtime");
            MatchProgress.save(server);
        }
        void report(String stage,Throwable failure) {
            try {
                var json=new JsonObject();json.addProperty("stage",stage);json.addProperty("arena",MatchArena.active().id);json.addProperty("dedicated",server.isDedicatedServer());
                json.addProperty("passed",failure==null);json.add("checks",MatchProgress.JSON.toJsonTree(passed));
                if(failure!=null)json.addProperty("failure",failure.toString());
                Files.writeString(world.resolve("arena-fixture-"+stage+".json"),MatchProgress.JSON.toJson(json));
                com.myriad.Myriad.LOGGER.info("ARENA_FIXTURE_{}: stage={} assertions={}",failure==null?"PASS":"FAIL",stage,passed.size());
            }catch(Exception error){throw new IllegalStateException(error);}
        }
    }
    private static Object field(Object instance,String name) throws Exception {
        Class<?> type=instance instanceof Class<?> clazz?clazz:instance.getClass();
        var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(instance instanceof Class<?>?null:instance);
    }
}
