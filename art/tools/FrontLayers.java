import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Vistas frente / 3/4 frente / 3/4 costas / costas: separa as duas pernas logo abaixo da barra da túnica
 * (perna esquerda e direita) e deixa o resto como camada do corpo (sem as pernas, para não desenhar em dobro).
 * A espada continua no corpo (polígono por vista).
 * Uso: java FrontLayers.java <pasta res> <pasta debug> [perfil=guerreiro|aprendiz|mago]
 * Perfil aprendiz: lê aprendiz_*.png, grava aprendiz_frente_*.png (túnica bege, mãos de pele em vez de guarda dourada).
 */
public class FrontLayers {
    static int W, H;

    static class View {
        String name;
        int legL, legR, split;
        int[][] blade;
        View(String n, int l, int r, int sp, int[][] b) { name = n; legL = l; legR = r; split = sp; blade = b; }
    }

    static View[] VIEWS = {
            new View("s", 58, 202, 128, new int[][]{{30, 310}, {84, 316}, {80, 340}, {62, 372}, {34, 430}, {14, 428}, {20, 368}, {38, 330}}),
            new View("se", 70, 204, 133, new int[][]{{34, 310}, {88, 318}, {80, 342}, {58, 386}, {32, 430}, {14, 428}, {22, 370}, {42, 330}}),
            new View("ne", 60, 192, 122, new int[][]{{158, 312}, {206, 300}, {214, 322}, {234, 418}, {222, 428}, {184, 346}, {166, 340}}),
            new View("n", 56, 194, 127, new int[][]{{174, 312}, {213, 296}, {224, 318}, {244, 420}, {232, 430}, {194, 348}, {178, 340}}),
    };

    /** Aprendiz (sprite 262x420): pernas, divisão e polígono da lâmina por vista. */
    static final View[] VIEWS_APR = {
            new View("s", 58, 208, 130, new int[][]{{30, 298}, {80, 304}, {78, 322}, {60, 345}, {24, 412}, {10, 412}, {14, 375}, {38, 320}}),
            new View("se", 72, 220, 142, new int[][]{{40, 298}, {92, 304}, {90, 322}, {70, 345}, {30, 412}, {16, 412}, {20, 375}, {46, 320}}),
            new View("ne", 62, 200, 126, new int[][]{{176, 316}, {232, 298}, {238, 318}, {224, 330}, {254, 412}, {238, 420}, {194, 336}, {180, 332}}),
            new View("n", 62, 200, 130, new int[][]{{180, 312}, {236, 294}, {242, 314}, {228, 326}, {258, 410}, {244, 420}, {198, 336}, {184, 330}}),
    };
    /** Mago (sprite 266x420): sem lâmina; manto azul com barra dourada. */
    static final View[] VIEWS_MAGO = {
            new View("s", 70, 210, 130, new int[][]{{0, 0}, {1, 0}, {1, 1}}),
            new View("se", 78, 220, 142, new int[][]{{0, 0}, {1, 0}, {1, 1}}),
            new View("ne", 85, 210, 140, new int[][]{{0, 0}, {1, 0}, {1, 1}}),
            new View("n", 75, 205, 140, new int[][]{{0, 0}, {1, 0}, {1, 1}}),
    };
    static boolean apr = false;
    static boolean robe = false;

    public static void main(String[] a) throws Exception {
        String pre = "guerreiro_", outPre = "";
        if (a.length > 2 && a[2].equals("aprendiz")) { apr = true; VIEWS = VIEWS_APR; pre = "aprendiz_"; outPre = "aprendiz_"; }
        if (a.length > 2 && a[2].equals("mago")) { apr = true; robe = true; VIEWS = VIEWS_MAGO; pre = "mago_"; outPre = "mago_"; }
        File res = new File(a[0]);
        File dbg = new File(a[1]);
        dbg.mkdirs();
        BufferedImage sheet = null;
        Graphics2D sg = null;
        for (int v = 0; v < VIEWS.length; v++) {
            View vw = VIEWS[v];
            BufferedImage src0 = ImageIO.read(new File(res, pre + vw.name + ".png"));
            W = src0.getWidth();
            H = src0.getHeight();
            if (sheet == null) {
                sheet = new BufferedImage(W * 3 + 40, (H + 10) * VIEWS.length + 10, BufferedImage.TYPE_INT_ARGB);
                sg = sheet.createGraphics();
                sg.setColor(new Color(0x8D, 0xAA, 0x55));
                sg.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            }
            BufferedImage src = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            src.getGraphics().drawImage(src0, 0, 0, null);
            int[] s = src.getRGB(0, 0, W, H, null, 0, W);

            Path2D.Float blade = new Path2D.Float();
            blade.moveTo(vw.blade[0][0], vw.blade[0][1]);
            for (int i = 1; i < vw.blade.length; i++) blade.lineTo(vw.blade[i][0], vw.blade[i][1]);
            blade.closePath();

            // fim da túnica por coluna (azul ou barra clara), procurado só na faixa da cintura/barra
            int[] tb = new int[W];
            boolean[] found = new boolean[W];
            java.util.List<Integer> hems = new java.util.ArrayList<>();
            for (int x = 0; x < W; x++) {
                int b = -1;
                for (int y = robe ? 296 : apr ? 270 : 280; y < (robe ? 372 : apr ? 336 : 352); y++) {
                    int c = s[y * W + x];
                    if ((c >>> 24) < 128 || blade.contains(x + 0.5, y + 0.5)) continue;
                    if (apr ? isTunic(c) : isBlue(c) || isLight(c)) b = Math.max(b, y);
                }
                if (b > 0) { tb[x] = b + 4; found[x] = true; if (x >= vw.legL && x <= vw.legR) hems.add(tb[x]); }
            }
            if (apr) {
                // barra rasgada e remendo escuro: a barra de cada coluna é a mais baixa da vizinhança (±7) + contorno
                int[] t2 = tb.clone();
                for (int x = 0; x < W; x++) {
                    if (!found[x]) continue;
                    for (int k = Math.max(0, x - 7); k <= Math.min(W - 1, x + 7); k++) if (found[k]) t2[x] = Math.max(t2[x], tb[k]);
                    t2[x] += 4;
                }
                tb = t2;
                hems.clear();
                for (int x = vw.legL; x <= vw.legR; x++) if (found[x]) hems.add(tb[x]);
            }
            java.util.Collections.sort(hems);
            int hemMed = hems.isEmpty() ? 330 : hems.get(hems.size() / 2);
            // colunas sem túnica (punhos, guarda): corta na altura típica da barra, abaixo das mãos
            for (int x = 0; x < W; x++) if (!found[x]) tb[x] = hemMed + 4;
            int hemMin = 999, hemMax = 0;
            for (int x = vw.legL; x <= vw.legR; x++) { hemMin = Math.min(hemMin, tb[x]); hemMax = Math.max(hemMax, tb[x]); }

            int[] body = new int[W * H], legL = new int[W * H], legR = new int[W * H];
            int lx0 = W, lx1 = 0, rx0 = W, rx1 = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int i = y * W + x;
                int c = s[i];
                if ((c >>> 24) == 0) continue;
                boolean leg = x >= vw.legL && x <= vw.legR && y > tb[x] && !blade.contains(x + 0.5, y + 0.5) && (apr ? !isSkin(c) : !isLight(c) && !isGold(c));
                if (!leg) body[i] = c;
                else if (x < vw.split) { legL[i] = c; lx0 = Math.min(lx0, x); lx1 = Math.max(lx1, x); }
                else { legR[i] = c; rx0 = Math.min(rx0, x); rx1 = Math.max(rx1, x); }
            }
            // estende as pernas para cima por baixo da túnica (escondido; só aparece se a perna descer/encurtar)
            extendUp(legL, body);
            extendUp(legR, body);

            save(body, new File(res, outPre + "frente_" + vw.name + "_corpo.png"));
            save(legL, new File(res, outPre + "frente_" + vw.name + "_perna_e.png"));
            save(legR, new File(res, outPre + "frente_" + vw.name + "_perna_d.png"));
            int hipY = hemMed;
            System.out.printf("%s: quadril y=%d (barra %d..%d)  perna E x=%d..%d  perna D x=%d..%d%n",
                    vw.name, hipY, hemMin, hemMax, lx0, lx1, rx0, rx1);

            // conferência: original | parado remontado | passo (esquerda levantada) | passo (direita levantada)
            int oy = 10 + v * (H + 10);
            sg.drawImage(src, 10, oy, null);
            sg.drawImage(pose(body, legL, legR, (lx0 + lx1) / 2, (rx0 + rx1) / 2, hipY, 8, 0, 0, 3), W + 20, oy, null);
            sg.drawImage(pose(body, legL, legR, (lx0 + lx1) / 2, (rx0 + rx1) / 2, hipY, 0, 8, 0, -3), W * 2 + 30, oy, null);
        }
        sg.dispose();
        ImageIO.write(sheet, "png", new File(dbg, outPre + "front_layers_check.png"));
        System.out.println("ok");
    }

    /** Remonta com a perna esquerda/direita levantada (px) e encurtada (0,92 no pico) e o corpo deslocado. */
    static BufferedImage pose(int[] body, int[] legL, int[] legR, int cxL, int cxR, int hipY, int liftL, int liftR, int advance, int sway) {
        BufferedImage o = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        leg(g, legL, cxL, hipY, liftL);
        leg(g, legR, cxR, hipY, liftR);
        g.drawImage(img(body), sway, 0, null);
        g.dispose();
        return o;
    }

    static void leg(Graphics2D g, int[] p, int cx, int hipY, int lift) {
        double k = 1.0 - 0.08 * lift / 8.0;
        AffineTransform at = new AffineTransform();
        at.translate(0, -lift);
        at.translate(cx, hipY);
        at.scale(1, k);
        at.translate(-cx, -hipY);
        g.drawImage(img(p), at, null);
    }

    static void extendUp(int[] layer, int[] body) {
        for (int x = 0; x < W; x++) {
            int top = -1;
            for (int y = 0; y < H; y++) if ((layer[y * W + x] >>> 24) > 200 && lum(layer[y * W + x]) > 45) { top = y; break; }
            if (top < 0) continue;
            int c = layer[Math.min(H - 1, top + 2) * W + x];
            if ((c >>> 24) < 200) c = layer[top * W + x];
            for (int y = top - 1; y >= Math.max(0, top - 24); y--) {
                int i = y * W + x;
                if (layer[i] != 0) continue;
                if ((body[i] >>> 24) < 250) break;
                layer[i] = c;
            }
        }
    }

    static boolean isBlue(int c) { int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255; return b > r + 25 && b > g + 5; }

    static boolean isLight(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
        return lum(c) > 165 && mx - mn < 50;
    }

    /** Túnica bege do aprendiz. */
    static boolean isTunic(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        if (robe) return isBlue(c) || isGold(c);
        return lum(c) > 120 && g > 100 && r - b > 35 && r - b < 130 && r - g < 60;
    }

    /** Pele (punhos fechados que descem até a barra). */
    static boolean isSkin(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return r > 200 && g > 130 && b > 100 && r - g > 30;
    }

    static boolean isGold(int c) { int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255; return r > 170 && g > 120 && b < 100; }

    static int lum(int c) { return (((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255)) / 10; }

    static BufferedImage img(int[] p) {
        BufferedImage b = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        b.setRGB(0, 0, W, H, p, 0, W);
        return b;
    }

    static void save(int[] p, File f) throws Exception { ImageIO.write(img(p), "png", f); }
}
