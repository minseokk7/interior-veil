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
                if (isEmp) {
                    // [고고도 EMP 전용] 상공 200블럭 위에서 퍼져나가는 푸른 전기 스파크 링 파티클
                    if (elapsed < 80) {
                        float p = (float) elapsed / 80.0f;
                        double empRadius = Math.pow(p, 0.6) * 180.0;
                        double empCenterY = beam.y + 200.0;

                        for (int i = 0; i < 30; i++) {
                            double angle = Math.random() * 2.0 * Math.PI;
                            double px = beam.x + Math.cos(angle) * empRadius;
                            double pz = beam.z + Math.sin(angle) * empRadius;
                            double py = empCenterY + (Math.random() - 0.5) * 8.0;

                            level.addParticle(ParticleTypes.ELECTRIC_SPARK, px, py, pz, (Math.random() - 0.5) * 0.2, (Math.random() - 0.5) * 0.2, (Math.random() - 0.5) * 0.2);
                            if (Math.random() < 0.3) {
                                level.addParticle(ParticleTypes.GLOW, px, py, pz, 0, 0, 0);
                            }
                        }

                        // 지상으로 떨어지는 전자기 잔류 방전 스파크
                        for (int i = 0; i < 8; i++) {
                            double angle = Math.random() * 2.0 * Math.PI;
                            double gr = Math.random() * empRadius * 0.8;
                            double gx = beam.x + Math.cos(angle) * gr;
                            double gz = beam.z + Math.sin(angle) * gr;
                            double gy = beam.y + Math.random() * 4.0;
                            level.addParticle(ParticleTypes.ELECTRIC_SPARK, gx, gy, gz, 0, 0.05, 0);
                        }
                    }
                    return beam.ticksRemaining <= 0;
                }

                // 1. [고폭탄 전용] 착탄 중심부 거대 분화 파티클
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

                dev.minse.interiorveil.network.ForcefieldStatePayload veilState = VeilForcefieldRenderer.getCurrentState();
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

        for (ActiveBeam beam : BEAMS) {
            int elapsed = beam.maxTicks - beam.ticksRemaining;
            float fadeAlpha = Math.min(1.0f, (float) beam.ticksRemaining / 100.0f);

            int r = (beam.color >> 16) & 0xFF;
            int g = (beam.color >> 8) & 0xFF;
            int b = beam.color & 0xFF;

            boolean isEmp = (beam.color == 0x00E5FF);

            if (isEmp) {
                // [EMP 전용] 핵폭발/화염 버섯구름 대신, 지평선으로 퍼져나가는 순수 푸른 전자기 링 파동 렌더링
                if (elapsed < 120) {
                    drawEmpShockwaveRings(consumer, pose, camPos, right, up, beam, elapsed, anim);
                }
            } else {
                // [고폭탄 전용] 1. 다단계 초고속 열폭풍 충격파 파동 렌더링 (착탄 초기 160틱 = 8초)
                if (elapsed < 160) {
                    drawCascadingThermalStorm(consumer, pose, camPos, right, up, beam, elapsed, anim);
                }

                // 2. 실사 3D 핵폭발 버섯구름 렌더링 (착탄 후 360틱 = 18초 지속)
                if (elapsed < 360) {
                    drawCinematicNuclearCloud(consumer, pose, camPos, right, up, beam, elapsed, anim);
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

            // 내부 코어 빔
            drawVerticalBeam(consumer, pose, camPos, beam.x, beam.z, startY, endY, isEmp ? 0.25f : 0.4f, coreR, coreG, coreB, coreAlpha, anim);
            // 외부 글로우 빔
            drawVerticalBeam(consumer, pose, camPos, beam.x, beam.z, startY, endY, isEmp ? 0.65f : 0.95f, r, g, b, glowAlpha, -anim * 0.7f);
        }

        if (context.consumers() instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch(RenderType.lightning());
        }
    }

    /**
     * [오르비탈 레일건 EMP 전용]
     * 1. 상공 200m 수평 마하 디스크 충격파 링
     * 2. 수직 궤도 빔을 관통하며 아래로 초고속 쇄도하는 14개의 전자기 가속 코일 링 (Accelerator Rings)
     * 3. 빔 외곽을 휘감아 도는 이중 나선(Double Helix) 플라즈마 와류
     * 4. 지상 수평 마하 충격파 방전 링
     */
    private static void drawEmpShockwaveRings(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 camPos,
            Vector3f right,
            Vector3f up,
            ActiveBeam beam,
            int elapsed,
            float anim
    ) {
        double empCenterY = beam.y + 200.0;
        double startY = beam.y;
        double endY = Math.min(320.0, startY + 256.0);
        double beamHeight = endY - startY;

        // --- 1. [Orbital Coil Rings] 수직 궤도 레일건 축을 따라 초고속으로 하강하는 전자기 가속 링 (6개) ---
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
                float size = 1.6f;

                drawSoftPuff(consumer, pose, camPos, px, ringY, pz, right, up, size, 0, 240, 255, (int) (240 * coilFade));
                drawSoftPuff(consumer, pose, camPos, px, ringY, pz, right, up, size * 0.5f, 220, 255, 255, (int) (255 * coilFade));
            }
        }

        // --- 2. [Double Helix] 레일건 빔을 휘감아 회전하는 이중 나선 플라즈마 스트림 ---
        int helixSteps = 24;
        for (int h = 0; h < 2; h++) {
            double helixPhase = h * Math.PI;
            for (int s = 0; s < helixSteps; s++) {
                double t = (double) s / helixSteps;
                double hy = startY + t * beamHeight;
                double hAngle = t * 16.0 * Math.PI - anim * 0.6 + helixPhase;
                double hr = 1.4 + Math.sin(t * 8.0) * 0.4;
                double hx = beam.x + Math.cos(hAngle) * hr;
                double hz = beam.z + Math.sin(hAngle) * hr;

                drawSoftPuff(consumer, pose, camPos, hx, hy, hz, right, up, 1.4f, 80, 220, 255, 160);
            }
        }

        // --- 3. [Concentric 3D Torus Mach Rings] 상공 200m 6중 입체 네온 도넛 튜브 고리 ---
        int ringCount = 6;
        int ringInterval = 8;
        int ringLifetime = 60;

        for (int r = 0; r < ringCount; r++) {
            int ringStartTick = r * ringInterval;
            int ringElapsed = elapsed - ringStartTick;
            if (ringElapsed >= 0 && ringElapsed < ringLifetime) {
                float p = (float) ringElapsed / ringLifetime;
                double radius = Math.pow(p, 0.58) * 280.0;
                float alpha = (1.0f - p) * 0.95f;

                if (alpha > 0.01f && radius > 0.5) {
                    int ringR = (r % 2 == 0) ? 0 : 50;
                    int ringG = (r % 2 == 0) ? 245 : 210;
                    int ringB = 255;

                    drawGlowingTorusRing(consumer, pose, camPos, beam.x, empCenterY, beam.z, radius, 2.4f, ringR, ringG, ringB, (int) (240 * alpha));
                    drawGlowingTorusRing(consumer, pose, camPos, beam.x, empCenterY, beam.z, radius, 1.0f, 235, 255, 255, (int) (255 * alpha));
                }
            }
        }

        // --- 4. [Ground Surface Concentric Wave Rings] 바닥 지표면에 바짝 밀착되어 쫙 퍼져나가는 6중 평면 동심원 파동 ---
        int groundRingCount = 6;
        int groundInterval = 7;
        int groundLifetime = 50;

        for (int grIdx = 0; grIdx < groundRingCount; grIdx++) {
            int gStart = grIdx * groundInterval;
            int gElapsed = elapsed - gStart;
            if (gElapsed >= 0 && gElapsed < groundLifetime) {
                float gp = (float) gElapsed / (float) groundLifetime;
                double gr = Math.pow(gp, 0.55) * 220.0;
                float ga = (1.0f - gp) * 0.95f;

                if (ga > 0.01f && gr > 0.5) {
                    float ringWidth = (float) (2.5 + gp * 6.0);
                    int grR = (grIdx % 2 == 0) ? 0 : 40;
                    int grG = (grIdx % 2 == 0) ? 235 : 200;
                    int grB = 255;

                    drawFlatGroundWaveRing(consumer, pose, camPos, beam.x, beam.y + 0.15, beam.z, gr, ringWidth, grR, grG, grB, (int) (240 * ga));
                    drawFlatGroundWaveRing(consumer, pose, camPos, beam.x, beam.y + 0.18, beam.z, gr, ringWidth * 0.35f, 240, 255, 255, (int) (255 * ga));
                }
            }
        }
    }

    /**
     * 지형 표면(Terrain Heightmap)의 굴곡과 언덕, 산등성이를 실시간으로 완벽하게 타고 흐르는 동심원 충격파 링을 렌더링한다.
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
            int a
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
            double groundY1 = cy;
            if (level != null) {
                int bx1 = (int) Math.floor(wx1);
                int bz1 = (int) Math.floor(wz1);
                int h1 = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, bx1, bz1);
                if (h1 > level.getMinY()) {
                    groundY1 = h1 + 0.15;
                }
            }

            // 2. 세그먼트 2의 실제 지형 표면 Y 높이 샘플링
            double wx2 = cx + radius * cos2;
            double wz2 = cz + radius * sin2;
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
     * 어느 각도에서 바라보아도 두께감과 원형 볼륨이 살아있는 완벽한 3D 에너지 튜브 고리.
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
            int a
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
