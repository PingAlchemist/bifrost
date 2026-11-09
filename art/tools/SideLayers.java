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
 * Uso: java SideLayers.java <guerreiro_e.png> <pasta res> <pasta debug>
 */
public class SideLayers {
    // ---- medidas no espaço do sprite (248x420) ----
    static final int HEM_Y = 324;               // barra da túnica: abaixo disso são pernas
    static final int[] SHOULDER = {124, 252};
    static final int[] HIP_F = {112, 318};      // perna da frente (bota da esquerda, inteira)
    static final int[] HIP_B = {146, 318};      // perna de trás
    static final int[][] ARM_POLY = {
            {100, 250}, {150, 250}, {155, 275}, {160, 305}, {168, 318}, {166, 332}, {192, 402}, {182, 418},
            {170, 420}, {150, 385}, {124, 342}, {110, 336}, {102, 318}, {98, 290}
    };
    /** Lâmina (da guarda até a ponta), com ~2px de folga para levar o contorno junto. */
    static final int[][] BLADE_POLY = {{125, 336}, {155, 329}, {187, 401}, {178, 419}, {164, 414}};

    static int W, H;

    static int splitX(int y) { return y < 372 ? 136 : 152; }

    public static void main(String[] a) throws Exception {
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
            for (int y = 250; y < 348; y++) {
                int c = s[y * W + x];
                if ((c >>> 24) < 128) continue;
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
            boolean bladeZone = inArm[i] && y > 336;
            boolean blade = bladePoly.contains(x + 0.5, y + 0.5);
            if (bladeZone && !blade) {
                if (x < splitX(y)) legF[i] = c; else legB[i] = c;
            } else if (inArm[i]) {
                boolean tunic = isBlue(c) || (isLight(c) && y <= 334);
                if (tunic) body[i] = c; else arm[i] = c;
            } else if (aboveHem) body[i] = c;
            else if (x < splitX(y)) legF[i] = c;
            else legB[i] = c;
        }
        boolean[] armPx = new boolean[W * H];
        for (int i = 0; i < armPx.length; i++) armPx[i] = (arm[i] >>> 24) != 0;

        // corpo: preenche o que estava atrás do braço (só onde o braço cobria, então parado não muda nada)
        fillHoles(body, armPx, 240, HEM_Y + 10);
        // pernas: preenche o que estava sob a lâmina
        fillHoles(legF, armPx, HEM_Y - 8, H);
        fillHoles(legB, armPx, HEM_Y - 8, H);
        // pernas: estende para cima por baixo da túnica (escondido parado, aparece ao girar)
        extendUp(legF, body);
        extendUp(legB, body);

        File out = new File(a[1]);
        save(body, new File(out, "lado_corpo.png"));
        save(arm, new File(out, "lado_braco.png"));
        save(legF, new File(out, "lado_perna_f.png"));
        save(legB, new File(out, "lado_perna_t.png"));

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
        ImageIO.write(sheet, "png", new File(dbg, "side_layers_check.png"));
        System.out.println("ok");
    }

    /** Preenche pixels do buraco (máscara) que estejam entre pixels da própria camada na mesma linha. */
    static void fillHoles(int[] layer, boolean[] hole, int y0, int y1) {
        for (int y = Math.max(0, y0); y < Math.min(H, y1); y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (!hole[i] || layer[i] != 0) continue;
                int l = -1, rr = -1;
                for (int k = x - 1; k >= Math.max(0, x - 60); k--) { int c = layer[y * W + k]; if (!hole[y * W + k] && (c >>> 24) > 200 && lum(c) > 45) { l = c; break; } }
                for (int k = x + 1; k < Math.min(W, x + 60); k++) { int c = layer[y * W + k]; if (!hole[y * W + k] && (c >>> 24) > 200 && lum(c) > 45) { rr = c; break; } }
                if (l != -1 && rr != -1) layer[i] = l;
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
