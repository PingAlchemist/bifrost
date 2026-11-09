import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Prepara os sprites do guerreiro a partir da folha original.
 * Uso: java SpriteTool.java <folha.png> <pasta res/drawable-nodpi> <pasta art/parts> <preview.png> <alturaFinal>
 *
 * - Remove o fundo (transparente ou branco por preenchimento a partir das bordas) e limpa o halo.
 * - Fileira de cima: 5 vistas (frente, 3/4 frente, lado, 3/4 costas, costas) -> mesma tela, centralizadas
 *   pelo corpo e com os pés na mesma linha de base; gera as 3 direções da esquerda por espelhamento.
 * - Fileira de baixo: 8 partes salvas em art/parts (não integradas ainda).
 * - Gera uma prévia com as 8 direções sobre grama.
 */
public class SpriteTool {
    static int W, H;
    static int[] px;

    public static void main(String[] a) throws Exception {
        File src = new File(a[0]);
        File outRes = new File(a[1]);
        File outParts = new File(a[2]);
        File preview = new File(a[3]);
        int targetH = Integer.parseInt(a[4]);

        BufferedImage in = ImageIO.read(src);
        W = in.getWidth();
        H = in.getHeight();
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        img.getGraphics().drawImage(in, 0, 0, null);
        px = img.getRGB(0, 0, W, H, null, 0, W);
        System.out.println("folha " + W + "x" + H + " hasAlpha=" + in.getColorModel().hasAlpha());

        removeBackground();
        // limpa halo: pixels quase transparentes somem
        for (int i = 0; i < px.length; i++) if (alpha(px[i]) < 28) px[i] = 0;

        // ---- fileiras (bandas de linhas ocupadas) ----
        List<int[]> rows = runs(rowOcc(), 10);
        System.out.println("fileiras: " + rows.size());
        int[] top = rows.get(0);
        int[] bottom = rows.get(rows.size() - 1);
        List<int[]> views = objects(top);
        List<int[]> parts = objects(bottom);
        System.out.println("vistas: " + views.size() + "  partes: " + parts.size());
        if (views.size() != 5) throw new IllegalStateException("esperava 5 vistas, achei " + views.size());

        // ---- vistas ----
        BufferedImage[] crops = new BufferedImage[5];
        int[] anchor = new int[5];
        int maxL = 0, maxR = 0, maxH = 0;
        for (int i = 0; i < 5; i++) {
            int[] b = views.get(i); // x0,y0,x1,y1 (inclusivo)
            crops[i] = crop(b);
            int bh = b[3] - b[1] + 1;
            // centro do corpo: centróide da faixa do capacete/rosto (exclui pluma e espada)
            int ya = b[1] + (int) (bh * 0.25), yb = b[1] + (int) (bh * 0.45);
            long sx = 0, n = 0;
            for (int y = ya; y <= yb; y++) for (int x = b[0]; x <= b[2]; x++) if (alpha(px[y * W + x]) > 128) { sx += x; n++; }
            int ax = (int) (sx / Math.max(1, n)) - b[0];
            anchor[i] = ax;
            maxL = Math.max(maxL, ax);
            maxR = Math.max(maxR, (b[2] - b[0] + 1) - ax);
            maxH = Math.max(maxH, bh);
            System.out.println("vista " + i + " bbox=" + b[0] + "," + b[1] + "-" + b[2] + "," + b[3] + " ancoraX=" + ax);
        }
        int half = Math.max(maxL, maxR) + 6;
        int cw = half * 2;
        int ch = maxH + 10;
        int base = ch - 3;
        BufferedImage[] canv = new BufferedImage[5];
        for (int i = 0; i < 5; i++) {
            BufferedImage c = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = c.createGraphics();
            g.drawImage(crops[i], half - anchor[i], base - crops[i].getHeight(), null);
            g.dispose();
            canv[i] = c;
        }
        double sc = (double) targetH / ch;
        int ow = (int) Math.round(cw * sc);
        if (ow % 2 == 1) ow++;
        int oh = targetH;
        String[] names = {"guerreiro_s", "guerreiro_se", "guerreiro_e", "guerreiro_ne", "guerreiro_n"};
        BufferedImage[] fin = new BufferedImage[8];
        for (int i = 0; i < 5; i++) fin[i] = resize(canv[i], ow, oh);
        // espelhadas: SE->SW, E->W, NE->NW
        fin[5] = mirror(fin[3]); // nw
        fin[6] = mirror(fin[2]); // w
        fin[7] = mirror(fin[1]); // sw
        String[] all = {"guerreiro_s", "guerreiro_se", "guerreiro_e", "guerreiro_ne", "guerreiro_n", "guerreiro_nw", "guerreiro_w", "guerreiro_sw"};
        outRes.mkdirs();
        for (int i = 0; i < 8; i++) ImageIO.write(fin[i], "png", new File(outRes, all[i] + ".png"));
        int baseOut = (int) Math.round(base * sc);
        int contentOut = (int) Math.round(maxH * sc);
        System.out.println("SPRITE W=" + ow + " H=" + oh + " ANCHOR_X=" + (ow / 2) + " BASE_Y=" + baseOut + " CONTENT_H=" + contentOut);

        // ---- partes ----
        String[] pn = {"cabeca", "capacete", "tronco", "braco_1", "braco_2", "perna_1", "perna_2", "espada"};
        outParts.mkdirs();
        for (int i = 0; i < parts.size(); i++) {
            String nm = i < pn.length ? pn[i] : ("parte_" + i);
            ImageIO.write(crop(parts.get(i)), "png", new File(outParts, nm + ".png"));
        }

        // ---- prévia ----
        int[] order = {0, 1, 2, 3, 4, 5, 6, 7};
        String[] lbl = {"S (frente)", "SE", "E (lado)", "NE", "N (costas)", "NW", "W (lado)", "SW"};
        int cell = ow + 20;
        BufferedImage pv = new BufferedImage(cell * 8 + 20, oh + 70, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = pv.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x8D, 0xAA, 0x55));
        g.fillRect(0, 0, pv.getWidth(), pv.getHeight());
        java.util.Random r = new java.util.Random(3);
        for (int i = 0; i < 400; i++) {
            g.setColor(new Color(0x6F, 0x8B, 0x40, 60));
            int x = r.nextInt(pv.getWidth()), y = r.nextInt(pv.getHeight());
            g.drawLine(x, y, x - 2, y - 6);
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        for (int k = 0; k < 8; k++) {
            int x0 = 10 + k * cell + 10;
            int fy = 10 + baseOut;
            g.setColor(new Color(0, 0, 0, 70));
            g.fillOval(x0 + ow / 2 - 34, fy - 9, 68, 18);
            g.drawImage(fin[order[k]], x0, 10, null);
            g.setColor(Color.WHITE);
            int tw = g.getFontMetrics().stringWidth(lbl[k]);
            g.drawString(lbl[k], x0 + ow / 2 - tw / 2, oh + 50);
        }
        g.dispose();
        ImageIO.write(pv, "png", preview);
        System.out.println("ok");
    }

    static int alpha(int c) { return (c >>> 24) & 0xFF; }

    /** Fundo transparente: nada a fazer. Fundo sólido: preenche a partir das bordas com a cor do canto. */
    static void removeBackground() {
        int corner = px[0];
        if (alpha(corner) < 16 && alpha(px[W - 1]) < 16 && alpha(px[px.length - 1]) < 16) {
            System.out.println("fundo já transparente");
            return;
        }
        int cr = (corner >> 16) & 255, cg = (corner >> 8) & 255, cb = corner & 255;
        boolean[] seen = new boolean[px.length];
        int[] stack = new int[px.length];
        int sp = 0;
        for (int x = 0; x < W; x++) { stack[sp++] = x; stack[sp++] = (H - 1) * W + x; }
        for (int y = 0; y < H; y++) { stack[sp++] = y * W; stack[sp++] = y * W + W - 1; }
        int removed = 0;
        while (sp > 0) {
            int i = stack[--sp];
            if (seen[i]) continue;
            seen[i] = true;
            int c = px[i];
            int d = Math.abs(((c >> 16) & 255) - cr) + Math.abs(((c >> 8) & 255) - cg) + Math.abs((c & 255) - cb);
            if (d > 60) continue;
            px[i] = 0;
            removed++;
            int x = i % W, y = i / W;
            if (x > 0) stack[sp++] = i - 1;
            if (x < W - 1) stack[sp++] = i + 1;
            if (y > 0) stack[sp++] = i - W;
            if (y < H - 1) stack[sp++] = i + W;
        }
        System.out.println("fundo removido: " + removed + " px");
    }

    static boolean[] rowOcc() {
        boolean[] o = new boolean[H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (alpha(px[y * W + x]) > 0) { o[y] = true; break; }
        return o;
    }

    /** Sequências de true separadas por pelo menos minGap posições vazias. */
    static List<int[]> runs(boolean[] occ, int minGap) {
        List<int[]> out = new ArrayList<>();
        int start = -1, lastOn = -1;
        for (int i = 0; i < occ.length; i++) {
            if (occ[i]) {
                if (start < 0) start = i;
                else if (i - lastOn - 1 >= minGap) { out.add(new int[]{start, lastOn}); start = i; }
                lastOn = i;
            }
        }
        if (start >= 0) out.add(new int[]{start, lastOn});
        // descarta ruído muito pequeno
        out.removeIf(r -> r[1] - r[0] < 8);
        return out;
    }

    /** Objetos dentro de uma faixa de linhas: separa por colunas vazias e calcula a caixa justa. */
    static List<int[]> objects(int[] band) {
        boolean[] col = new boolean[W];
        for (int x = 0; x < W; x++) for (int y = band[0]; y <= band[1]; y++) if (alpha(px[y * W + x]) > 0) { col[x] = true; break; }
        List<int[]> cs = runs(col, 6);
        List<int[]> out = new ArrayList<>();
        for (int[] c : cs) {
            int y0 = Integer.MAX_VALUE, y1 = -1;
            for (int y = band[0]; y <= band[1]; y++) for (int x = c[0]; x <= c[1]; x++) if (alpha(px[y * W + x]) > 0) { y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
            out.add(new int[]{c[0], y0, c[1], y1});
        }
        return out;
    }

    static BufferedImage crop(int[] b) {
        int w = b[2] - b[0] + 1, h = b[3] - b[1] + 1;
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) o.setRGB(x, y, px[(b[1] + y) * W + b[0] + x]);
        return o;
    }

    /** Redução em etapas com alfa pré-multiplicado (sem halo escuro). */
    static BufferedImage resize(BufferedImage src, int tw, int th) {
        BufferedImage cur = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
        cur.getGraphics().drawImage(src, 0, 0, null);
        int w = cur.getWidth(), h = cur.getHeight();
        while (w / 2 >= tw && h / 2 >= th) {
            w /= 2; h /= 2;
            cur = step(cur, w, h);
        }
        cur = step(cur, tw, th);
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(cur, 0, 0, null);
        return out;
    }

    static BufferedImage step(BufferedImage s, int w, int h) {
        BufferedImage d = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = d.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(s, 0, 0, w, h, null);
        g.dispose();
        return d;
    }

    static BufferedImage mirror(BufferedImage s) {
        BufferedImage d = new BufferedImage(s.getWidth(), s.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = d.createGraphics();
        g.drawImage(s, new AffineTransform(-1, 0, 0, 1, s.getWidth(), 0), null);
        g.dispose();
        return d;
    }
}
