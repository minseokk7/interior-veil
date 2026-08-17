import os
from PIL import Image

def generate_enhanced_key(output_path):
    size = 32
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    # 팔레트
    OUTLINE = (32, 18, 12, 255)       # 어두운 윤곽선
    PURPLE_OUT = (45, 12, 60, 255)    # 보라색 윤곽선
    
    # 골드 (Gold)
    G1 = (255, 255, 220, 255)  # 초 하이라이트
    G2 = (255, 225, 80, 255)   # 밝은 금
    G3 = (220, 165, 25, 255)   # 중간 금
    G4 = (165, 110, 15, 255)   # 어두운 금
    G5 = (105, 65, 8, 255)     # 그림자 금

    # 보라색 차원 젬스톤 (Dimensional Amethyst Jewel)
    J_SHINE = (255, 255, 255, 255)
    J1 = (245, 170, 255, 255)  # 하이라이트
    J2 = (205, 95, 255, 255)   # 밝은 보라
    J3 = (155, 40, 225, 255)   # 메인 보라
    J4 = (105, 20, 165, 255)   # 어두운 보라
    J5 = (65, 10, 110, 255)    # 그림자 보라

    # 네온 시안 코어 (Neon Cyan Core & Energy)
    C1 = (230, 255, 255, 255)
    C2 = (100, 245, 255, 255)
    C3 = (0, 195, 235, 255)
    C4 = (0, 120, 170, 255)

    # 픽셀 아트 데이터 매트릭스 (32x32)
    pixels = {}

    def p(x, y, col):
        if 0 <= x < size and 0 <= y < size:
            pixels[(x, y)] = col

    # --- 1. 상단 골든 링 & 보석 헤드 (Center: 16, 9) ---
    # 상단 십자/왕관 돌기
    for x in range(15, 18):
        p(x, 1, OUTLINE)
    p(16, 2, G1)
    p(15, 2, G2)
    p(17, 2, G4)
    p(14, 2, OUTLINE)
    p(18, 2, OUTLINE)

    # 대각선 4방향 돌기 장식
    diag_spikes = [
        (10, 3, OUTLINE), (11, 4, G2), (10, 4, G1), (11, 3, G3), (12, 4, OUTLINE), (10, 5, OUTLINE),
        (22, 3, OUTLINE), (21, 4, G3), (22, 4, G4), (21, 3, G2), (20, 4, OUTLINE), (22, 5, OUTLINE),
        (7, 9, OUTLINE), (8, 9, G1), (8, 10, G3), (9, 9, G2), (8, 8, OUTLINE), (9, 10, OUTLINE),
        (25, 9, OUTLINE), (24, 9, G4), (24, 10, G5), (23, 9, G3), (24, 8, OUTLINE), (23, 10, OUTLINE),
        (10, 15, OUTLINE), (11, 14, G3), (11, 15, G4), (12, 14, G2), (10, 14, OUTLINE), (12, 15, OUTLINE),
        (22, 15, OUTLINE), (21, 14, G4), (21, 15, G5), (20, 14, G3), (22, 14, OUTLINE), (20, 15, OUTLINE),
    ]
    for x, y, col in diag_spikes:
        p(x, y, col)

    # 헤드 링 외곽 원형/팔각형 (y: 3~15, x: 10~22)
    for x in range(13, 20):
        p(x, 3, OUTLINE)
        p(x, 15, OUTLINE)
    for y in range(6, 13):
        p(9, y, OUTLINE)
        p(23, y, OUTLINE)

    # 대각선 외곽 아웃라인
    p(12, 4, OUTLINE); p(20, 4, OUTLINE)
    p(11, 5, OUTLINE); p(21, 5, OUTLINE)
    p(10, 6, OUTLINE); p(22, 6, OUTLINE)
    p(10, 12, OUTLINE); p(22, 12, OUTLINE)
    p(11, 13, OUTLINE); p(21, 13, OUTLINE)
    p(12, 14, OUTLINE); p(20, 14, OUTLINE)

    # 골드 림 채우기 (내경: x 12~20, y 5~13)
    for x in range(10, 23):
        for y in range(4, 15):
            dx = x - 16
            dy = y - 9
            d_sq = dx*dx + dy*dy
            if 15 <= d_sq <= 32:
                if dx + dy < -2:
                    p(x, y, G1 if d_sq <= 22 else G2)
                elif dx + dy < 2:
                    p(x, y, G2 if dx < 0 else G3)
                elif dx + dy < 5:
                    p(x, y, G4)
                else:
                    p(x, y, G5)

    # 내부 보석 아웃라인 (d_sq == 14, 13 근처)
    for x in range(12, 21):
        for y in range(5, 14):
            dx = x - 16
            dy = y - 9
            d_sq = dx*dx + dy*dy
            if d_sq == 13 or d_sq == 14:
                p(x, y, PURPLE_OUT)

    # 중앙 보석 (d_sq <= 12)
    for x in range(13, 20):
        for y in range(6, 13):
            dx = x - 16
            dy = y - 9
            d_sq = dx*dx + dy*dy
            if d_sq <= 12:
                if dx == -1 and dy == -1:
                    p(x, y, J_SHINE)
                elif dx <= 0 and dy <= 0:
                    p(x, y, J1 if d_sq <= 3 else J2)
                elif dx >= 0 and dy >= 0:
                    p(x, y, J5 if d_sq >= 8 else J4)
                else:
                    p(x, y, J3)

    # 중앙 보석 코어 시안 펄스 포인트
    p(16, 9, C1)
    p(15, 9, C2)
    p(16, 10, C3)

    # --- 2. 넥 가드 및 날개 (y: 16 ~ 18) ---
    # 넥 링
    for x in range(14, 19):
        p(x, 16, G2 if x <= 16 else G4)
    p(13, 16, OUTLINE); p(19, 16, OUTLINE)

    # 좌우 사이버네틱 크로스 가드 (y: 17~18)
    for x in range(11, 22):
        p(x, 17, G2 if x < 15 else (C2 if x == 16 else (G3 if x < 19 else G4)))
    p(10, 17, OUTLINE); p(22, 17, OUTLINE)

    for x in range(12, 21):
        p(x, 18, G3 if x < 16 else (C3 if x == 16 else G5))
    p(11, 18, OUTLINE); p(21, 18, OUTLINE)

    # --- 3. 샤프트 (열쇠 기둥 y: 19 ~ 28) ---
    for y in range(19, 29):
        p(14, y, OUTLINE)
        p(15, y, G1 if y % 4 == 0 else G2)
        # 중앙 에너지 코어 라인
        if y % 3 == 0:
            p(16, y, C1)
        elif y % 3 == 1:
            p(16, y, C2)
        else:
            p(16, y, J2)
        p(17, y, G4 if y % 2 == 0 else G5)
        p(18, y, OUTLINE)

    # --- 4. 하단 비트 (열쇠 이빨) (y: 22 ~ 28) ---
    # 좌측 비트 (결계 룬 블레이드)
    # 상단 톱니
    for x in range(9, 15):
        p(x, 22, OUTLINE)
    for x in range(10, 15):
        p(x, 23, G1 if x == 10 else G2)
    p(9, 23, OUTLINE)
    for x in range(10, 15):
        p(x, 24, J2 if x <= 12 else G3)
    p(9, 24, OUTLINE)
    for x in range(9, 15):
        p(x, 25, OUTLINE)

    # 하단 톱니
    for x in range(10, 15):
        p(x, 26, G2 if x == 10 else G3)
    p(9, 26, OUTLINE)
    for x in range(10, 15):
        p(x, 27, G4)
    p(9, 27, OUTLINE)
    for x in range(9, 15):
        p(x, 28, OUTLINE)

    # 우측 비트 (보조 이빨)
    for x in range(18, 23):
        p(x, 23, OUTLINE)
    for x in range(18, 22):
        p(x, 24, G3 if x == 21 else G2)
    p(22, 24, OUTLINE)
    for x in range(18, 22):
        p(x, 25, J3 if x >= 20 else G4)
    p(22, 25, OUTLINE)
    for x in range(18, 23):
        p(x, 26, OUTLINE)
    for x in range(18, 21):
        p(x, 27, G4)
    p(21, 27, OUTLINE)
    for x in range(18, 22):
        p(x, 28, OUTLINE)

    # --- 5. 바닥 팁 (y: 29 ~ 31) ---
    for x in range(15, 18):
        p(x, 29, G2 if x == 15 else (C1 if x == 16 else G4))
    p(14, 29, OUTLINE); p(18, 29, OUTLINE)

    p(15, 30, G3); p(16, 30, G2); p(17, 30, G5)
    p(14, 30, OUTLINE); p(18, 30, OUTLINE)

    p(15, 31, OUTLINE); p(16, 31, G3); p(17, 31, OUTLINE)
    p(16, 32, OUTLINE)

    # 이미지에 픽셀 적용
    for (x, y), col in pixels.items():
        if 0 <= x < size and 0 <= y < size:
            img.putpixel((x, y), col)

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    img.save(output_path, "PNG")
    print(f"Enhanced key texture saved to {output_path}")

if __name__ == "__main__":
    out_file = r"C:\Users\minse\Documents\Codex\2026-08-13\new-chat-2\work\interior-veil\src\main\resources\assets\interiorveil\textures\item\veil_key.png"
    generate_enhanced_key(out_file)
