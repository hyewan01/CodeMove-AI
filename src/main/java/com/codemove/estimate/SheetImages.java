package com.codemove.estimate;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * 수기 견적서 사진 → AI에 보낼 사진 묶음 (전체 1장 + 가로 2 × 세로 3 조각 6장).
 * <p>
 * Gemini 는 사진 한 장을 크기와 상관없이 약 1,100 토큰으로 줄여 봐서, A4 한 장의 작은 손글씨(날짜 · 층 · 톤)를 회차마다
 * 다르게 읽었습니다(0915 견적서: 2톤 → 9톤, 날짜 누락). 조각마다 1,100 토큰씩 쓰면 글씨를 약 4배 크게 봅니다.
 * 추가 비용은 장당 약 $0.01.
 */
final class SheetImages {

	private SheetImages() {
	}

	static final int COLS = 2, ROWS = 3;
	/** 조각끼리 겹치는 비율 (칸 경계에 걸친 글씨가 잘리지 않게) */
	static final double OVERLAP = 0.12;
	/** 보내는 사진의 긴 변 최대 픽셀 */
	static final int MAX_SIDE = 1600;

	/** 사진을 읽을 수 없으면(HEIC · WEBP 등 자바가 못 여는 형식) null → 원본 한 장으로 보냄 */
	static List<byte[]> tiles(Path file) throws IOException {
		BufferedImage img = ImageIO.read(file.toFile());
		if (img == null) {
			return null;
		}
		img = upright(rgb(img), orientation(Files.readAllBytes(file)));
		List<byte[]> out = new ArrayList<>();
		out.add(jpeg(scale(img)));
		int w = img.getWidth(), h = img.getHeight();
		double tw = (double) w / COLS, th = (double) h / ROWS;
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLS; c++) {
				int x0 = (int) Math.max(0, c * tw - tw * OVERLAP), y0 = (int) Math.max(0, r * th - th * OVERLAP);
				int x1 = (int) Math.min(w, (c + 1) * tw + tw * OVERLAP), y1 = (int) Math.min(h, (r + 1) * th + th * OVERLAP);
				out.add(jpeg(scale(img.getSubimage(x0, y0, x1 - x0, y1 - y0))));
			}
		}
		return out;
	}

	static String promptNote() {
		return "\n\n## 보내는 사진\n첫 사진은 견적서 전체이고, 그다음 " + (COLS * ROWS) + "장은 같은 견적서를 왼쪽 위부터 가로 " + COLS
				+ " × 세로 " + ROWS + " 로 잘라 크게 본 조각입니다(조각끼리 조금 겹침). 글자는 조각에서 읽고, 어느 칸인지는 전체 사진으로 확인합니다. "
				+ "겹친 부분에 같은 글자가 두 번 보여도 한 번만 옮깁니다.\n";
	}

	private static BufferedImage rgb(BufferedImage img) {
		if (img.getType() == BufferedImage.TYPE_INT_RGB) {
			return img;
		}
		BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(img, 0, 0, java.awt.Color.WHITE, null);
		g.dispose();
		return out;
	}

	private static BufferedImage scale(BufferedImage img) {
		int w = img.getWidth(), h = img.getHeight();
		double k = Math.min(1.0, (double) MAX_SIDE / Math.max(w, h));
		if (k >= 1.0) {
			return img;
		}
		int nw = Math.max(1, (int) Math.round(w * k)), nh = Math.max(1, (int) Math.round(h * k));
		BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.drawImage(img, 0, 0, nw, nh, null);
		g.dispose();
		return out;
	}

	private static byte[] jpeg(BufferedImage img) throws IOException {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		ImageIO.write(img, "jpg", b);
		return b.toByteArray();
	}

	/** EXIF 방향(1 정상, 3 거꾸로, 6 오른쪽으로 90°, 8 왼쪽으로 90°)대로 세움. 자바 ImageIO 는 이 값을 무시함 */
	static BufferedImage upright(BufferedImage img, int orientation) {
		int w = img.getWidth(), h = img.getHeight();
		AffineTransform t = new AffineTransform();
		int nw = w, nh = h;
		switch (orientation) {
			case 3 -> {
				t.translate(w, h);
				t.rotate(Math.PI);
			}
			case 6 -> {
				nw = h;
				nh = w;
				t.translate(h, 0);
				t.rotate(Math.PI / 2);
			}
			case 8 -> {
				nw = h;
				nh = w;
				t.translate(0, w);
				t.rotate(-Math.PI / 2);
			}
			default -> {
				return img;
			}
		}
		BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(img, t, null);
		g.dispose();
		return out;
	}

	/** JPEG 의 EXIF 방향 값 (없으면 1) */
	static int orientation(byte[] b) {
		try {
			if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
				return 1;
			}
			int p = 2;
			while (p + 4 <= b.length && (b[p] & 0xFF) == 0xFF) {
				int marker = b[p + 1] & 0xFF;
				int len = ((b[p + 2] & 0xFF) << 8) | (b[p + 3] & 0xFF);
				if (marker == 0xE1 && p + 10 < b.length && b[p + 4] == 'E' && b[p + 5] == 'x' && b[p + 6] == 'i'
						&& b[p + 7] == 'f') {
					int tiff = p + 10;
					boolean le = b[tiff] == 'I';
					int ifd = tiff + u32(b, tiff + 4, le);
					int n = u16(b, ifd, le);
					for (int i = 0; i < n; i++) {
						int e = ifd + 2 + i * 12;
						if (u16(b, e, le) == 0x0112) {
							return u16(b, e + 8, le);
						}
					}
					return 1;
				}
				if (marker == 0xDA) {
					return 1;
				}
				p += 2 + len;
			}
		} catch (RuntimeException e) {
			// 깨진 EXIF 는 정상 방향으로
		}
		return 1;
	}

	private static int u16(byte[] b, int i, boolean le) {
		return le ? (b[i] & 0xFF) | (b[i + 1] & 0xFF) << 8 : (b[i] & 0xFF) << 8 | (b[i + 1] & 0xFF);
	}

	private static int u32(byte[] b, int i, boolean le) {
		return le ? u16(b, i, true) | u16(b, i + 2, true) << 16 : u16(b, i, false) << 16 | u16(b, i + 2, false);
	}
}
