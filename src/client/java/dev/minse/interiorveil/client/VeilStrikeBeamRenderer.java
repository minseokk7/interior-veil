package dev.minse.interiorveil.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 전술 폭격 충돌 지점의 3D 실사 핵폭발 버섯구름 & 다층 광역 열폭풍(Dynamic Multi-Wave Thermal Storm) 렌더러.
 * 3단계 연속 팽창 충격파, 방사형 화염 스트림, 실시간 대류 흡입 와류를 3차원 볼륨으로 렌더링한다.
 */
public final class VeilStrikeBeamRenderer {
    private static final List<ActiveBeam> BEAMS = new CopyOnWriteArrayList<>();

    private VeilStrikeBeamRenderer() {
    }

    public static void addBeam(double x, double y, double z, int durationTicks, int color) {
        BEAMS.add(new ActiveBeam(x, y, z, durationTicks, durationTicks, color));
        // 착탄 지점 기준 최대 256m 범위 내 강력한 지진파 화면 진동 (지속시간 50틱 = 2.5초)
        VeilCameraShake.trigger(new Vec3(x, y, z), 256.0, 2.2, 50.0);
    }

    public static void clientTick() {
        Minecraft client = Minecraft.getInstance();
        net.minecraft.client.multiplayer.ClientLevel level = client.level;
        net.minecraft.world.entity.player.Player player = client.player;

        BEAMS.removeIf(beam -> {
            beam.ticksRemaining--;

            boolean isEmp = (beam.color == 0x00E5FF);

            // 착탄 초기 160틱(8초) 동안 파티클 효과
            int elapsed = beam.maxTicks - beam.ticksRemaining;
            if (level != null && elapsed < 160) {
                dev.minse.interiorveil.network.ForcefieldStatePayload veilState = VeilForcefieldRenderer.getCurrentState();

                // 1. ⚡ [EMP 전용 파티클 (0x00E5FF)]
                if (beam.color == 0x00E5FF) {
                    if (elapsed < 80) {
                        float p = (float) elapsed / 80.0f;
                        double empRadius = Math.pow(p, 0.6) * 180.0;
                        double empCenterY = beam.y + 200.0;

                        for (int i = 0; i < 25; i++) {
                            double angle = Math.random() * 2.0 * Math.PI;
                            double px = beam.x + Math.cos(angle) * empRadius;
                            double pz = beam.z + Math.sin(angle) * empRadius;
                            double py = empCenterY + (Math.random() - 0.5) * 8.0;
                            if (isInsideVeil(px, pz, veilState)) continue;

                            level.addParticle(ParticleTypes.ELECTRIC_SPARK, px, py, pz, (Math.random() - 0.5) * 0.2, (Math.random() - 0.5) * 0.2, (Math.random() - 0.5) * 0.2);
                            if (Math.random() < 0.3) {
                                level.addParticle(ParticleTypes.GLOW, px, py, pz, 0, 0, 0);
                            }
                        }
                        for (int i = 0; i < 8; i++) {
                            double angle = Math.random() * 2.0 * Math.PI;
                            double gr = Math.random() * empRadius * 0.8;
                            double gx = beam.x + Math.cos(angle) * gr;
                            double gz = beam.z + Math.sin(angle) * gr;
                            if (isInsideVeil(gx, gz, veilState)) continue;
                            level.addParticle(ParticleTypes.ELECTRIC_SPARK, gx, beam.y + Math.random() * 3.0, gz, 0, 0.05, 0);
                        }
                    }
                    return beam.ticksRemaining <= 0;
                }

                // 2. 📦 [보급 포드 전용 파티클 (0x55FF55)]
                if (beam.color == 0x55FF55) {
                    if (elapsed < 60) {
                        for (int i = 0; i < 12; i++) {
                            double angle = Math.random() * 2.0 * Math.PI;
                            double r = Math.random() * 4.0;
                            double px = beam.x + Math.cos(angle) * r;
                            double pz = beam.z + Math.sin(angle) * r;
                            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, beam.y + 0.2, pz, Math.cos(angle) * 0.15, 0.08, Math.sin(angle) * 0.15);
                            level.addParticle(ParticleTypes.FIREWORK, px, beam.y + 0.5, pz, 0, 0.1, 0);
                        }
                    }
                    return beam.ticksRemaining <= 0;
                }

                // 3. ❄️ [극저온 동결탄 전용 파티클 (0x80D8FF)]
                if (beam.color == 0x80D8FF) {
                    if (elapsed < 120) {
                        float cp = (float) elapsed / 120.0f;
                        double cryoRadius = Math.pow(cp, 0.5) * 48.0;
                        for (int i = 0; i < 30; i++) {
                            double angle = Math.random() * 2.0 * Math.PI;
                            double r = Math.random() * cryoRadius;
                            double px = beam.x + Math.cos(angle) * r;
                            double pz = beam.z + Math.sin(angle) * r;
                            if (isInsideVeil(px, pz, veilState)) continue;

                            double py = beam.y + Math.random() * 5.0;
                            level.addParticle(ParticleTypes.SNOWFLAKE, px, py, pz, (Math.random() - 0.5) * 0.15, -0.05, (Math.random() - 0.5) * 0.15);
                            if (Math.random() < 0.3) {
                                level.addParticle(ParticleTypes.DRIPPING_WATER, px, py + 1.0, pz, 0, -0.1, 0);
                            }
                        }
                    }
                    return beam.ticksRemaining <= 0;
                }

                // 4. 🕳️ [중력 특이점 탄 전용 파티클 (0x9400D3)]
                if (beam.color == 0x9400D3) {
                    if (elapsed < 140) {
                        for (int i = 0; i < 35; i++) {
                            double angle = Math.random() * 2.0 * Math.PI;
                            double r = 4.0 + Math.random() * 44.0;
                            double px = beam.x + Math.cos(angle) * r;
                            double pz = beam.z + Math.sin(angle) * r;
                            double py = beam.y + 1.5 + (Math.random() - 0.5) * 8.0;

                            // 중심부(beam.x, beam.y + 1.5, beam.z)를 향해 빨려 들어가는 흡입 벡터
                            double vx = (beam.x - px) * 0.08 - Math.sin(angle) * 0.15;
                            double vz = (beam.z - pz) * 0.08 + Math.cos(angle) * 0.15;
                            double vy = (beam.y + 1.5 - py) * 0.05;

                            level.addParticle(ParticleTypes.PORTAL, px, py, pz, vx, vy, vz);
                            if (Math.random() < 0.25) {
                                level.addParticle(ParticleTypes.REVERSE_PORTAL, px, py, pz, vx * 0.5, vy * 0.5, vz * 0.5);
                            }
                            if (Math.random() < 0.15) {
                                level.addParticle(ParticleTypes.WITCH, px, py, pz, vx, vy, vz);
                            }
                        }
                    }
                    return beam.ticksRemaining <= 0;
                }

                // 5. ☣️ [나노 낙진탄 전용 파티클 (0x00FF66)]
                if (beam.color == 0x00FF66) {
                    if (elapsed < 160) {
                        float np = (float) elapsed / 160.0f;
                        double nanoRadius = Math.min(48.0, 10.0 + np * 38.0);
                        for (int i = 0; i < 28; i++) {
                            double angle = Math.random() * 2.0 * Math.PI;
                            double r = Math.random() * nanoRadius;
                            double px = beam.x + Math.cos(angle) * r;
                            double pz = beam.z + Math.sin(angle) * r;
                            if (isInsideVeil(px, pz, veilState)) continue;

                            double py = beam.y + 0.2 + Math.random() * 3.5;
                            level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, px, py, pz, (Math.random() - 0.5) * 0.05, 0.02, (Math.random() - 0.5) * 0.05);
                            if (Math.random() < 0.2) {
                                level.addParticle(ParticleTypes.SCULK_SOUL, px, py, pz, 0, 0.04, 0);
                            }
                            if (Math.random() < 0.15) {
                                level.addParticle(ParticleTypes.MYCELIUM, px, py, pz, 0, 0, 0);
                            }
                        }
                    }
                    return beam.ticksRemaining <= 0;
                }

                // 6. 🛡️ [방어막 포드 전용 파티클 (0xFFD700)]
                if (beam.color == 0xFFD700) {
                    if (elapsed < 80) {
                        for (int i = 0; i < 20; i++) {
                            double angle = Math.random() * 2.0 * Math.PI;
                            double r = 16.0; // 16m 방어 돔 경계
                            double px = beam.x + Math.cos(angle) * r;
                            double pz = beam.z + Math.sin(angle) * r;
                            double py = beam.y + Math.random() * 12.0;

                            level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, px, py, pz, 0, 0.05, 0);
                            if (Math.random() < 0.3) {
                                level.addParticle(ParticleTypes.ENCHANT, px, py, pz, 0, 0.1, 0);
                            }
                        }
                    }
                    return beam.ticksRemaining <= 0;
                }

                // 7. 💥 [고폭탄 전용 파티클 (0xFF2222)]
                if (elapsed < 80) {
                    for (int i = 0; i < 15; i++) {
                        double angle = Math.random() * 2.0 * Math.PI;
                        double speed = 0.5 + Math.random() * 0.8;
                        level.addParticle(ParticleTypes.EXPLOSION, beam.x, beam.y + 1.0, beam.z, Math.cos(angle) * speed, 0.2 + Math.random() * 0.4, Math.sin(angle) * speed);
                        level.addParticle(ParticleTypes.LAVA, beam.x, beam.y + 2.0, beam.z, Math.cos(angle) * speed * 0.8, 0.4, Math.sin(angle) * speed * 0.8);
                    }
                }

                // 2. 파면(Wave Front) 팽창 반경 계산 (Zone 2, 3 확장에 맞춘 240m 광역 파면)
                float p1 = (float) Math.min(80, elapsed) / 80.0f;
                double currentWaveRadius = Math.pow(p1, 0.65) * 240.0;

                boolean playerInsideVeil = false;
                if (player != null && veilState != null) {
                    double distPlayerToVeil = Math.sqrt(Math.pow(player.getX() - veilState.centerX(), 2) + Math.pow(player.getZ() - veilState.centerZ(), 2));
                    if (distPlayerToVeil < veilState.radius()) {
                        playerInsideVeil = true;
                    }
                }

                // 3. 플레이어 시야 근접 집중 폭풍 (Player Proximity Storm Blast)
                // ※ 플레이어가 결계 안에 있을 때는 내부로 폭풍이 들어오지 못하므로 실행하지 않음!
                if (player != null && !playerInsideVeil) {
                    double distToCenter = Math.sqrt(Math.pow(player.getX() - beam.x, 2) + Math.pow(player.getZ() - beam.z, 2));
                    double distToWave = Math.abs(distToCenter - currentWaveRadius);

                    // 폭풍 전선이 플레이어 50m 이내에 도달했을 때 (최대 250m 광역 커버)
                    if (distToWave < 50.0 && distToCenter < 250.0) {
                        double stormDirX = (player.getX() - beam.x) / Math.max(1.0, distToCenter);
                        double stormDirZ = (player.getZ() - beam.z) / Math.max(1.0, distToCenter);

                        // 플레이어 전방 시야에 대량의 화염 및 자욱한 흙먼지 폭풍 방출 (초당 수백 개)
                        int stormDensity = (int) (45 * (1.0 - distToWave / 50.0));
                        for (int i = 0; i < stormDensity; i++) {
                            double offsetX = (Math.random() - 0.5) * 32.0;
                            double offsetZ = (Math.random() - 0.5) * 32.0;
                            double offsetY = Math.random() * 8.0;

                            double px = player.getX() + offsetX;
                            double py = player.getY() + offsetY;
                            double pz = player.getZ() + offsetZ;

                            // 결계 내부 영역 보호
                            if (veilState != null) {
                                double dV = Math.sqrt(Math.pow(px - veilState.centerX(), 2) + Math.pow(pz - veilState.centerZ(), 2));
                                if (dV < veilState.radius()) continue;
                            }

                            double windSpeed = 0.9 + Math.random() * 0.8;
                            double vx = stormDirX * windSpeed + (Math.random() - 0.5) * 0.2;
                            double vz = stormDirZ * windSpeed + (Math.random() - 0.5) * 0.2;
                            double vy = (Math.random() - 0.3) * 0.2;

                            level.addParticle(ParticleTypes.FLAME, px, py, pz, vx, vy, vz);
                            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, py, pz, vx * 0.6, vy + 0.1, vz * 0.6);
                            if (Math.random() < 0.3) {
                                level.addParticle(ParticleTypes.LAVA, px, py, pz, vx * 0.8, vy + 0.2, vz * 0.8);
                            }
                            if (Math.random() < 0.5) {
                                level.addParticle(ParticleTypes.LARGE_SMOKE, px, py, pz, vx * 0.7, vy + 0.05, vz * 0.7);
                            }
                        }

                        // 폭풍이 몸을 스쳐 지나갈 때 굉음 및 강풍 사운드
                        if (distToWave < 14.0 && elapsed % 6 == 0) {
                            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                                    net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.WEATHER,
                                    1.3F, 0.5F + (float) Math.random() * 0.3F, false);
                        }
                    }
                }

                // 4. 결계(Veil Barrier) 표면을 타고 흐르는 열폭풍 편향 스트림 (Shield Hull Wash Effect)
                // 결계가 활성화되어 있다면, 폭풍이 결계 내부로 들어오지 못하고 결계 돔 외벽 표면을 따라 2갈래로 휘감아 흐름!
                if (veilState != null && level != null) {
                    double vCenterX = veilState.centerX();
                    double vCenterY = veilState.centerY();
                    double vCenterZ = veilState.centerZ();
                    double vRadius = veilState.radius();

                    double distBeamToVeil = Math.sqrt(Math.pow(vCenterX - beam.x, 2) + Math.pow(vCenterZ - beam.z, 2));
                    double hitStart = distBeamToVeil - vRadius;
                    double hitEnd = distBeamToVeil + vRadius + 50.0;

                    // 폭풍 파면이 결계에 닿아 지나가는 구간일 때
                    if (currentWaveRadius >= hitStart && currentWaveRadius <= hitEnd) {
                        double stormDirX = (vCenterX - beam.x) / Math.max(1.0, distBeamToVeil);
                        double stormDirZ = (vCenterZ - beam.z) / Math.max(1.0, distBeamToVeil);

                        // 결계 외벽 둘레를 따라 맹렬한 화염/스파크 편향 스트림 방출 (틱당 75개)
                        for (int i = 0; i < 75; i++) {
                            // 착탄 방향을 마주보는 결계 전면부~측면부 각도 중심
                            double baseAngle = Math.atan2(beam.z - vCenterZ, beam.x - vCenterX);
                            double spread = (Math.random() - 0.5) * Math.PI * 1.7; // 전면 150도 각도
                            double angle = baseAngle + spread;

                            // 결계 외벽 표면 바로 바깥 (0.3~1.0m)
                            double pr = vRadius + 0.3 + Math.random() * 0.8;
                            double px = vCenterX + Math.cos(angle) * pr;
                            double pz = vCenterZ + Math.sin(angle) * pr;
                            double py = vCenterY + (Math.random() - 0.25) * (vRadius * 0.9);

                            // 결계 외벽의 접선 방향(Tangent Vector) 계산 (좌/우로 갈라져 돔을 타고 흐름)
                            double side = Math.signum(spread);
                            if (side == 0) side = (Math.random() < 0.5) ? 1.0 : -1.0;

                            double tangX = -Math.sin(angle) * side;
                            double tangZ = Math.cos(angle) * side;

                            // 접선 방향 + 폭풍 진행 방향 합성
                            double flowSpeed = 1.0 + Math.random() * 0.7;
                            double vx = (tangX * 0.75 + stormDirX * 0.4) * flowSpeed;
                            double vz = (tangZ * 0.75 + stormDirZ * 0.4) * flowSpeed;
                            double vy = 0.1 + Math.random() * 0.2; // 돔 곡면을 타고 위로 상승

                            // 화염풍 및 결계 에너지 방전 스파크
                            level.addParticle(ParticleTypes.FLAME, px, py, pz, vx, vy, vz);
                            level.addParticle(ParticleTypes.ELECTRIC_SPARK, px, py, pz, vx * 0.7, vy * 1.3, vz * 0.7);
                            if (Math.random() < 0.4) {
                                level.addParticle(ParticleTypes.LAVA, px, py, pz, vx * 0.6, vy, vz * 0.6);
                            }
                            if (Math.random() < 0.45) {
                                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, py, pz, vx * 0.5, vy + 0.15, vz * 0.5);
                            }
                        }

                        // 결계 표면 방전 보호막 굴절 사운드
                        if (player != null && elapsed % 8 == 0) {
                            if (playerInsideVeil) {
                                level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                                        net.minecraft.sounds.SoundEvents.BEACON_AMBIENT, net.minecraft.sounds.SoundSource.BLOCKS,
                                        1.5F, 1.5F + (float) Math.random() * 0.4F, false);
                            }
                        }
                    }
                }

                // 5. 지면 전체를 휩쓰는 360도 방사형 지상 파티클 (결계 내부에는 생성되지 않도록 보호)
                for (int i = 0; i < 25; i++) {
                    double angle = Math.random() * 2.0 * Math.PI;
                    double r = currentWaveRadius * (0.85 + Math.random() * 0.15);
                    double px = beam.x + Math.cos(angle) * r;
                    double pz = beam.z + Math.sin(angle) * r;

                    // 만약 생성 지점이 결계 내부라면 결계 밖으로 밀어냄
                    if (veilState != null) {
                        double dVeil = Math.sqrt(Math.pow(px - veilState.centerX(), 2) + Math.pow(pz - veilState.centerZ(), 2));
                        if (dVeil < veilState.radius()) {
                            continue; // 결계 내부는 100% 청정 보호
                        }
                    }

                    double py = beam.y + 0.5 + Math.random() * 4.0;
                    double speed = 0.7 + Math.random() * 0.5;

                    level.addParticle(ParticleTypes.FLAME, px, py, pz, Math.cos(angle) * speed, 0.1, Math.sin(angle) * speed);
                    level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, py, pz, Math.cos(angle) * 0.4, 0.15, Math.sin(angle) * 0.4);
                }
            }

            return beam.ticksRemaining <= 0;
        });
    }

    public static void clear() {
        BEAMS.clear();
    }

    public static void render(WorldRenderContext context) {
        if (BEAMS.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.gameRenderer == null) {
            return;
        }

        PoseStack.Pose pose = context.matrices().last();
        VertexConsumer consumer = context.consumers().getBuffer(RenderType.lightning());
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        Quaternionf camRot = camera.rotation();
        Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f).rotate(camRot);
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f).rotate(camRot);

        long gameTime = client.level.getGameTime();
        float anim = gameTime * 0.12f;

        dev.minse.interiorveil.network.ForcefieldStatePayload veilState = VeilForcefieldRenderer.getCurrentState();

        for (ActiveBeam beam : BEAMS) {
            int elapsed = beam.maxTicks - beam.ticksRemaining;
            float fadeAlpha = Math.min(1.0f, (float) beam.ticksRemaining / 100.0f);

            int r = (beam.color >> 16) & 0xFF;
            int g = (beam.color >> 8) & 0xFF;
            int b = beam.color & 0xFF;

            switch (beam.color) {
                case 0x00E5FF -> { // ⚡ 1: EMP 전자기 펄스탄
                    if (elapsed < 140) {
                        drawEmpTacticalDischarge(consumer, pose, camPos, right, up, beam, elapsed, anim, veilState);
                    }
                }
                case 0x55FF55 -> { // 📦 2: 궤도 보급 포드
                    if (elapsed < 120) {
                        drawSupplyDropPod(consumer, pose, camPos, right, up, beam, elapsed, anim, veilState);
                    }
                }
                case 0x80D8FF -> { // ❄️ 3: 극저온 동결탄 (Cryo)
                    if (elapsed < 140) {
                        drawCryoBlizzardSpire(consumer, pose, camPos, right, up, beam, elapsed, anim, veilState);
                    }
                }
                case 0x9400D3 -> { // 🕳️ 4: 중력 특이점 탄 (Singularity)
                    if (elapsed < 160) {
                        drawGravitySingularity(consumer, pose, camPos, right, up, beam, elapsed, anim, veilState);
                    }
                }
                case 0x00FF66 -> { // ☣️ 5: 나노 독소 / 낙진탄 (Nanite Fallout)
                    if (elapsed < 160) {
                        drawNaniteToxicCloud(consumer, pose, camPos, right, up, beam, elapsed, anim, veilState);
                    }
                }
                case 0xFFD700 -> { // 🛡️ 6: 궤도 드롭 방어막 포드 (Shield Pod)
                    if (elapsed < 140) {
                        drawDeployableShieldDeployment(consumer, pose, camPos, right, up, beam, elapsed, anim, veilState);
                    }
                }
                default -> { // 💥 0: 고폭 열폭풍탄 (HE)
                    if (elapsed < 160) {
                        drawCascadingThermalStorm(consumer, pose, camPos, right, up, beam, elapsed, anim);
                    }
                    if (elapsed < 360) {
                        drawCinematicNuclearCloud(consumer, pose, camPos, right, up, beam, elapsed, anim);
                    }
                }
            }

            // 3. 궤도 레이저 기둥 렌더링
            int coreR = Math.min(255, (r + 255) / 2);
            int coreG = Math.min(255, (g + 255) / 2);
            int coreB = Math.min(255, (b + 255) / 2);

            int coreAlpha = (int) (240 * fadeAlpha);
            int glowAlpha = (int) (180 * fadeAlpha);

            double startY = beam.y;
            double endY = Math.min(320.0, startY + 256.0);

            boolean isHE = (beam.color == 0xFF2222);

            // 내부 코어 빔
            drawVerticalBeam(consumer, pose, camPos, beam.x, beam.z, startY, endY, !isHE ? 0.25f : 0.4f, coreR, coreG, coreB, coreAlpha, anim);
            // 외부 글로우 빔
            drawVerticalBeam(consumer, pose, camPos, beam.x, beam.z, startY, endY, !isHE ? 0.65f : 0.95f, r, g, b, glowAlpha, -anim * 0.7f);
        }

        if (context.consumers() instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch(RenderType.lightning());
        }
    }

    // =========================================================================
    // ⚡ Type 1: EMP 전자기 펄스탄 전용 렌더링 (고고도 방전 구체 + 번개 아크 + 전자기 서킷 파동)
    // =========================================================================
    private static void drawEmpTacticalDischarge(
            VertexConsumer consumer, PoseStack.Pose pose, Vec3 camPos, Vector3f right, Vector3f up,
            ActiveBeam beam, int elapsed, float anim, dev.minse.interiorveil.network.ForcefieldStatePayload veilState
    ) {
        double empCenterY = beam.y + 200.0;
        double startY = beam.y;
        double endY = Math.min(320.0, startY + 256.0);
        double beamHeight = endY - startY;

        // 1. 수직 레일건 가속 코일 링 (6개)
        int coilCount = 6;
        for (int c = 0; c < coilCount; c++) {
            double ringY = endY - ((elapsed * 9.0 + c * (beamHeight / coilCount)) % beamHeight);
            if (ringY < startY || ringY > endY) continue;
            float coilFade = (float) Math.sin((ringY - startY) / beamHeight * Math.PI);
            float coilRadius = (float) (2.5 + Math.sin(c * 1.5 + anim * 2.0) * 0.8);
            int coilNodes = 16;
            for (int i = 0; i < coilNodes; i++) {
                double angle = (i * 2.0 * Math.PI / coilNodes) + anim * 0.2;
                double px = beam.x + Math.cos(angle) * coilRadius;
                double pz = beam.z + Math.sin(angle) * coilRadius;
                if (isInsideVeil(px, pz, veilState)) continue;
                drawSoftPuff(consumer, pose, camPos, px, ringY, pz, right, up, 1.6f, 0, 229, 255, (int) (240 * coilFade));
                drawSoftPuff(consumer, pose, camPos, px, ringY, pz, right, up, 0.8f, 255, 255, 255, (int) (255 * coilFade));
            }
        }

        // 2. 상공 200m EMP 고전압 테슬라 방전 구체
        for (int i = 0; i < 24; i++) {
            double a1 = i * Math.PI / 12.0 + anim * 0.4;
            double a2 = i * 2.0 * Math.PI / 24.0 - anim * 0.3;
            double sx = beam.x + Math.cos(a1) * Math.sin(a2) * 8.0;
            double sy = empCenterY + Math.cos(a2) * 8.0;
            double sz = beam.z + Math.sin(a1) * Math.sin(a2) * 8.0;
            drawSoftPuff(consumer, pose, camPos, sx, sy, sz, right, up, 3.5f, 0, 229, 255, 220);
            drawSoftPuff(consumer, pose, camPos, sx, sy, sz, right, up, 1.5f, 255, 255, 255, 255);
        }

        // 3. 상공 200m 6중 3D 토러스 마하 링
        for (int r = 0; r < 6; r++) {
            int ringElapsed = elapsed - r * 8;
            if (ringElapsed >= 0 && ringElapsed < 60) {
                float p = (float) ringElapsed / 60.0f;
                double radius = Math.pow(p, 0.58) * 280.0;
                float alpha = (1.0f - p) * 0.95f;
                if (alpha > 0.01f && radius > 0.5) {
                    drawGlowingTorusRing(consumer, pose, camPos, beam.x, empCenterY, beam.z, radius, 2.4f, 0, 229, 255, (int) (240 * alpha), veilState);
                    drawGlowingTorusRing(consumer, pose, camPos, beam.x, empCenterY, beam.z, radius, 1.0f, 255, 255, 255, (int) (255 * alpha), veilState);
                }
            }
        }

        // 4. 지표면 6중 전자기 서킷 파동
        for (int grIdx = 0; grIdx < 6; grIdx++) {
            int gElapsed = elapsed - grIdx * 7;
            if (gElapsed >= 0 && gElapsed < 50) {
                float gp = (float) gElapsed / 50.0f;
                double gr = Math.pow(gp, 0.55) * 220.0;
                float ga = (1.0f - gp) * 0.95f;
                if (ga > 0.01f && gr > 0.5) {
                    float ringWidth = (float) (2.5 + gp * 6.0);
                    drawFlatGroundWaveRing(consumer, pose, camPos, beam.x, beam.y + 0.15, beam.z, gr, ringWidth, 0, 229, 255, (int) (240 * ga), veilState);
                    drawFlatGroundWaveRing(consumer, pose, camPos, beam.x, beam.y + 0.18, beam.z, gr, ringWidth * 0.35f, 255, 255, 255, (int) (255 * ga), veilState);
                }
            }
        }
    }

    // =========================================================================
    // 📦 Type 2: 궤도 보급 포드 전용 렌더링 (4방향 유도 비콘 + 회전 랜딩 패드 + 감속 제트)
    // =========================================================================
    private static void drawSupplyDropPod(
            VertexConsumer consumer, PoseStack.Pose pose, Vec3 camPos, Vector3f right, Vector3f up,
            ActiveBeam beam, int elapsed, float anim, dev.minse.interiorveil.network.ForcefieldStatePayload veilState
    ) {
        // 1. 착탄 지점을 향해 꽂히는 4방향 대각선 유도 비콘 라인 (Tactical Landing Laser Beacons)
        int beaconDist = 24;
        double[][] beaconOrigins = {
                {beam.x + beaconDist, beam.z + beaconDist},
                {beam.x - beaconDist, beam.z + beaconDist},
                {beam.x + beaconDist, beam.z - beaconDist},
                {beam.x - beaconDist, beam.z - beaconDist}
        };
        for (double[] bPos : beaconOrigins) {
            for (int step = 0; step < 20; step++) {
                double t = (double) step / 20.0;
                double lx = bPos[0] + (beam.x - bPos[0]) * t;
                double lz = bPos[1] + (beam.z - bPos[1]) * t;
                double ly = (beam.y + 40.0 * (1.0 - t));
                drawSoftPuff(consumer, pose, camPos, lx, ly, lz, right, up, 0.6f, 85, 255, 85, 200);
            }
        }

        // 2. 3중 회전 전술 랜딩 패드 홀로그램 링 (Holographic Landing Pad)
        double[] padRadii = {4.0, 8.0, 14.0};
        for (int pIdx = 0; pIdx < padRadii.length; pIdx++) {
            double r = padRadii[pIdx];
            float rotDir = (pIdx % 2 == 0) ? 1.0f : -1.0f;
            int nodes = 24;
            for (int i = 0; i < nodes; i++) {
                double angle = (i * 2.0 * Math.PI / nodes) + anim * rotDir * 0.5;
                double px = beam.x + Math.cos(angle) * r;
                double pz = beam.z + Math.sin(angle) * r;
                drawSoftPuff(consumer, pose, camPos, px, beam.y + 0.2, pz, right, up, 0.8f, 85, 255, 85, 220);
                if (i % 6 == 0) {
                    // 패드 코너 마커 포인트
                    drawSoftPuff(consumer, pose, camPos, px, beam.y + 0.4, pz, right, up, 1.4f, 255, 255, 255, 255);
                }
            }
        }

        // 3. 착탄 시 방출되는 냉각 증기 및 감압 링 (Cooling Vent Steam Waves)
        for (int v = 0; v < 3; v++) {
            int vElapsed = elapsed - v * 12;
            if (vElapsed >= 0 && vElapsed < 40) {
                float vp = (float) vElapsed / 40.0f;
                double vr = Math.pow(vp, 0.6) * 20.0;
                float va = (1.0f - vp) * 0.8f;
                drawFlatGroundWaveRing(consumer, pose, camPos, beam.x, beam.y + 0.1, beam.z, vr, 1.5f, 220, 255, 220, (int) (220 * va), veilState);
            }
        }
    }

    // =========================================================================
    // ❄️ Type 3: 극저온 동결탄 전용 렌더링 (서리 크리스탈 스파이어 + 6각 눈꽃 결정 룬 + 빙결 파동)
    // =========================================================================
    private static void drawCryoBlizzardSpire(
            VertexConsumer consumer, PoseStack.Pose pose, Vec3 camPos, Vector3f right, Vector3f up,
            ActiveBeam beam, int elapsed, float anim, dev.minse.interiorveil.network.ForcefieldStatePayload veilState
    ) {
        double startY = beam.y;
        double endY = Math.min(320.0, startY + 256.0);
        double beamHeight = endY - startY;

        // 1. 수직 6각 기둥 서리 크리스탈 스파이어 (Glacial Crystal Spire)
        int spireSlices = 36;
        for (int s = 0; s < spireSlices; s++) {
            double t = (double) s / spireSlices;
            double sy = startY + t * beamHeight;
            double baseR = (1.0 - t * 0.6) * 3.5;
            for (int i = 0; i < 6; i++) {
                double angle = i * Math.PI / 3.0 + anim * 0.15;
                double px = beam.x + Math.cos(angle) * baseR;
                double pz = beam.z + Math.sin(angle) * baseR;
                if (isInsideVeil(px, pz, veilState)) continue;
                drawSoftPuff(consumer, pose, camPos, px, sy, pz, right, up, 2.0f, 128, 216, 255, 210);
                drawSoftPuff(consumer, pose, camPos, px, sy, pz, right, up, 0.9f, 255, 255, 255, 255);
            }
        }

        // 2. 바닥 6방향 대칭 프랙탈 눈꽃 결정 룬 그리드 (Hexagonal Fractal Snowflake Grid)
        for (int arm = 0; arm < 6; arm++) {
            double armAngle = arm * Math.PI / 3.0;
            double armLength = Math.min(48.0, 4.0 + elapsed * 1.8);
            for (double d = 2.0; d < armLength; d += 2.0) {
                double px = beam.x + Math.cos(armAngle) * d;
                double pz = beam.z + Math.sin(armAngle) * d;
                if (isInsideVeil(px, pz, veilState)) continue;
                drawSoftPuff(consumer, pose, camPos, px, beam.y + 0.15, pz, right, up, 1.2f, 128, 216, 255, 230);

                // 눈꽃 결정 가지 (Branches)
                if ((int) d % 6 == 0) {
                    for (int side = -1; side <= 1; side += 2) {
                        double bAngle = armAngle + side * (Math.PI / 4.0);
                        double bx = px + Math.cos(bAngle) * 3.0;
                        double bz = pz + Math.sin(bAngle) * 3.0;
                        if (!isInsideVeil(bx, bz, veilState)) {
                            drawSoftPuff(consumer, pose, camPos, bx, beam.y + 0.15, bz, right, up, 0.8f, 200, 240, 255, 240);
                        }
                    }
                }
            }
        }

        // 3. 지표면 급속 결빙 서리 충격파 링 (Frost Shockwave Rings)
        for (int fr = 0; fr < 5; fr++) {
            int fElapsed = elapsed - fr * 8;
            if (fElapsed >= 0 && fElapsed < 45) {
                float fp = (float) fElapsed / 45.0f;
                double frad = Math.pow(fp, 0.55) * 64.0;
                float fa = (1.0f - fp) * 0.9f;
                drawFlatGroundWaveRing(consumer, pose, camPos, beam.x, beam.y + 0.12, beam.z, frad, 3.0f, 128, 216, 255, (int) (230 * fa), veilState);
                drawFlatGroundWaveRing(consumer, pose, camPos, beam.x, beam.y + 0.15, beam.z, frad, 1.0f, 255, 255, 255, (int) (255 * fa), veilState);
            }
        }
    }

    // =========================================================================
    // 🕳️ Type 4: 중력 특이점 탄 전용 렌더링 (블랙홀 코어 + 3중 강착원반 + 12개 중력 흡입 와류)
    // =========================================================================
    private static void drawGravitySingularity(
            VertexConsumer consumer, PoseStack.Pose pose, Vec3 camPos, Vector3f right, Vector3f up,
            ActiveBeam beam, int elapsed, float anim, dev.minse.interiorveil.network.ForcefieldStatePayload veilState
    ) {
        double coreY = beam.y + 3.0;

        // 1. 중심부 칠흑 같은 암흑 블랙홀 구체 (Dark Event Horizon Core)
        for (int lat = -8; lat <= 8; lat++) {
            double phi = lat * Math.PI / 16.0;
            double rRing = Math.cos(phi) * 4.5;
            double yRing = coreY + Math.sin(phi) * 4.5;
            for (int lon = 0; lon < 16; lon++) {
                double theta = lon * 2.0 * Math.PI / 16.0;
                double px = beam.x + Math.cos(theta) * rRing;
                double pz = beam.z + Math.sin(theta) * rRing;
                drawSoftPuff(consumer, pose, camPos, px, yRing, pz, right, up, 2.2f, 20, 0, 40, 255); // 칠흑 같은 암흑
                drawSoftPuff(consumer, pose, camPos, px, yRing, pz, right, up, 1.2f, 148, 0, 211, 200); // 보라색 경계광
            }
        }

        // 2. 서로 다른 3개 축으로 맹렬히 회전하는 3중 강착원반 (3-Axis Accretion Disks)
        double[] diskRadii = {9.0, 16.0, 24.0};
        for (int d = 0; d < 3; d++) {
            double diskR = diskRadii[d];
            double tiltAngle = (d - 1) * (Math.PI / 4.0); // -45도, 0도, +45도 기울임
            int nodes = 32;
            for (int i = 0; i < nodes; i++) {
                double a = i * 2.0 * Math.PI / nodes + anim * (2.0 - d * 0.5);
                double lx = Math.cos(a) * diskR;
                double lz = Math.sin(a) * diskR;
                double ly = Math.sin(a) * Math.sin(tiltAngle) * diskR * 0.4;
                double px = beam.x + lx;
                double py = coreY + ly;
                double pz = beam.z + lz;
                drawSoftPuff(consumer, pose, camPos, px, py, pz, right, up, 2.5f, 148, 0, 211, 240);
                drawSoftPuff(consumer, pose, camPos, px, py, pz, right, up, 1.0f, 255, 128, 255, 255);
            }
        }

        // 3. 주변 48m 공간에서 중심으로 나선을 그리며 빨려 들어가는 12개 중력 흡입 와류선 (Inward Gravitational Stream Lines)
        int streamCount = 12;
        for (int s = 0; s < streamCount; s++) {
            double baseAngle = s * 2.0 * Math.PI / streamCount;
            for (int step = 0; step < 24; step++) {
                double t = (double) step / 24.0;
                double dist = 4.5 + t * 44.0; // 4.5m ~ 48m
                double spiralAngle = baseAngle + t * 5.0 * Math.PI - anim * 1.5;
                double px = beam.x + Math.cos(spiralAngle) * dist;
                double pz = beam.z + Math.sin(spiralAngle) * dist;
                double py = coreY + (Math.sin(t * Math.PI) * 6.0 * (1.0 - t));
                if (isInsideVeil(px, pz, veilState)) continue;

                int alpha = (int) (220 * (1.0 - t * 0.6));
                drawSoftPuff(consumer, pose, camPos, px, py, pz, right, up, 1.5f, 100, 0, 180, alpha);
            }
        }
    }

    // =========================================================================
    // ☣️ Type 5: 나노 독소 / 낙진탄 전용 렌더링 (생화학 버블 안개 돔 + 바이오하자드 링 + 저고도 낙진 안개)
    // =========================================================================
    private static void drawNaniteToxicCloud(
            VertexConsumer consumer, PoseStack.Pose pose, Vec3 camPos, Vector3f right, Vector3f up,
            ActiveBeam beam, int elapsed, float anim, dev.minse.interiorveil.network.ForcefieldStatePayload veilState
    ) {
        float life = Math.min(1.0f, (float) elapsed / 160.0f);
        double fogRadius = Math.min(48.0, 8.0 + life * 40.0);

        // 1. 유독성 생화학 나노 안개 돔 (Bubbling Bio-Toxic Fog Dome)
        int bubbleCount = 48;
        for (int i = 0; i < bubbleCount; i++) {
            double angle = (i * 2.0 * Math.PI / bubbleCount) + Math.sin(i * 1.5 + anim) * 0.2;
            double r = Math.sin(i * 3.7 + anim * 0.5) * 0.4 * fogRadius + fogRadius * 0.6;
            double px = beam.x + Math.cos(angle) * r;
            double pz = beam.z + Math.sin(angle) * r;
            double py = beam.y + 1.0 + Math.abs(Math.sin(i * 2.1 + anim * 0.8)) * 6.0;
            if (isInsideVeil(px, pz, veilState)) continue;

            float puffSize = (float) (3.0 + Math.sin(i + anim) * 1.2);
            drawSoftPuff(consumer, pose, camPos, px, py, pz, right, up, puffSize, 0, 255, 102, 190);
            drawSoftPuff(consumer, pose, camPos, px, py, pz, right, up, puffSize * 0.4f, 180, 255, 200, 230);
        }

        // 2. 3중 회전 바이오하자드 삼각 링 (Rotating Biohazard Tri-Ring)
        for (int tr = 0; tr < 3; tr++) {
            double triAngle = tr * 2.0 * Math.PI / 3.0 + anim * 0.4;
            double triDist = fogRadius * 0.7;
            double cx = beam.x + Math.cos(triAngle) * triDist;
            double cz = beam.z + Math.sin(triAngle) * triDist;
            if (isInsideVeil(cx, cz, veilState)) continue;
            for (int a = 0; a < 16; a++) {
                double sa = a * 2.0 * Math.PI / 16.0;
                double px = cx + Math.cos(sa) * 5.0;
                double pz = cz + Math.sin(sa) * 5.0;
                drawSoftPuff(consumer, pose, camPos, px, beam.y + 1.5, pz, right, up, 1.2f, 0, 255, 102, 210);
            }
        }

        // 3. 지표면에 바짝 깔려 출렁이는 6중 나노 낙진 안개 장막
        for (int gr = 0; gr < 6; gr++) {
            double grDist = (gr + 1) * (fogRadius / 6.0);
            drawFlatGroundWaveRing(consumer, pose, camPos, beam.x, beam.y + 0.1, beam.z, grDist, 4.0f, 0, 255, 102, 180, veilState);
        }
    }

    // =========================================================================
    // 🛡️ Type 6: 궤도 드롭 방어막 포드 전용 렌더링 (황금빛 육각 허니컴 보호막 메쉬 + 4방향 앵커 빔)
    // =========================================================================
    private static void drawDeployableShieldDeployment(
            VertexConsumer consumer, PoseStack.Pose pose, Vec3 camPos, Vector3f right, Vector3f up,
            ActiveBeam beam, int elapsed, float anim, dev.minse.interiorveil.network.ForcefieldStatePayload veilState
    ) {
        double domeRadius = 16.0; // 16m 방어 돔

        // 1. 4개 코너 앵커 안정화 역장 빔 (Golden Anchor Field Vertical Beams)
        double anchorDist = domeRadius * 1.05;
        double[][] anchors = {
                {beam.x + anchorDist, beam.z},
                {beam.x - anchorDist, beam.z},
                {beam.x, beam.z + anchorDist},
                {beam.x, beam.z - anchorDist}
        };
        for (double[] anc : anchors) {
            double startY = beam.y;
            double endY = beam.y + 14.0;
            // 앵커 내부 코어
            drawVerticalBeam(consumer, pose, camPos, anc[0], anc[1], startY, endY, 0.2f, 255, 255, 255, 220, anim);
            // 앵커 외부 황금빛 글로우
            drawVerticalBeam(consumer, pose, camPos, anc[0], anc[1], startY, endY, 0.5f, 255, 215, 0, 180, -anim * 0.5f);
        }

        // 2. 지표면 황금빛 결계 안정화 룬 링
        drawFlatGroundWaveRing(consumer, pose, camPos, beam.x, beam.y + 0.15, beam.z, domeRadius, 2.0f, 255, 215, 0, 240, veilState);
        drawFlatGroundWaveRing(consumer, pose, camPos, beam.x, beam.y + 0.18, beam.z, domeRadius, 0.6f, 255, 255, 255, 255, veilState);
    }

    private static boolean isInsideVeil(double x, double z, dev.minse.interiorveil.network.ForcefieldStatePayload veilState) {
        if (veilState == null || !veilState.active()) return false;
        for (dev.minse.interiorveil.network.ForcefieldStatePayload.DomeEntry dome : veilState.domes()) {
            double dx = x - dome.centerX();
            double dz = z - dome.centerZ();
            if ((dx * dx + dz * dz) < (dome.radius() * dome.radius())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 지형 표면(Terrain Heightmap)의 굴곡과 언덕, 산등성이를 실시간으로 완벽하게 타고 흐르는 동심원 충격파 링을 렌더링한다.
     * 결계 내부로 파고들지 못하도록 결계 외벽에서 깔끔하게 차단(Clipping)된다.
     */
    private static void drawFlatGroundWaveRing(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 camPos,
            double cx,
            double cy,
            double cz,
            double radius,
            float width,
            int r,
            int g,
            int b,
            int a,
            dev.minse.interiorveil.network.ForcefieldStatePayload veilState
    ) {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        int segments = Math.max(32, Math.min(64, (int) (radius * 0.8)));
        double innerR = Math.max(0.0, radius - width * 0.5);
        double outerR = radius + width * 0.5;

        for (int i = 0; i < segments; i++) {
            double a1 = i * 2.0 * Math.PI / segments;
            double a2 = (i + 1) * 2.0 * Math.PI / segments;

            float sin1 = (float) Math.sin(a1);
            float cos1 = (float) Math.cos(a1);
            float sin2 = (float) Math.sin(a2);
            float cos2 = (float) Math.cos(a2);

            // 1. 세그먼트 1의 실제 지형 표면 Y 높이 샘플링
            double wx1 = cx + radius * cos1;
            double wz1 = cz + radius * sin1;

            // 2. 세그먼트 2의 실제 지형 표면 Y 높이 샘플링
            double wx2 = cx + radius * cos2;
            double wz2 = cz + radius * sin2;

            // 결계 내부 침투 완벽 차단: 결계 내부 좌표에 속한 세그먼트는 렌더링 스킵
            if (isInsideVeil(wx1, wz1, veilState) || isInsideVeil(wx2, wz2, veilState)) {
                continue;
            }

            double groundY1 = cy;
            if (level != null) {
                int bx1 = (int) Math.floor(wx1);
                int bz1 = (int) Math.floor(wz1);
                int h1 = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, bx1, bz1);
                if (h1 > level.getMinY()) {
                    groundY1 = h1 + 0.15;
                }
            }

            double groundY2 = cy;
            if (level != null) {
                int bx2 = (int) Math.floor(wx2);
                int bz2 = (int) Math.floor(wz2);
                int h2 = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, bx2, bz2);
                if (h2 > level.getMinY()) {
                    groundY2 = h2 + 0.15;
                }
            }

            float y1 = (float) (groundY1 - camPos.y);
            float y2 = (float) (groundY2 - camPos.y);

            float x1 = (float) (cx + innerR * cos1 - camPos.x);
            float z1 = (float) (cz + innerR * sin1 - camPos.z);

            float x2 = (float) (cx + outerR * cos1 - camPos.x);
            float z2 = (float) (cz + outerR * sin1 - camPos.z);

            float x3 = (float) (cx + outerR * cos2 - camPos.x);
            float z3 = (float) (cz + outerR * sin2 - camPos.z);

            float x4 = (float) (cx + innerR * cos2 - camPos.x);
            float z4 = (float) (cz + innerR * sin2 - camPos.z);

            // 상단/하단 양면 렌더링 (지형 표면에 완벽하게 밀착되어 굴곡을 타고 흐름)
            consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
            consumer.addVertex(pose, x2, y1, z2).setColor(r, g, b, a);
            consumer.addVertex(pose, x3, y2, z3).setColor(r, g, b, a);
            consumer.addVertex(pose, x4, y2, z4).setColor(r, g, b, a);

            consumer.addVertex(pose, x4, y2, z4).setColor(r, g, b, a);
            consumer.addVertex(pose, x3, y2, z3).setColor(r, g, b, a);
            consumer.addVertex(pose, x2, y1, z2).setColor(r, g, b, a);
            consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        }
    }

    /**
     * 오르비탈 레일건 스타일의 3D 입체 발광 네온 도넛 튜브 링(Torus Tube Mesh)을 렌더링한다.
     * 결계 내부로 파고들지 못하도록 결계 외벽에서 깔끔하게 차단(Clipping)된다.
     */
    private static void drawGlowingTorusRing(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 camPos,
            double cx,
            double cy,
            double cz,
            double mainRadius,
            float tubeRadius,
            int r,
            int g,
            int b,
            int a,
            dev.minse.interiorveil.network.ForcefieldStatePayload veilState
    ) {
        int mainSegments = Math.max(24, Math.min(48, (int) (mainRadius * 0.6)));
        int tubeSegments = 4; // 4각형 다이아몬드 단면 튜브 (초경량 3D 볼륨)

        for (int i = 0; i < mainSegments; i++) {
            double u1 = i * 2.0 * Math.PI / mainSegments;
            double u2 = (i + 1) * 2.0 * Math.PI / mainSegments;

            float cosU1 = (float) Math.cos(u1);
            float sinU1 = (float) Math.sin(u1);
            float cosU2 = (float) Math.cos(u2);
            float sinU2 = (float) Math.sin(u2);

            double wx1 = cx + mainRadius * cosU1;
            double wz1 = cz + mainRadius * sinU1;
            double wx2 = cx + mainRadius * cosU2;
            double wz2 = cz + mainRadius * sinU2;

            // 결계 내부 침투 완벽 차단
            if (isInsideVeil(wx1, wz1, veilState) || isInsideVeil(wx2, wz2, veilState)) {
                continue;
            }

            for (int j = 0; j < tubeSegments; j++) {
                double v1 = j * 2.0 * Math.PI / tubeSegments;
                double v2 = (j + 1) * 2.0 * Math.PI / tubeSegments;

                float cosV1 = (float) Math.cos(v1);
                float sinV1 = (float) Math.sin(v1);
                float cosV2 = (float) Math.cos(v2);
                float sinV2 = (float) Math.sin(v2);

                // 3D 토러스 공식: P(u, v) = ((R + r * cos(v)) * cos(u), r * sin(v), (R + r * cos(v)) * sin(u))
                float r1 = (float) (mainRadius + tubeRadius * cosV1);
                float r2 = (float) (mainRadius + tubeRadius * cosV2);

                float x1 = (float) (cx + r1 * cosU1 - camPos.x);
                float y1 = (float) (cy + tubeRadius * sinV1 - camPos.y);
                float z1 = (float) (cz + r1 * sinU1 - camPos.z);

                float x2 = (float) (cx + r2 * cosU1 - camPos.x);
                float y2 = (float) (cy + tubeRadius * sinV2 - camPos.y);
                float z2 = (float) (cz + r2 * sinU1 - camPos.z);

                float x3 = (float) (cx + r2 * cosU2 - camPos.x);
                float y3 = (float) (cy + tubeRadius * sinV2 - camPos.y);
                float z3 = (float) (cz + r2 * sinU2 - camPos.z);

                float x4 = (float) (cx + r1 * cosU2 - camPos.x);
                float y4 = (float) (cy + tubeRadius * sinV1 - camPos.y);
                float z4 = (float) (cz + r1 * sinU2 - camPos.z);

                consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
                consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
                consumer.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
                consumer.addVertex(pose, x4, y4, z4).setColor(r, g, b, a);
            }
        }
    }

    /**
     * 3단계 다중 연속 충격파 파동 및 짙은 볼륨 화염/모래폭풍 구름 렌더링 엔진.
     */
    private static void drawCascadingThermalStorm(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 camPos,
            Vector3f right,
            Vector3f up,
            ActiveBeam beam,
            int elapsed,
            float anim
    ) {
        dev.minse.interiorveil.network.ForcefieldStatePayload veilState = VeilForcefieldRenderer.getCurrentState();

        // --- Wave 1: 1차 초음속 마하 파면 (선봉 눈부신 황금빛 폭풍 구름 덩어리들: elapsed 0~75) ---
        if (elapsed < 75) {
            float p1 = (float) elapsed / 75.0f;
            double r1 = Math.pow(p1, 0.65) * 250.0;
            float a1 = (1.0f - p1) * 0.95f;
            if (a1 > 0.01f && r1 > 1.0) {
                int nodes = 48;
                for (int i = 0; i < nodes; i++) {
                    double angle = (i * 2.0 * Math.PI / nodes) + anim * 0.08;
                    double px = beam.x + Math.cos(angle) * r1;
                    double pz = beam.z + Math.sin(angle) * r1;
                    double py = beam.y + 1.8 + Math.sin(i * 2.0 + anim) * 2.5;
                    float size = (float) (14.0 + p1 * 26.0);

                    double[] p = new double[]{px, py, pz};
                    if (filterOrDeflectByVeil(p, size, veilState)) {
                        drawSoftPuff(consumer, pose, camPos, p[0], p[1], p[2], right, up, size, 255, 210, 80, (int) (245 * a1));
                    }
                    double[] pGround = new double[]{px, beam.y + 0.6, pz};
                    if (filterOrDeflectByVeil(pGround, size * 0.75f, veilState)) {
                        drawSoftPuff(consumer, pose, camPos, pGround[0], pGround[1], pGround[2], right, up, size * 0.75f, 255, 140, 30, (int) (220 * a1));
                    }
                }
            }
        }

        // --- Wave 2: 2차 거대 화염/용암 열폭풍 구름 (중화염 폭풍 파동: elapsed 15~130) ---
        if (elapsed >= 15 && elapsed < 130) {
            float p2 = (float) (elapsed - 15) / 115.0f;
            double r2 = Math.pow(p2, 0.72) * 210.0;
            float a2 = (1.0f - p2) * 0.95f;
            if (a2 > 0.01f && r2 > 1.0) {
                int nodes = 42;
                for (int i = 0; i < nodes; i++) {
                    double angle = (i * 2.0 * Math.PI / nodes) - anim * 0.06;
                    double px = beam.x + Math.cos(angle) * r2;
                    double pz = beam.z + Math.sin(angle) * r2;
                    double py = beam.y + 3.0 + Math.sin(angle * 3.0 + anim) * 3.5;
                    float size = (float) (18.0 + p2 * 30.0);

                    double[] p = new double[]{px, py, pz};
                    if (filterOrDeflectByVeil(p, size, veilState)) {
                        drawSoftPuff(consumer, pose, camPos, p[0], p[1], p[2], right, up, size, 255, 75, 10, (int) (250 * a2));
                    }
                    double[] pUpper = new double[]{px, py + 5.0, pz};
                    if (filterOrDeflectByVeil(pUpper, size * 0.85f, veilState)) {
                        drawSoftPuff(consumer, pose, camPos, pUpper[0], pUpper[1], pUpper[2], right, up, size * 0.85f, 200, 45, 5, (int) (200 * a2));
                    }
                }
            }
        }

        // --- Wave 3: 3차 지상 흑갈색 화산재 흙먼지 쇄도 구름 (후폭풍: elapsed 35~180) ---
        if (elapsed >= 35 && elapsed < 180) {
            float p3 = (float) (elapsed - 35) / 145.0f;
            double r3 = Math.pow(p3, 0.8) * 180.0;
            float a3 = (1.0f - p3) * 0.85f;
            if (a3 > 0.01f && r3 > 1.0) {
                int nodes = 38;
                for (int i = 0; i < nodes; i++) {
                    double angle = (i * 2.0 * Math.PI / nodes) + anim * 0.04;
                    double px = beam.x + Math.cos(angle) * r3;
                    double pz = beam.z + Math.sin(angle) * r3;
                    double py = beam.y + 2.5 + Math.sin(i * 1.5 + anim) * 3.0;
                    float size = (float) (16.0 + p3 * 28.0);

                    double[] p = new double[]{px, py, pz};
                    if (filterOrDeflectByVeil(p, size, veilState)) {
                        drawSoftPuff(consumer, pose, camPos, p[0], p[1], p[2], right, up, size, 55, 42, 32, (int) (230 * a3));
                    }
                    double[] pGround = new double[]{px, beam.y + 1.0, pz};
                    if (filterOrDeflectByVeil(pGround, size * 0.9f, veilState)) {
                        drawSoftPuff(consumer, pose, camPos, pGround[0], pGround[1], pGround[2], right, up, size * 0.9f, 40, 30, 22, (int) (210 * a3));
                    }
                }
            }
        }

        // --- 4. 방사형 회전 화염 스트림 (Radial Swirling Fire Tendrils: 8갈래) ---
        if (elapsed < 110) {
            float progress = (float) elapsed / 110.0f;
            float streamAlpha = (1.0f - progress) * 0.9f;
            int arms = 8;
            for (int a = 0; a < arms; a++) {
                double baseAngle = (a * 2.0 * Math.PI / arms) + anim * 0.15;
                for (int seg = 1; seg <= 6; seg++) {
                    float sFrac = (float) seg / 6.0f;
                    double r = sFrac * (Math.pow(progress, 0.7) * 190.0);
                    double spiralAngle = baseAngle + sFrac * 0.8;
                    double px = beam.x + Math.cos(spiralAngle) * r;
                    double pz = beam.z + Math.sin(spiralAngle) * r;
                    double py = beam.y + 1.2 + Math.sin(seg + anim) * 1.8;
                    float size = 9.0f + sFrac * 14.0f;
                    int aVal = (int) (230 * streamAlpha * (1.0f - sFrac * 0.3f));

                    double[] p = new double[]{px, py, pz};
                    if (filterOrDeflectByVeil(p, size, veilState)) {
                        drawSoftPuff(consumer, pose, camPos, p[0], p[1], p[2], right, up, size, 255, 120, 20, aVal);
                    }
                }
            }
        }
    }

    /**
     * 결계가 존재할 때, 폭풍 구름 덩어리가 결계 내부를 침범하지 못하도록 외벽 표면으로 굴절시키거나 내부 침투 시 렌더링을 차단한다.
     */
    private static boolean filterOrDeflectByVeil(
            double[] pos,
            float puffRadius,
            dev.minse.interiorveil.network.ForcefieldStatePayload veilState
    ) {
        if (veilState == null) return true;

        double dx = pos[0] - veilState.centerX();
        double dz = pos[2] - veilState.centerZ();
        double distSq = dx * dx + dz * dz;
        double vRadius = veilState.radius();

        // 1. 구름 중심이 결계 내부 완전히 안쪽에 있을 때 -> 결계 외벽 표면으로 밀어냄
        if (distSq < vRadius * vRadius) {
            double dist = Math.sqrt(distSq);
            if (dist > 0.01) {
                double nx = dx / dist;
                double nz = dz / dist;
                pos[0] = veilState.centerX() + nx * (vRadius + puffRadius * 0.5 + 0.5);
                pos[2] = veilState.centerZ() + nz * (vRadius + puffRadius * 0.5 + 0.5);
                pos[1] = Math.max(pos[1], veilState.centerY() + Math.random() * 2.0);
                return true;
            } else {
                return false; // 중심점은 스킵
            }
        }

        // 2. 구름 중심은 결계 바깥이지만 구름 반경이 결계 내부로 파고들 때 -> 바깥으로 밀착 조정
        double dist = Math.sqrt(distSq);
        if (dist < vRadius + puffRadius * 0.7) {
            double nx = dx / dist;
            double nz = dz / dist;
            pos[0] = veilState.centerX() + nx * (vRadius + puffRadius * 0.7 + 0.3);
            pos[2] = veilState.centerZ() + nz * (vRadius + puffRadius * 0.7 + 0.3);
        }

        return true;
    }

    /**
     * 실사 핵폭발(비키니 환초 수소폭탄) 형태를 재현한 시네마틱 3D 볼륨 버섯구름 렌더링 엔진.
     */
    private static void drawCinematicNuclearCloud(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 camPos,
            Vector3f right,
            Vector3f up,
            ActiveBeam beam,
            int elapsed,
            float anim
    ) {
        float lifeProgress = Math.min(1.0f, (float) elapsed / 360.0f);
        // 전체 페이드 인/아웃 (최소 0.35 이상 유지하여 결계 안에서도 선명하게 투과)
        float cloudAlpha = (elapsed < 25)
                ? (float) elapsed / 25.0f
                : (1.0f - (float) Math.pow(lifeProgress, 1.2f));

        if (cloudAlpha <= 0.01f) return;

        // 시간에 따른 높이 및 갓 반경 성장 (하늘 높이 솟구치는 거대 버섯 돔)
        double capHeight = beam.y + 45.0 + Math.min(120.0, Math.pow(elapsed * 0.25, 0.85) * 35.0);
        double capMaxRadius = 15.0 + Math.min(60.0, Math.pow(elapsed * 0.22, 0.8) * 25.0);

        // --- 1. 기저부 지상 응결 충격파 링 (Ground Condensation Skirt) ---
        if (elapsed < 140) {
            float skirtProgress = (float) elapsed / 140.0f;
            float skirtAlpha = cloudAlpha * (1.0f - skirtProgress) * 0.75f;
            double skirtRadius = 6.0 + skirtProgress * 32.0;
            int skirtNodes = 18;
            for (int i = 0; i < skirtNodes; i++) {
                double angle = (i * 2.0 * Math.PI / skirtNodes) + anim * 0.04;
                double px = beam.x + Math.cos(angle) * skirtRadius;
                double pz = beam.z + Math.sin(angle) * skirtRadius;
                double py = beam.y + 1.8 + Math.sin(i * 1.5 + anim) * 1.0;
                float puffR = 6.0f + skirtProgress * 5.0f;

                // 백색 수증기 및 옅은 흙먼지
                int a = (int) (170 * skirtAlpha);
                drawSoftPuff(consumer, pose, camPos, px, py, pz, right, up, puffR, 190, 185, 175, a);
            }
        }

        // --- 2. 소용돌이치는 불기둥 줄기 (Twisting Fire & Dust Stem Column) ---
        int stemLayers = 28;
        for (int i = 0; i < stemLayers; i++) {
            float hFrac = (float) i / (float) stemLayers;
            double py = beam.y + 1.5 + hFrac * (capHeight - beam.y);

            // 기둥 반경: 중간까지는 3~5블록, 상공 갓에 도달하면서 12블록으로 넓어짐
            double stemRadius = 3.2 + Math.pow(hFrac, 2.2) * 9.0;
            float puffSize = 5.0f + hFrac * 5.5f;

            // (1) 내부 코어: 타오르는 오렌지/황금빛 불기둥
            for (int j = 0; j < 2; j++) {
                double coreAngle = (j * Math.PI) + (hFrac * 6.0) + (anim * 0.4);
                double px = beam.x + Math.cos(coreAngle) * (stemRadius * 0.35);
                double pz = beam.z + Math.sin(coreAngle) * (stemRadius * 0.35);

                int cr = 255;
                int cg = (int) (110 + (1.0f - hFrac) * 70);
                int cb = (int) (15 + (1.0f - hFrac) * 30);
                int a = (int) (220 * cloudAlpha);
                drawSoftPuff(consumer, pose, camPos, px, py, pz, right, up, puffSize * 0.85f, cr, cg, cb, a);
            }

            // (2) 외부 덮개: 불기둥을 휘감는 짙은 흑갈색 흙먼지 연기
            for (int j = 0; j < 3; j++) {
                double spiralAngle = (j * 2.0 * Math.PI / 3.0) + (hFrac * 4.5) - (anim * 0.25);
                double px = beam.x + Math.cos(spiralAngle) * stemRadius;
                double pz = beam.z + Math.sin(spiralAngle) * stemRadius;

                // 짙은 흑갈색 화산재 톤
                int rVal = 52 + (i % 3) * 6;
                int gVal = 42 + (i % 3) * 5;
                int bVal = 35 + (i % 3) * 4;
                int a = (int) (210 * cloudAlpha);
                drawSoftPuff(consumer, pose, camPos, px, py, pz, right, up, puffSize, rVal, gVal, bVal, a);
            }
        }

        // --- 3. 거대 버섯 머리 갓 (Nuclear Fireball & Dark Canopy Dome) ---
        // 3-1. [내부 화염 코어]: 갓의 중심과 하단에서 뿜어져 나오는 맹렬한 불덩어리
        int fireballNodes = 14;
        for (int i = 0; i < fireballNodes; i++) {
            double angle = (i * 2.0 * Math.PI / fireballNodes) + anim * 0.15;
            double r = capMaxRadius * 0.45;
            double px = beam.x + Math.cos(angle) * r;
            double pz = beam.z + Math.sin(angle) * r;
            // 갓 하부 중심에서 이글거림
            double py = capHeight - 1.0 + Math.sin(angle * 2.0 + anim * 0.8) * 2.0;
            float puffSize = (float) (capMaxRadius * 0.55);

            // 강렬한 화염 오렌지/레드
            int cr = 255;
            int cg = 85 + (i % 3) * 35;
            int cb = 10;
            int a = (int) (240 * cloudAlpha);
            drawSoftPuff(consumer, pose, camPos, px, py, pz, right, up, puffSize, cr, cg, cb, a);
        }

        // 3-2. [중간 응결 칼라 링]: 갓 바로 아래 줄기 둘레에 형성되는 하얀 도넛 수증기 링
        int collarNodes = 16;
        for (int i = 0; i < collarNodes; i++) {
            double angle = (i * 2.0 * Math.PI / collarNodes) - anim * 0.08;
            double r = capMaxRadius * 0.65;
            double px = beam.x + Math.cos(angle) * r;
            double pz = beam.z + Math.sin(angle) * r;
            double py = capHeight - 6.0 + Math.sin(angle * 3.0 + anim) * 1.5;
            float puffSize = (float) (capMaxRadius * 0.35);

            int a = (int) (180 * cloudAlpha);
            drawSoftPuff(consumer, pose, camPos, px, py, pz, right, up, puffSize, 215, 220, 230, a);
        }

        // 3-3. [외부 흑갈색 화산재 돔 덮개]: 갓 상단과 외곽을 덮어 씌우는 거대한 돔형 먹구름
        int domeLayers = 3;
        for (int d = 0; d < domeLayers; d++) {
            float dFrac = (float) d / (float) domeLayers;
            double layerR = capMaxRadius * (0.5 + dFrac * 0.55);
            // 위로 볼록하게 솟아오른 반구형 돔 고도
            double layerY = capHeight + Math.cos(dFrac * Math.PI * 0.5) * (capMaxRadius * 0.55);

            int nodesInLayer = 12 + d * 6;
            for (int i = 0; i < nodesInLayer; i++) {
                double angle = (i * 2.0 * Math.PI / nodesInLayer) + anim * (0.06 - d * 0.02);
                double px = beam.x + Math.cos(angle) * layerR;
                double pz = beam.z + Math.sin(angle) * layerR;
                double py = layerY + Math.sin(angle * 2.0 + anim) * 1.5;
                float puffSize = (float) (capMaxRadius * 0.48);

                // 상단은 밝은 황갈색/베이지, 외곽/하단은 짙은 흑갈색 먹구름
                int rVal, gVal, bVal;
                if (d == 0) {
                    // 최상단 꼭대기 (밝은 베이지/태양빛 톤)
                    rVal = 175;
                    gVal = 155;
                    bVal = 135;
                } else {
                    // 외곽 돔 (짙은 화산재 흑갈색 먹구름)
                    rVal = 46 + (i % 3) * 6;
                    gVal = 36 + (i % 3) * 5;
                    bVal = 28 + (i % 3) * 4;
                }

                int a = (int) (225 * cloudAlpha);
                drawSoftPuff(consumer, pose, camPos, px, py, pz, right, up, puffSize, rVal, gVal, bVal, a);
            }
        }
    }

    /**
     * 카메라를 향하는 8각형 소프트 볼륨 퍼프 렌더링.
     * 중심부는 짙고 외곽은 알파 0으로 부드럽게 감쇄되어, 여러 개가 겹치면 완벽한 3D 구름 덩어리를 형성한다.
     */
    private static void drawSoftPuff(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 camPos,
            double cx, double cy, double cz,
            Vector3f right,
            Vector3f up,
            float radius,
            int r, int g, int b, int a
    ) {
        if (a <= 1) return;
        float relX = (float) (cx - camPos.x);
        float relY = (float) (cy - camPos.y);
        float relZ = (float) (cz - camPos.z);

        float rx = right.x * radius;
        float ry = right.y * radius;
        float rz = right.z * radius;

        float ux = up.x * radius;
        float uy = up.y * radius;
        float uz = up.z * radius;

        // 4개의 버텍스로 1개의 고효율 Billboard Quad 생성 (BufferBuilder 오버플로우 원천 방지)
        consumer.addVertex(pose, relX - rx - ux, relY - ry - uy, relZ - rz - uz).setColor(r, g, b, a);
        consumer.addVertex(pose, relX + rx - ux, relY + ry - uy, relZ + rz - uz).setColor(r, g, b, a);
        consumer.addVertex(pose, relX + rx + ux, relY + ry + uy, relZ + rz + uz).setColor(r, g, b, a);
        consumer.addVertex(pose, relX - rx + ux, relY - ry + uy, relZ - rz + uz).setColor(r, g, b, a);
    }

    private static void drawVerticalBeam(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 camera,
            double x,
            double z,
            double minY,
            double maxY,
            float radius,
            int r,
            int g,
            int b,
            int a,
            float rotation
    ) {
        float relX = (float) (x - camera.x);
        float relMinY = (float) (minY - camera.y);
        float relMaxY = (float) (maxY - camera.y);
        float relZ = (float) (z - camera.z);

        int sides = 8;
        for (int i = 0; i < sides; i++) {
            double angle1 = rotation + (i * 2.0 * Math.PI / sides);
            double angle2 = rotation + ((i + 1) * 2.0 * Math.PI / sides);

            float x1 = relX + (float) (Math.cos(angle1) * radius);
            float z1 = relZ + (float) (Math.sin(angle1) * radius);
            float x2 = relX + (float) (Math.cos(angle2) * radius);
            float z2 = relZ + (float) (Math.sin(angle2) * radius);

            consumer.addVertex(pose, x1, relMinY, z1).setColor(r, g, b, a);
            consumer.addVertex(pose, x2, relMinY, z2).setColor(r, g, b, a);
            consumer.addVertex(pose, x2, relMaxY, z2).setColor(r, g, b, a);
            consumer.addVertex(pose, x1, relMaxY, z1).setColor(r, g, b, a);
        }
    }

    private static final class ActiveBeam {
        final double x;
        final double y;
        final double z;
        final int maxTicks;
        int ticksRemaining;
        final int color;

        ActiveBeam(double x, double y, double z, int maxTicks, int ticksRemaining, int color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.maxTicks = maxTicks;
            this.ticksRemaining = ticksRemaining;
            this.color = color;
        }
    }
}
