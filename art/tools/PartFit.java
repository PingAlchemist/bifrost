import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Encaixa as partes de art/parts sobre o sprite de lado (guerreiro_e.png) por busca de escala/rotação/posição
 * minimizando a diferença de cor. Exporta cada parte já transformada (lado_*.png) no espaço do sprite e imprime
 * as posições/pivôs para o código.
 * Uso: java PartFit.java <guerreiro_e.png> <pasta parts> <pasta saída res> <composição.png>
 */
public class PartFit {
    static int TW, TH;
    static int[] T;
    static boolean[] claimed;

    static class Fit {
        String name;
        double s, rot, cost;
        int ox, oy;
        BufferedImage img;
    }

    public static void main(String[] a) throws Exception {
        BufferedImage tgt = toArgb(ImageIO.read(new File(a[0])));
        TW = tgt.getWidth();
        TH = tgt.getHeight();
        T = tgt.getRGB(0, 0, TW, TH, null, 0, TW);
        claimed = new boolean[TW * TH];
        File parts = new File(a[1]);
        File out = new File(a[2]);

        // nome, escala min, escala max, rot min, rot max, passo rot
        Object[][] spec = {
                {"capacete", 0.40, 0.80, -12, 12, 3},
                {"cabeca", 0.40, 0.80, -12, 12, 3},
                {"tronco", 0.40, 0.80, -10, 10, 2},
                {"perna_1", 0.40, 0.80, -15, 15, 3},
                {"perna_2", 0.40, 0.80, -15, 15, 3},
                {"braco_1", 0.40, 0.80, -30, 30, 5},
                {"braco_2", 0.40, 0.80, -30, 30, 5},
                {"espada", 0.40, 0.80, -60, 60, 5},
        };
        Fit[] fits = new Fit[spec.length];
        for (int i = 0; i < spec.length; i++) {
            String n = (String) spec[i][0];
            BufferedImage p = toArgb(ImageIO.read(new File(parts, n + ".png")));
            boolean twin = n.equals("perna_2") || n.equals("braco_2");
            if (n.equals("perna_1") || n.equals("braco_1")) java.util.Arrays.fill(claimed, false);
            Fit f = fit(n, p, (double) spec[i][1], (double) spec[i][2], (int) spec[i][3], (int) spec[i][4], (int) spec[i][5], twin);
            fits[i] = f;
            if (n.equals("perna_1") || n.equals("braco_1")) claim(f);
            System.out.printf(Locale.US, "%-9s s=%.3f rot=%.1f pos=(%d,%d) size=%dx%d custo=%.3f%n",
                    n, f.s, f.rot, f.ox, f.oy, f.img.getWidth(), f.img.getHeight(), f.cost);
        }
        out.mkdirs();
        for (Fit f : fits) ImageIO.write(f.img, "png", new File(out, "lado_" + f.name + ".png"));

        // composição: original | partes montadas
        BufferedImage comp = new BufferedImage(TW * 2 + 30, TH + 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = comp.createGraphics();
        g.setColor(new Color(0x8D, 0xAA, 0x55));
        g.fillRect(0, 0, comp.getWidth(), comp.getHeight());
        g.drawImage(tgt, 10, 10, null);
        String[] order = {"braco_2", "perna_2", "tronco", "perna_1", "cabeca", "capacete", "braco_1", "espada"};
        for (String n : order) for (Fit f : fits) if (f.name.equals(n)) g.drawImage(f.img, TW + 20 + f.ox, 10 + f.oy, null);
        g.dispose();
        ImageIO.write(comp, "png", new File(a[3]));
        System.out.println("ok");
    }

    static BufferedImage toArgb(BufferedImage i) {
        BufferedImage o = new BufferedImage(i.getWidth(), i.getHeight(), BufferedImage.TYPE_INT_ARGB);
        o.getGraphics().drawImage(i, 0, 0, null);
        return o;
    }

    static BufferedImage transform(BufferedImage p, double s, double rotDeg) {
        double r = Math.toRadians(rotDeg);
        double w = p.getWidth() * s, h = p.getHeight() * s;
        double cw = Math.abs(w * Math.cos(r)) + Math.abs(h * Math.sin(r));
        double ch = Math.abs(w * Math.sin(r)) + Math.abs(h * Math.cos(r));
        int W = (int) Math.ceil(cw) + 2, H = (int) Math.ceil(ch) + 2;
        BufferedImage o = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        AffineTransform at = new AffineTransform();
        at.translate(W / 2.0, H / 2.0);
        at.rotate(r);
        at.scale(s, s);
        at.translate(-p.getWidth() / 2.0, -p.getHeight() / 2.0);
        g.drawImage(p, at, null);
        g.dispose();
        return o;
    }

    static double cost(int[] px, int w, int h, int ox, int oy, int stride, boolean twin) {
        double sum = 0;
        int n = 0;
        for (int y = 0; y < h; y += stride) {
            for (int x = 0; x < w; x += stride) {
                int c = px[y * w + x];
                if ((c >>> 24) < 140) continue;
                n++;
                int tx = ox + x, ty = oy + y;
                if (tx < 0 || ty < 0 || tx >= TW || ty >= TH) { sum += 1.0; continue; }
                int t = T[ty * TW + tx];
                if ((t >>> 24) < 100) { sum += 1.0; continue; }
                int d = Math.abs(((c >> 16) & 255) - ((t >> 16) & 255)) + Math.abs(((c >> 8) & 255) - ((t >> 8) & 255)) + Math.abs((c & 255) - (t & 255));
                double v = Math.min(1.0, d / 300.0);
                if (twin && claimed[ty * TW + tx]) v += 0.5;
                sum += v;
            }
        }
        return n == 0 ? 9 : sum / n;
    }

    static Fit fit(String name, BufferedImage p, double s0, double s1, int r0, int r1, int rs, boolean twin) {
        Fit best = new Fit();
        best.name = name;
        best.cost = 1e9;
        // busca grossa
        for (double s = s0; s <= s1 + 1e-9; s += 0.025) {
            for (int r = r0; r <= r1; r += rs) {
                BufferedImage im = transform(p, s, r);
                int w = im.getWidth(), h = im.getHeight();
                int[] px = im.getRGB(0, 0, w, h, null, 0, w);
                for (int oy = -h / 3; oy < TH - h / 2; oy += 3) {
                    for (int ox = -w / 3; ox < TW - w / 2; ox += 3) {
                        double c = cost(px, w, h, ox, oy, 4, twin);
                        if (c < best.cost) { best.cost = c; best.s = s; best.rot = r; best.ox = ox; best.oy = oy; }
                    }
                }
            }
        }
        // refinamento
        Fit ref = new Fit();
        ref.name = name;
        ref.cost = 1e9;
        for (double s = best.s - 0.025; s <= best.s + 0.025 + 1e-9; s += 0.005) {
            for (double r = best.rot - rs; r <= best.rot + rs + 1e-9; r += 1) {
                BufferedImage im = transform(p, s, r);
                int w = im.getWidth(), h = im.getHeight();
                int[] px = im.getRGB(0, 0, w, h, null, 0, w);
                // a imagem transformada muda de tamanho: recentra a busca pelo centro
                int bw = transform(p, best.s, best.rot).getWidth(), bh = transform(p, best.s, best.rot).getHeight();
                int cx = best.ox + bw / 2, cy = best.oy + bh / 2;
                for (int oy = cy - h / 2 - 5; oy <= cy - h / 2 + 5; oy++) {
                    for (int ox = cx - w / 2 - 5; ox <= cx - w / 2 + 5; ox++) {
                        double c = cost(px, w, h, ox, oy, 1, twin);
                        if (c < ref.cost) { ref.cost = c; ref.s = s; ref.rot = r; ref.ox = ox; ref.oy = oy; ref.img = im; }
                    }
                }
            }
        }
        return ref;
    }

    static void claim(Fit f) {
        int w = f.img.getWidth(), h = f.img.getHeight();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if ((f.img.getRGB(x, y) >>> 24) < 140) continue;
            int tx = f.ox + x, ty = f.oy + y;
            if (tx >= 0 && ty >= 0 && tx < TW && ty < TH) claimed[ty * TW + tx] = true;
        }
    }
}
