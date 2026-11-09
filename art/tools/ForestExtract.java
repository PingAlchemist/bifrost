import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Monta o mapa de floresta a partir de duas imagens alinhadas pixel a pixel:
 *   completo = mapa com árvores/arbustos/pedras, chao = só o chão.
 * Diferença por pixel (soma RGB > limiar) -> componentes. Os que tocam o topo formam a borda de cima,
 * os que tocam o fundo formam a borda de baixo, os demais são objetos (cada um extraído separadamente:
 * fechamento de falhas, preenchimento de buracos da copa, borda suavizada 1px, ponto de apoio na base).
 * Uso: java ForestExtract.java <completo.png> <chao.png> <pasta saída> [limiar] [areaMin]
 */
public class ForestExtract {
    static int W, H;
    static int[] C, G;
    static boolean[] SH;

    static class Obj {
        int id, x0, y0, x1, y1, area, ax, ay;
        String type, name = "";
        BufferedImage img;
    }

    public static void main(String[] a) throws Exception {
        BufferedImage cimg = argb(ImageIO.read(new File(a[0])));
        BufferedImage gimg = argb(ImageIO.read(new File(a[1])));
        File out = new File(a[2]);
        int thr = a.length > 3 ? Integer.parseInt(a[3]) : 70;
        int minArea = a.length > 4 ? Integer.parseInt(a[4]) : 350;
        out.mkdirs();
        W = cimg.getWidth();
        H = cimg.getHeight();
        C = cimg.getRGB(0, 0, W, H, null, 0, W);
        G = gimg.getRGB(0, 0, W, H, null, 0, W);

        boolean[] mask = new boolean[W * H];
        boolean[] shadow = new boolean[W * H];
        int on = 0, sh = 0;
        for (int i = 0; i < mask.length; i++) {
            int c = C[i], g = G[i];
            int d = Math.abs(((c >> 16) & 255) - ((g >> 16) & 255)) + Math.abs(((c >> 8) & 255) - ((g >> 8) & 255)) + Math.abs((c & 255) - (g & 255));
            if (d <= thr) continue;
            if (isShadow(c, g)) { shadow[i] = true; sh++; continue; }
            mask[i] = true;
            on++;
        }
        SH = shadow;
        System.out.println("pixels de objeto: " + on + " (" + (100 * on / mask.length) + "%)  sombra: " + sh);
        // chão com as sombras dos objetos (ficam por baixo do personagem)
        BufferedImage ground = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < G.length; i++) ground.setRGB(i % W, i / W, shadow[i] ? C[i] : G[i]);
        ImageIO.write(ground, "png", new File(out, "floresta_chao_sombras.png"));

        // ---- crescimento a partir de sementes ----
        // rótulo 1 = borda de cima, 2 = borda de baixo, 3.. = objetos de art/floresta/sementes.txt
        boolean[] full = new boolean[W * H];
        for (int i = 0; i < full.length; i++) full[i] = mask[i] || shadow[i];
        List<String> names = new ArrayList<>();
        names.add("borda_topo");
        names.add("borda_base");
        int[] lab = new int[W * H];
        int[] q = new int[W * H];
        int qh = 0, qt = 0;
        // as bordas largam na frente: toda a faixa externa (topo até TOP_SEED, base a partir de BOT_SEED) já é delas
        int topSeed = 110, botSeed = H - 120;
        for (int y = 0; y < H; y++) {
            if (y >= topSeed && y <= botSeed) continue;
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (!mask[i]) continue;
                lab[i] = y < topSeed ? 1 : 2;
                q[qt++] = i;
            }
        }
        File seedFile = new File(out, "sementes.txt");
        for (String ln : java.nio.file.Files.readAllLines(seedFile.toPath())) {
            ln = ln.trim();
            if (ln.isEmpty() || ln.startsWith("#")) continue;
            String[] p = ln.split("\\s+");
            int sx = Integer.parseInt(p[0]), sy = Integer.parseInt(p[1]);
            String nm = p.length > 2 ? p[2] : ("obj" + names.size());
            // nome repetido = mais uma semente do mesmo objeto
            int id = names.indexOf(nm) + 1;
            if (id <= 0) { names.add(nm); id = names.size(); }
            // a semente pode cair num pixel de sombra/chão: procura o pixel de objeto mais próximo (até 12px)
            int best = -1, bd = Integer.MAX_VALUE;
            for (int dy = -12; dy <= 12; dy++) for (int dx = -12; dx <= 12; dx++) {
                int xx = sx + dx, yy = sy + dy;
                if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue;
                int j = yy * W + xx;
                if (mask[j] && lab[j] == 0 && dx * dx + dy * dy < bd) { bd = dx * dx + dy * dy; best = j; }
            }
            if (best < 0) { System.out.println("semente sem objeto: " + ln); continue; }
            lab[best] = id;
            q[qt++] = best;
        }
        // fase 1: cresce só por pixels que não são sombra (a sombra separa objetos vizinhos)
        while (qh < qt) {
            int i = q[qh++];
            int x = i % W, y = i / W;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue;
                int j = yy * W + xx;
                if (lab[j] == 0 && mask[j]) { lab[j] = lab[i]; q[qt++] = j; }
            }
        }
        // fase 2: sombras (e verde escuro das copas) vão para a região mais próxima
        qh = 0;
        qt = 0;
        for (int i = 0; i < lab.length; i++) if (lab[i] != 0) q[qt++] = i;
        while (qh < qt) {
            int i = q[qh++];
            int x = i % W, y = i / W;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue;
                int j = yy * W + xx;
                if (lab[j] == 0 && full[j]) { lab[j] = lab[i]; q[qt++] = j; }
            }
        }
        int n = names.size();
        int[][] bb = new int[n + 1][];
        for (int k = 1; k <= n; k++) bb[k] = new int[]{W, H, 0, 0, 0};
        for (int i = 0; i < lab.length; i++) {
            int k = lab[i];
            if (k == 0) continue;
            int x = i % W, y = i / W;
            int[] b = bb[k];
            b[0] = Math.min(b[0], x); b[1] = Math.min(b[1], y); b[2] = Math.max(b[2], x); b[3] = Math.max(b[3], y); b[4]++;
        }

        boolean[] top = new boolean[W * H], bottom = new boolean[W * H];
        for (int i = 0; i < lab.length; i++) { if (lab[i] == 1) top[i] = true; else if (lab[i] == 2) bottom[i] = true; }
        List<Obj> objs = new ArrayList<>();
        for (int k = 3; k <= n; k++) {
            if (bb[k][4] < minArea) { System.out.println("pequeno demais: " + names.get(k - 1)); continue; }
            Obj o = extract(lab, k, bb[k]);
            o.name = names.get(k - 1);
            if (o.name.startsWith("pedra")) o.type = "pedra";
            else if (o.name.startsWith("arbusto")) o.type = "arbusto";
            else if (o.name.startsWith("arvore") || o.name.startsWith("pinheiro")) o.type = "arvore";
            objs.add(o);
        }
        System.out.println("objetos: " + objs.size());

        // ---- bordas de floresta (topo / base) ----
        BufferedImage topImg = band(top, true);
        BufferedImage botImg = band(bottom, false);
        ImageIO.write(topImg, "png", new File(out, "floresta_topo.png"));
        ImageIO.write(botImg, "png", new File(out, "floresta_base.png"));

        // ---- objetos ----
        try (PrintWriter pw = new PrintWriter(new File(out, "objetos.txt"), "UTF-8")) {
            pw.println("# id tipo x y largura altura apoioX apoioY nome (coordenadas do mapa, px da imagem original)");
            int id = 0;
            for (Obj o : objs) {
                o.id = id++;
                ImageIO.write(o.img, "png", new File(out, String.format("obj_%02d.png", o.id)));
                pw.printf("%d %s %d %d %d %d %d %d %s%n", o.id, o.type, o.x0, o.y0, o.img.getWidth(), o.img.getHeight(), o.ax, o.ay, o.name);
            }
        }

        // ---- prévias ----
        BufferedImage pv = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = pv.createGraphics();
        g.setColor(new Color(255, 0, 255));
        g.fillRect(0, 0, W, H);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        for (Obj o : objs) {
            g.drawImage(o.img, o.x0, o.y0, null);
            g.setColor(Color.WHITE);
            g.drawLine(o.ax - 5, o.ay, o.ax + 5, o.ay);
            g.drawLine(o.ax, o.ay - 5, o.ax, o.ay + 5);
            g.setColor(Color.BLACK);
            g.drawString(o.id + " " + o.type, o.x0 + 2, o.y0 + 12);
        }
        g.dispose();
        ImageIO.write(pv, "png", new File(out, "preview_objetos.png"));

        BufferedImage pb = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        g = pb.createGraphics();
        g.setColor(new Color(255, 0, 255));
        g.fillRect(0, 0, W, H);
        g.drawImage(topImg, 0, 0, null);
        g.drawImage(botImg, 0, 0, null);
        g.dispose();
        ImageIO.write(pb, "png", new File(out, "preview_bordas.png"));

        // remontagem: chão + bordas + objetos (deve parecer o mapa completo)
        BufferedImage rc = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        g = rc.createGraphics();
        g.drawImage(gimg, 0, 0, null);
        g.drawImage(topImg, 0, 0, null);
        for (Obj o : objs) g.drawImage(o.img, o.x0, o.y0, null);
        g.drawImage(botImg, 0, 0, null);
        g.dispose();
        ImageIO.write(rc, "png", new File(out, "preview_remontado.png"));
        System.out.println("ok");
    }

    static BufferedImage argb(BufferedImage i) {
        BufferedImage o = new BufferedImage(i.getWidth(), i.getHeight(), BufferedImage.TYPE_INT_ARGB);
        o.getGraphics().drawImage(i, 0, 0, null);
        return o;
    }

    static int lum(int c) { return (((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255)) / 10; }

    /** Sombra = o chão escurecido de forma uniforme (mesma proporção em R, G e B). */
    static boolean isShadow(int c, int g) {
        // o azul do chão é quase 0, então a proporção usa só R e G; no azul basta a diferença ser pequena
        double rc = (c >> 16) & 255, gc = (c >> 8) & 255, bc = c & 255;
        double rg = Math.max(1, (g >> 16) & 255), gg = Math.max(1, (g >> 8) & 255), bg = g & 255;
        double kr = rc / rg, kg = gc / gg;
        return kr > 0.35 && kr < 0.86 && kg > 0.35 && kg < 0.86 && Math.abs(kr - kg) < 0.12 && Math.abs(bc - bg) < 35;
    }

    /** Um objeto: fecha falhas e preenche buracos só dentro da própria máscara, suaviza 1px e acha a base. */
    static Obj extract(int[] lab, int id, int[] b) {
        int pad = 4;
        int x0 = Math.max(0, b[0] - pad), y0 = Math.max(0, b[1] - pad);
        int x1 = Math.min(W - 1, b[2] + pad), y1 = Math.min(H - 1, b[3] + pad);
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        boolean[] m = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) m[y * w + x] = lab[(y0 + y) * W + x0 + x] == id;
        // fechamento (2px): dilata e erode
        m = erode(dilate(dilate(m, w, h), w, h), w, h);
        m = erode(m, w, h);
        // preenche buracos internos (o que o fundo de fora não alcança)
        boolean[] outside = new boolean[w * h];
        int[] st = new int[w * h * 4 + 4 * (w + h)];
        int sp = 0;
        for (int x = 0; x < w; x++) { st[sp++] = x; st[sp++] = (h - 1) * w + x; }
        for (int y = 0; y < h; y++) { st[sp++] = y * w; st[sp++] = y * w + w - 1; }
        while (sp > 0) {
            int i = st[--sp];
            if (outside[i] || m[i]) continue;
            outside[i] = true;
            int x = i % w, y = i / w;
            if (x > 0) st[sp++] = i - 1;
            if (x < w - 1) st[sp++] = i + 1;
            if (y > 0) st[sp++] = i - w;
            if (y < h - 1) st[sp++] = i + w;
        }
        for (int i = 0; i < m.length; i++) if (!outside[i]) m[i] = true;

        Obj o = new Obj();
        o.x0 = x0; o.y0 = y0; o.x1 = x1; o.y1 = y1; o.area = b[4];
        o.img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        long sumSat = 0;
        int cnt = 0, gray = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            if (!m[i]) continue;
            boolean edge = (x == 0 || !m[i - 1]) || (x == w - 1 || !m[i + 1]) || (y == 0 || !m[i - w]) || (y == h - 1 || !m[i + w]);
            int c = C[(y0 + y) * W + x0 + x];
            int alpha = edge ? 128 : 255;
            o.img.setRGB(x, y, (alpha << 24) | (c & 0xFFFFFF));
            int r = (c >> 16) & 255, gg = (c >> 8) & 255, bb = c & 255;
            int sat = Math.max(r, Math.max(gg, bb)) - Math.min(r, Math.min(gg, bb));
            sumSat += sat;
            if (sat < 40 && lum(c) > 80) gray++;
            cnt++;
        }
        // base (ponto de apoio): pixel mais baixo que não é sombra, nas colunas centrais
        int cx0 = (int) (w * 0.3), cx1 = (int) (w * 0.7);
        int ay = -1;
        long sx = 0;
        int nb = 0;
        for (int y = h - 1; y >= 0 && ay < 0; y--) {
            for (int x = cx0; x <= cx1; x++) {
                int i = y * w + x;
                if (!m[i]) continue;
                int c = C[(y0 + y) * W + x0 + x], gnd = G[(y0 + y) * W + x0 + x];
                int r = (c >> 16) & 255, gg = (c >> 8) & 255, bb = c & 255;
                boolean shadow = gg >= r && gg >= bb && lum(c) < lum(gnd) * 0.85;
                if (!shadow) { ay = y; sx += x; nb++; }
            }
        }
        if (ay < 0) { ay = h - 1; sx = w / 2; nb = 1; }
        o.ax = x0 + (int) (sx / nb);
        o.ay = y0 + ay;
        // a sombra no chão já está na camada de chão: tira do objeto (senão ela cobre o personagem que passa por trás)
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int gi = (y0 + y) * W + x0 + x;
            if (SH[gi] && (y0 + y) > o.ay - 25) o.img.setRGB(x, y, 0);
        }
        int avgSat = cnt == 0 ? 0 : (int) (sumSat / cnt);
        if (h > 100) o.type = "arvore";
        else if (avgSat < 60 || gray > cnt * 0.22) o.type = "pedra";
        else o.type = "arbusto";
        return o;
    }

    static boolean[] dilate(boolean[] m, int w, int h) {
        boolean[] o = new boolean[m.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean v = false;
            for (int dy = -1; dy <= 1 && !v; dy++) for (int dx = -1; dx <= 1 && !v; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx >= 0 && yy >= 0 && xx < w && yy < h && m[yy * w + xx]) v = true;
            }
            o[y * w + x] = v;
        }
        return o;
    }

    static boolean[] erode(boolean[] m, int w, int h) {
        boolean[] o = new boolean[m.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean v = true;
            for (int dy = -1; dy <= 1 && v; dy++) for (int dx = -1; dx <= 1 && v; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx < 0 || yy < 0 || xx >= w || yy >= h || !m[yy * w + xx]) v = false;
            }
            o[y * w + x] = v;
        }
        return o;
    }

    /** Faixa de floresta: fecha falhas, preenche buracos (fundo de fora = alcançável a partir da borda livre) e suaviza. */
    static BufferedImage band(boolean[] m0, boolean isTop) {
        boolean[] m = erode(erode(dilate(dilate(m0, W, H), W, H), W, H), W, H);
        boolean[] outside = new boolean[W * H];
        int[] st = new int[W * H * 4 + 4 * (W + H)];
        int sp = 0;
        // o "fora" começa pela borda oposta (meio do mapa)
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            boolean seed = isTop ? (y == H - 1) : (y == 0);
            if (seed) st[sp++] = y * W + x;
        }
        while (sp > 0) {
            int i = st[--sp];
            if (outside[i] || m[i]) continue;
            outside[i] = true;
            int x = i % W, y = i / W;
            if (x > 0) st[sp++] = i - 1;
            if (x < W - 1) st[sp++] = i + 1;
            if (y > 0) st[sp++] = i - W;
            if (y < H - 1) st[sp++] = i + W;
        }
        BufferedImage o = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int i = y * W + x;
            boolean in = !outside[i];
            if (!in) continue;
            boolean edge = (x > 0 && outside[i - 1]) || (x < W - 1 && outside[i + 1]) || (y > 0 && outside[i - W]) || (y < H - 1 && outside[i + W]);
            int alpha = edge ? 128 : 255;
            o.setRGB(x, y, (alpha << 24) | (C[i] & 0xFFFFFF));
        }
        return o;
    }
}
