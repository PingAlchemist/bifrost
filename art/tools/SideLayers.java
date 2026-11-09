import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Divide o sprite de lado (guerreiro_e.png) em camadas para animar o andar sem arte nova:
 * corpo (sem pernas e sem o braço da espada), braço+espada, perna da frente e perna de trás.
 * As áreas escondidas que aparecem ao girar (atrás do braço / sob a lâmina / topo das pernas sob a túnica)
 * são preenchidas copiando pixels vizinhos da mesma camada.
 * Todas as camadas têm o mesmo tamanho do sprite; parado, a remontagem é idêntica ao original.
 * Uso: java SideLayers.java <guerreiro_e.png> <pasta res> <pasta debug> [perfil=guerreiro|aprendiz|mago|cacador]
 * Perfil cacador: como o aprendiz (uma perna), túnica verde com barra dourada; o arco é desenhado pelo jogo.
 * Perfil mago: mesmo esquema do aprendiz (uma perna), manto azul com barra dourada; o cajado é desenhado pelo jogo.
 * Perfil aprendiz: grava aprendiz_lado_*.png; a vista de lado só mostra uma perna, então a de trás é cópia da da frente.
 */
public class SideLayers {
    // ---- medidas no espaço do sprite (248x420) ----
    static int HEM_Y = 324;               // barra da túnica: abaixo disso são pernas
    static int[] SHOULDER = {124, 252};
    static int[] HIP_F = {112, 318};      // perna da frente (bota da esquerda, inteira)
    static int[] HIP_B = {146, 318};      // perna de trás
    /** Abaixo disso, dentro do polígono do braço, só a lâmina vai com o braço. */
    static int BLADE_Y = 336;
    /** Aprendiz: no polígono do braço, acima desta linha é manga (fica no corpo); túnica bege em vez de azul. */
    static boolean apr = false;
    /** Mago: a túnica é o manto azul (barra dourada) em vez do bege. */
    static boolean robe = false;
    /** Caçador: túnica/capa verde (barra dourada). */
    static boolean green = false;
    static int SLEEVE_Y = 236;
    /** Aprendiz: faixa de colunas da perna; abaixo da túnica, fora dela, é corpo (barra da túnica atrás da lâmina). */
    static int LEG_X0 = 0, LEG_X1 = 9999;
    static int[][] ARM_POLY = {
            {100, 250}, {150, 250}, {155, 275}, {160, 305}, {168, 318}, {166, 332}, {192, 402}, {182, 418},
            {170, 420}, {150, 385}, {124, 342}, {110, 336}, {102, 318}, {98, 290}
    };
    /** Lâmina (da guarda até a ponta), com ~2px de folga para levar o contorno junto. */
    static int[][] BLADE_POLY = {{125, 336}, {155, 329}, {187, 401}, {178, 419}, {164, 414}};

    /** Medidas do aprendiz (sprite 262x420). */
    static void aprendiz() {
        apr = true;
        HEM_Y = 314;
        SHOULDER = new int[]{132, 238};
        HIP_F = new int[]{124, 312};
        HIP_B = new int[]{124, 312};
        BLADE_Y = 322;
        LEG_X0 = 88;
        LEG_X1 = 172;
        ARM_POLY = new int[][]{
                {115, 232}, {151, 232}, {152, 262}, {172, 266}, {174, 298}, {186, 296}, {190, 306}, {182, 318},
                {210, 402}, {209, 418}, {195, 419}, {150, 326}, {132, 326}, {127, 312}, {121, 298}, {116, 264}
        };
        BLADE_POLY = new int[][]{{148, 322}, {173, 313}, {211, 402}, {209, 419}, {195, 419}};
    }

    static int W, H;

    static int splitX(int y) { return y < 372 ? 136 : 152; }

    /** Medidas do mago (sprite 266x420): sem lâmina (o cajado é desenhado pelo jogo na mão). */
    static void mago() {
        apr = true;
        robe = true;
        HEM_Y = 350;
        SHOULDER = new int[]{130, 240};
        HIP_F = new int[]{140, 346};
        HIP_B = new int[]{140, 346};
        BLADE_Y = 9999;
        SLEEVE_Y = 236;
        LEG_X0 = 92;
        LEG_X1 = 196;
        ARM_POLY = new int[][]{
                {106, 236}, {152, 236}, {154, 262}, {164, 280}, {164, 332}, {114, 332}, {110, 300}, {100, 262}
        };
        BLADE_POLY = new int[][]{{0, 0}, {1, 0}, {1, 1}};
    }

    /** Medidas do caçador (sprite 242x420): sem lâmina (o arco é desenhado pelo jogo na mão). */
    static void cacador() {
        apr = true;
        green = true;
        HEM_Y = 312;
        SHOULDER = new int[]{136, 234};
        HIP_F = new int[]{146, 314};
        HIP_B = new int[]{146, 314};
        BLADE_Y = 9999;
        SLEEVE_Y = 232;
        LEG_X0 = 100;
        LEG_X1 = 210;
        ARM_POLY = new int[][]{
                {114, 228}, {158, 228}, {166, 252}, {174, 270}, {176, 300}, {178, 318}, {169, 330}, {148, 331},
                {124, 320}, {116, 296}, {112, 262}
        };
        BLADE_POLY = new int[][]{{0, 0}, {1, 0}, {1, 1}};
    }

    public static void main(String[] a) throws Exception {
        String pre = "";
        if (a.length > 3 && a[3].equals("aprendiz")) { aprendiz(); pre = "aprendiz_"; }
        if (a.length > 3 && a[3].equals("mago")) { mago(); pre = "mago_"; }
        if (a.length > 3 && a[3].equals("cacador")) { cacador(); pre = "cacador_"; }
        BufferedImage src0 = ImageIO.read(new File(a[0]));
        W = src0.getWidth();
        H = src0.getHeight();
        BufferedImage src = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        src.getGraphics().drawImage(src0, 0, 0, null);
        int[] s = src.getRGB(0, 0, W, H, null, 0, W);

        Path2D.Float poly = new Path2D.Float();
        poly.moveTo(ARM_POLY[0][0], ARM_POLY[0][1]);
        for (int i = 1; i < ARM_POLY.length; i++) poly.lineTo(ARM_POLY[i][0], ARM_POLY[i][1]);
        poly.closePath();
        Path2D.Float bladePoly = new Path2D.Float();
        bladePoly.moveTo(BLADE_POLY[0][0], BLADE_POLY[0][1]);
        for (int i = 1; i < BLADE_POLY.length; i++) bladePoly.lineTo(BLADE_POLY[i][0], BLADE_POLY[i][1]);
        bladePoly.closePath();

        int[] body = new int[W * H], arm = new int[W * H], legF = new int[W * H], legB = new int[W * H];
        boolean[] inArm = new boolean[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) inArm[y * W + x] = poly.contains(x + 0.5, y + 0.5);
        // fim da túnica por coluna: pixel azul/claro mais baixo (fora da lâmina) + contorno
        int[] tunicBottom = new int[W];
        for (int x = 0; x < W; x++) {
            int tb = HEM_Y - 8;
            for (int y = 250; y < HEM_Y + 24; y++) {
                int c = s[y * W + x];
                if ((c >>> 24) < 128) continue;
                if (apr) { if (isTunic(c) && !inArm[y * W + x]) tb = Math.max(tb, y); continue; }
                boolean lightHem = isLight(c) && !(inArm[y * W + x] && y > 334);
                if (isBlue(c) || lightHem) tb = Math.max(tb, y);
            }
            tunicBottom[x] = tb + 4;
        }
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int i = y * W + x;
            int c = s[i];
            if ((c >>> 24) == 0) continue;
            boolean aboveHem = y <= tunicBottom[x];
            // abaixo da guarda, o polígono só leva a lâmina (clara), a guarda (dourada) e o contorno colado nela
            boolean bladeZone = inArm[i] && y > BLADE_Y;
            boolean blade = bladePoly.contains(x + 0.5, y + 0.5);
            if (bladeZone && !blade) {
                if (x < LEG_X0 || x > LEG_X1) body[i] = c;
                else if (apr || x < splitX(y)) legF[i] = c; else legB[i] = c;
            } else if (inArm[i]) {
                boolean tunic = apr ? y < SLEEVE_Y : isBlue(c) || (isLight(c) && y <= 334);
                if (tunic) body[i] = c; else arm[i] = c;
            } else if (aboveHem || x < LEG_X0 || x > LEG_X1 || (green && y <= HEM_Y + 28 && isTunic(c))) body[i] = c;
            else if (apr || x < splitX(y)) legF[i] = c;
            else legB[i] = c;
        }
        boolean[] armPx = new boolean[W * H];
        for (int i = 0; i < armPx.length; i++) armPx[i] = (arm[i] >>> 24) != 0;

        // corpo: preenche o que estava atrás do braço (só onde o braço cobria, então parado não muda nada)
        int[] before = body.clone();
        fillHoles(body, armPx, green ? 226 : 240, HEM_Y + (green ? 22 : 10));
        if (green) smoothFilled(body, before);
        // pernas: preenche o que estava sob a lâmina
        fillHoles(legF, armPx, HEM_Y - 8, H);
        fillHoles(legB, armPx, HEM_Y - 8, H);
        // pernas: estende para cima por baixo da túnica (escondido parado, aparece ao girar)
        extendUp(legF, body);
        if (apr) legB = legF.clone(); // perna de trás: cópia da da frente (desenhada atrás e escurecida no jogo)
        else extendUp(legB, body);

        File out = new File(a[1]);
        save(body, new File(out, pre + "lado_corpo.png"));
        save(arm, new File(out, pre + "lado_braco.png"));
        save(legF, new File(out, pre + "lado_perna_f.png"));
        save(legB, new File(out, pre + "lado_perna_t.png"));

        // ---- conferência ----
        File dbg = new File(a[2]);
        dbg.mkdirs();
        BufferedImage rest = compose(legF, legB, body, arm, 0, 0, 0, 0, 0);
        int diff = 0;
        int[] r = rest.getRGB(0, 0, W, H, null, 0, W);
        for (int i = 0; i < r.length; i++) if (((s[i] >>> 24) > 0) && r[i] != s[i]) diff++;
        System.out.println("pixels diferentes na remontagem parada: " + diff);
        BufferedImage sheet = new BufferedImage(W * 4 + 50, H + 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x8D, 0xAA, 0x55));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.drawImage(src, 10, 10, null);
        g.drawImage(rest, W + 20, 10, null);
        g.drawImage(compose(legF, legB, body, arm, -25, 25, 7, 0, 8), W * 2 + 30, 10, null);
        g.drawImage(compose(legF, legB, body, arm, 25, -25, 0, 7, -8), W * 3 + 40, 10, null);
        g.dispose();
        ImageIO.write(sheet, "png", new File(dbg, pre + "side_layers_check.png"));
        System.out.println("ok");
    }

    /** Preenche pixels do buraco (máscara) que estejam entre pixels da própria camada na mesma linha. */
    static void fillHoles(int[] layer, boolean[] hole, int y0, int y1) {
        for (int y = Math.max(0, y0); y < Math.min(H, y1); y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (!hole[i] || layer[i] != 0) continue;
                int l = -1, rr = -1;
                int minLum = green ? 18 : 45; // caçador: calça marrom-escura também serve de amostra
                for (int k = x - 1; k >= Math.max(0, x - 60); k--) { int c = layer[y * W + k]; if (!hole[y * W + k] && (c >>> 24) > 200 && lum(c) > minLum) { l = c; break; } }
                for (int k = x + 1; k < Math.min(W, x + 60); k++) { int c = layer[y * W + k]; if (!hole[y * W + k] && (c >>> 24) > 200 && lum(c) > minLum) { rr = c; break; } }
                if (l != -1 && rr != -1) layer[i] = l;
                else if ((robe || green) && (l != -1 || rr != -1)) layer[i] = l != -1 ? l : rr; // manto: basta um lado
            }
        }
    }

    /** Caçador: o preenchimento por linha deixa listras; suaviza só os pixels preenchidos (média 7x7, 3 passadas). */
    static void smoothFilled(int[] layer, int[] before) {
        boolean[] filled = new boolean[layer.length];
        for (int i = 0; i < layer.length; i++) filled[i] = before[i] == 0 && layer[i] != 0;
        for (int it = 0; it < 3; it++) {
            int[] src = layer.clone();
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (!filled[i]) continue;
                int r = 0, g = 0, b = 0, n = 0;
                for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue;
                    int c = src[yy * W + xx];
                    if ((c >>> 24) < 250) continue;
                    r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255; n++;
                }
                if (n > 0) layer[i] = 0xFF000000 | (r / n << 16) | (g / n << 8) | (b / n);
            }
        }
    }

    /** Para cada coluna, copia o pixel mais alto (opaco) da perna para cima, só onde o corpo cobre. */
    static void extendUp(int[] layer, int[] body) {
        for (int x = 0; x < W; x++) {
            int top = -1;
            for (int y = 0; y < H; y++) if ((layer[y * W + x] >>> 24) > 200 && lum(layer[y * W + x]) > 45) { top = y; break; }
            if (top < 0) continue;
            int c = layer[(Math.min(H - 1, top + 2)) * W + x];
            if ((c >>> 24) < 200) c = layer[top * W + x];
            for (int y = top - 1; y >= Math.max(0, top - 34); y--) {
                int i = y * W + x;
                if (layer[i] != 0) continue;
                if ((body[i] >>> 24) < 250) break; // só por baixo da túnica opaca
                layer[i] = c;
            }
        }
    }

    static boolean isBlue(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return b > r + 25 && b > g + 5;
    }

    static boolean isLight(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
        return lum(c) > 165 && mx - mn < 50;
    }

    /** Túnica bege do aprendiz. */
    static boolean isTunic(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        if (robe) return isBlue(c) || isGold(c);
        if (green) return (g > r + 18 && g > b + 18) || isGold(c);
        return lum(c) > 120 && g > 100 && r - b > 35 && r - b < 130 && r - g < 60;
    }

    static boolean isDark(int c) { return lum(c) < 60; }

    static boolean isGold(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return r > 170 && g > 120 && b < 100;
    }

    static boolean nearLight(int[] s, int x, int y) {
        for (int k = -3; k <= 3; k++) {
            int xx = x + k;
            if (xx < 0 || xx >= W) continue;
            int c = s[y * W + xx];
            if ((c >>> 24) > 128 && isLight(c)) return true;
        }
        return false;
    }

    static int lum(int c) { return (((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255)) / 10; }

    static BufferedImage img(int[] p) {
        BufferedImage b = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        b.setRGB(0, 0, W, H, p, 0, W);
        return b;
    }

    static void save(int[] p, File f) throws Exception { ImageIO.write(img(p), "png", f); }

    /** Remonta: perna de trás, perna da frente, corpo, braço (ângulos em graus, elevação dos pés em px). */
    static BufferedImage compose(int[] legF, int[] legB, int[] body, int[] arm, double angF, double angB, int liftF, int liftB, double angArm) {
        BufferedImage o = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        draw(g, img(legB), HIP_B, angB, liftB);
        draw(g, img(legF), HIP_F, angF, liftF);
        g.drawImage(img(body), 0, 0, null);
        draw(g, img(arm), SHOULDER, angArm, 0);
        g.dispose();
        return o;
    }

    static void draw(Graphics2D g, BufferedImage b, int[] pv, double ang, int lift) {
        if (ang == 0 && lift == 0) { g.drawImage(b, 0, 0, null); return; }
        AffineTransform at = new AffineTransform();
        at.translate(0, -lift);
        at.rotate(Math.toRadians(ang), pv[0], pv[1]);
        g.drawImage(b, at, null);
    }
}
