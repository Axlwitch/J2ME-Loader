/*
 * Copyright 2012 Kulikov Dmitriy
 * Copyright 2017-2018 Nikita Shakarun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.microedition.lcdui;

import static javax.microedition.lcdui.game.Sprite.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Build;
import android.util.Log;

import com.mascotcapsule.micro3d.v3.Graphics3D;
import org.json.JSONArray;
import org.json.JSONObject;
import ru.playsoftware.j2meloader.util.TranslationManager;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Graphics implements
		com.vodafone.v10.graphics.j3d.Graphics3D,
		com.motorola.graphics.j3d.Graphics3D,
		com.jblend.graphics.j3d.Graphics3D {
	public static final int HCENTER = 1;
	public static final int VCENTER = 2;
	public static final int LEFT = 4;
	public static final int RIGHT = 8;
	public static final int TOP = 16;
	public static final int BOTTOM = 32;
	public static final int BASELINE = 64;

	public static final int SOLID = 0;
	public static final int DOTTED = 1;

	private final Canvas canvas;
	private final Image image;

	private final Paint drawPaint = new Paint();
	private final Paint fillPaint = new Paint();

	private int translateX;
	private int translateY;

	private final Rect clip = new Rect();
	private final Rect rect = new Rect();
	private final RectF rectF = new RectF();
	private final Path path = new Path();

	private final DashPathEffect dashPathEffect = new DashPathEffect(new float[]{5, 5}, 0);
	private int stroke = SOLID;

	private Font font = Font.getDefaultFont();
	private com.mascotcapsule.micro3d.v3.Graphics3D g3d;

	// Text dumping feature
	private List<TextDumpEntry> textDumpEntries = new ArrayList<>();
	private boolean textDumpEnabled = false;

	// Inner class untuk menyimpan entry text
	public static class TextDumpEntry {
		public String text;
		public int x;
		public int y;
		public int anchor;
		public int color;
		public long timestamp;

		public TextDumpEntry(String text, int x, int y, int anchor, int color) {
			this.text = text;
			this.x = x;
			this.y = y;
			this.anchor = anchor;
			this.color = color;
			this.timestamp = System.currentTimeMillis();
		}

		public JSONObject toJSON() {
			JSONObject obj = new JSONObject();
			try {
				obj.put("text", this.text);
				obj.put("x", this.x);
				obj.put("y", this.y);
				obj.put("anchor", this.anchor);
				obj.put("color", String.format("#%08X", this.color));
				obj.put("timestamp", this.timestamp);
			} catch (Exception e) {
				Log.e("TextDumpEntry", "Error converting to JSON", e);
			}
			return obj;
		}
	}

	Graphics(Image image) {
		this.image = image;
		canvas = new Canvas(image.getBitmap());
		canvas.save();
		canvas.clipRect(image.getBounds());
		canvas.getClipBounds(clip);
		drawPaint.setStyle(Paint.Style.STROKE);
		fillPaint.setStyle(Paint.Style.FILL);
		drawPaint.setAntiAlias(false);
		fillPaint.setAntiAlias(false);
	}

	public void reset(float cl, float ct, float cr, float cb) {
		setColor(0);
		setFont(Font.getDefaultFont());
		setStrokeStyle(SOLID);
		canvas.restoreToCount(1);
		canvas.save();
		canvas.clipRect(cl, ct, cr, cb);
		canvas.getClipBounds(this.clip);
		translateX = 0;
		translateY = 0;
	}

	public Canvas getCanvas() {
		return canvas;
	}

	public void fillPolygon(int[] xPoints, int xOffset, int[] yPoints, int yOffset, int nPoints) {
		if (nPoints > 0) {
			Path path = computePath(xPoints, xOffset, yPoints, yOffset, nPoints);
			canvas.drawPath(path, fillPaint);
		}
	}

	public void drawPolygon(int[] xPoints, int xOffset, int[] yPoints, int yOffset, int nPoints) {
		if (nPoints > 0) {
			Path path = computePath(xPoints, xOffset, yPoints, yOffset, nPoints);
			canvas.drawPath(path, drawPaint);
		}
	}

	private Path computePath(int[] xPoints, int xOffset, int[] yPoints, int yOffset, int nPoints) {
		path.reset();
		path.moveTo((float) xPoints[xOffset], (float) yPoints[yOffset]);
		for (int i = 1; i < nPoints; i++) {
			path.lineTo((float) xPoints[xOffset + i], (float) yPoints[yOffset + i]);
		}
		path.close();
		return path;
	}

	public void setColor(int color) {
		setColorAlpha(color | 0xFF000000);
	}

	public void setColorAlpha(int color) {
		drawPaint.setColor(color);
		fillPaint.setColor(color);
	}

	public void setColor(int r, int g, int b) {
		drawPaint.setARGB(255, r, g, b);
		fillPaint.setARGB(255, r, g, b);
	}

	public void setGrayScale(int value) {
		setColor(value, value, value);
	}

	public int getGrayScale() {
		int argb = drawPaint.getColor();
		int r = argb >> 16 & 0xFF;
		int g = argb >> 8 & 0xFF;
		int b = argb & 0xFF;
		return 0x4CB2 * r + 0x9691 * g + 0x1D3E * b >> 16;
	}

	public int getRedComponent() {
		return (drawPaint.getColor() >> 16) & 0xFF;
	}

	public int getGreenComponent() {
		return (drawPaint.getColor() >> 8) & 0xFF;
	}

	public int getBlueComponent() {
		return drawPaint.getColor() & 0xFF;
	}

	public int getColor() {
		return drawPaint.getColor();
	}

	public int getDisplayColor(int color) {
		return color;
	}

	public void setStrokeStyle(int stroke) {
		this.stroke = stroke;

		if (stroke == DOTTED) {
			drawPaint.setPathEffect(dashPathEffect);
		} else {
			drawPaint.setPathEffect(null);
		}
	}

	public int getStrokeStyle() {
		return stroke;
	}

	public void setFont(Font font) {
		this.font = font == null ? Font.getDefaultFont() : font;
	}

	public Font getFont() {
		return font;
	}

	public void setClip(int x, int y, int width, int height) {
		clip.set(x, y, x + width, y + height);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			canvas.restore();
			canvas.save();
			canvas.translate(translateX, translateY);
			canvas.clipRect(clip);
		} else {
			canvas.clipRect(clip, Region.Op.REPLACE);
		}
		canvas.getClipBounds(clip);
	}

	public void clipRect(int x, int y, int width, int height) {
		clip.set(x, y, x + width, y + height);
		canvas.clipRect(clip);
		canvas.getClipBounds(clip);
	}

	public int getClipX() {
		return clip.left;
	}

	public int getClipY() {
		return clip.top;
	}

	public int getClipWidth() {
		return clip.width();
	}

	public int getClipHeight() {
		return clip.height();
	}

	public void translate(int dx, int dy) {
		translateX += dx;
		translateY += dy;

		canvas.translate(dx, dy);
		canvas.getClipBounds(clip);
	}

	public int getTranslateX() {
		return translateX;
	}

	public int getTranslateY() {
		return translateY;
	}

	public void drawLine(int x1, int y1, int x2, int y2) {
		if (x2 >= x1) {
			x2++;
		} else {
			x1++;
		}

		if (y2 >= y1) {
			y2++;
		} else {
			y1++;
		}

		canvas.drawLine(x1, y1, x2, y2, drawPaint);
	}

	public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
		if (width < 0 || height < 0) return;
		rectF.set(x, y, x + width, y + height);
		canvas.drawArc(rectF, -startAngle, -arcAngle, false, drawPaint);
	}

	public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
		if (width <= 0 || height <= 0) return;
		rectF.set(x, y, x + width, y + height);
		canvas.drawArc(rectF, -startAngle, -arcAngle, true, fillPaint);
	}

	public void drawRect(int x, int y, int width, int height) {
		if (width < 0 || height < 0) return;
		canvas.drawRect(x, y, x + width, y + height, drawPaint);
	}

	public void fillRect(int x, int y, int width, int height) {
		if (width <= 0 || height <= 0) return;
		canvas.drawRect(x, y, x + width, y + height, fillPaint);
	}

	public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
		if (width < 0 || height < 0) return;
		rectF.set(x, y, x + width, y + height);
		canvas.drawRoundRect(rectF, arcWidth * 0.5f, arcHeight * 0.5f, drawPaint);
	}

	public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
		if (width < 0 || height < 0) return;
		rectF.set(x, y, x + width, y + height);
		canvas.drawRoundRect(rectF, arcWidth * 0.5f, arcHeight * 0.5f, fillPaint);
	}

	public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {
		fillPolygon(new int[]{x1, x2, x3}, 0, new int[]{y1, y2, y3}, 0, 3);
	}

	public void drawChar(char character, int x, int y, int anchor) {
		drawChars(new char[]{character}, 0, 1, x, y, anchor);
	}

	public void drawChars(char[] data, int offset, int length, int x, int y, int anchor) {
		Paint paint = font.paint;
		if ((anchor & Graphics.RIGHT) != 0) {
			paint.setTextAlign(Paint.Align.RIGHT);
		} else if ((anchor & Graphics.HCENTER) != 0) {
			paint.setTextAlign(Paint.Align.CENTER);
		} else {
			paint.setTextAlign(Paint.Align.LEFT);
		}

		float ly;
		if ((anchor & Graphics.BOTTOM) != 0) {
			ly = y - font.descent;
		} else if ((anchor & Graphics.VCENTER) != 0) {
			ly = y - (font.descent + font.ascent) / 2.0f;
		} else if ((anchor & Graphics.BASELINE) != 0) {
			ly = y;
		} else {
			ly = y - font.ascent;
		}

		paint.setColor(fillPaint.getColor());
		canvas.drawText(data, offset, length, x, ly, paint);

		// Dump text jika enabled
		if (textDumpEnabled) {
			String text = new String(data, offset, length);
			addTextDumpEntry(text, x, y, anchor);
		}
	}

	public void drawString(String text, int x, int y, int anchor) {
		text = TranslationManager.processString(text);

		Paint paint = font.paint;
		if ((anchor & Graphics.RIGHT) != 0) {
			paint.setTextAlign(Paint.Align.RIGHT);
		} else if ((anchor & Graphics.HCENTER) != 0) {
			paint.setTextAlign(Paint.Align.CENTER);
		} else {
			paint.setTextAlign(Paint.Align.LEFT);
		}

		float ly;
		if ((anchor & Graphics.BOTTOM) != 0) {
			ly = y - font.descent;
		} else if ((anchor & Graphics.VCENTER) != 0) {
			ly = y - (font.descent + font.ascent) / 2.0f;
		} else if ((anchor & Graphics.BASELINE) != 0) {
			ly = y;
		} else {
			ly = y - font.ascent;
		}

		paint.setColor(fillPaint.getColor());
		canvas.drawText(text, x, ly, paint);

		// Dump text jika enabled
		if (textDumpEnabled) {
			addTextDumpEntry(text, x, y, anchor);
		}
	}

	public void drawImage(Image image, int x, int y, int anchor) {
		float lx;
		if ((anchor & Graphics.RIGHT) != 0) {
			lx = x - image.getWidth();
		} else if ((anchor & Graphics.HCENTER) != 0) {
			lx = x - image.getWidth() / 2.0f;
		} else {
			lx = x;
		}

		float ly;
		if ((anchor & Graphics.BOTTOM) != 0) {
			ly = y - image.getHeight();
		} else if ((anchor & Graphics.VCENTER) != 0) {
			ly = y - image.getHeight() / 2.0f;
		} else {
			ly = y;
		}

		canvas.drawBitmap(image.getBitmap(), lx, ly, null);
	}

	public void drawSubstring(String str, int offset, int len, int x, int y, int anchor) {
		if (str == null) return;
		String sub = str.substring(offset, offset + len);
		sub = TranslationManager.processString(sub);
		drawString(sub, x, y, anchor);
	}

	public void drawRegion(Image image, int x_src, int y_src, int width, int height,
						   int transform, int x_dst, int y_dst, int anchor) {
		if (width <= 0 || height <= 0) return;

		Rect srcR = rect;
		RectF dstR = rectF;
		float dx;
		float dy;
		srcR.set(x_src, y_src, x_src + width, y_src + height);

		Matrix matrix = new Matrix();
		switch (transform) {
			case TRANS_NONE: {
				if ((anchor & Graphics.RIGHT) != 0) {
					dx = x_dst - width;
				} else if ((anchor & Graphics.HCENTER) != 0) {
					dx = x_dst - width / 2.0f;
				} else {
					dx = x_dst;
				}
				if ((anchor & Graphics.BOTTOM) != 0) {
					dy = y_dst - height;
				} else if ((anchor & Graphics.VCENTER) != 0) {
					dy = y_dst - height / 2.0f;
				} else {
					dy = y_dst;
				}

				dstR.set(dx, dy, dx + width, dy + height);
				canvas.drawBitmap(image.getBitmap(), srcR, dstR, null);
				return;
			}
			case TRANS_ROT90:
				matrix.preRotate(90);
				break;
			case TRANS_ROT180:
				matrix.preRotate(180);
				break;
			case TRANS_ROT270:
				matrix.preRotate(270);
				break;
			case TRANS_MIRROR:
				matrix.preScale(-1, 1);
				break;
			case TRANS_MIRROR_ROT90:
				matrix.preRotate(90);
				matrix.preScale(-1, 1);
				break;
			case TRANS_MIRROR_ROT180:
				matrix.preRotate(180);
				matrix.preScale(-1, 1);
				break;
			case TRANS_MIRROR_ROT270:
				matrix.preRotate(270);
				matrix.preScale(-1, 1);
				break;
			default:
				throw new IllegalArgumentException("Illegal transform=" + transform);
		}

		dstR.set(0, 0, width, height);
		matrix.mapRect(dstR);

		if ((anchor & Graphics.RIGHT) != 0) {
			dx = x_dst - dstR.width();
		} else if ((anchor & Graphics.HCENTER) != 0) {
			dx = x_dst - dstR.width() / 2.0f;
		} else {
			dx = x_dst;
		}
		if ((anchor & Graphics.BOTTOM) != 0) {
			dy = y_dst - dstR.height();
		} else if ((anchor & Graphics.VCENTER) != 0) {
			dy = y_dst - dstR.height() / 2.0f;
		} else {
			dy = y_dst;
		}

		matrix.postTranslate(Math.round(dx - dstR.left), Math.round(dy - dstR.top));
		dstR.set(0, 0, width, height);

		canvas.save();
		canvas.concat(matrix);
		try {
			canvas.drawBitmap(image.getBitmap(), srcR, dstR, null);
		} finally {
			canvas.restore();
		}
	}

	public void drawRGB(int[] rgbData, int offset, int scanlength, int x, int y, int width, int height, boolean processAlpha) {
		if (rgbData == null) {
			throw new NullPointerException();
		}
		if (width == 0 || height == 0) {
			return;
		}
		if (offset < 0 || offset > rgbData.length) {
			throw new ArrayIndexOutOfBoundsException();
		}
		if (width < 0 || height < 0) {
			Log.w(getClass().getSimpleName(), "drawRGB: width=" + width + ", height=" + height);
		}
		if (scanlength > 0) {
			if (offset + scanlength * (height - 1) + width > rgbData.length) {
				throw new ArrayIndexOutOfBoundsException();
			}
		} else {
			if (offset + width > rgbData.length || offset + scanlength * (height - 1) < 0) {
				throw new ArrayIndexOutOfBoundsException();
			}
		}

		int[] pixels = new int[height * width];
		int alphaCorrection = processAlpha ? Color.TRANSPARENT : Color.BLACK;
		for (int i = 0; i < height; i++) {
			int s = offset + i * scanlength;
			int d = i * width;
			for (int j = 0; j < width; j++) {
				int pixel = rgbData[s++];
				pixels[d + j] = alphaCorrection | pixel;
			}
		}
		canvas.drawBitmap(pixels, 0, width, x, y, width, height, true, null);
	}

	public void copyArea(int x_src, int y_src, int width, int height,
						 int x_dest, int y_dest, int anchor) {
		if (width <= 0 || height <= 0) return;
		final int[] pixels = new int[width * height];
		image.getBitmap().getPixels(pixels, 0, width, x_src, y_src, width, height);
		float dx;
		if ((anchor & Graphics.RIGHT) != 0) {
			dx = x_dest - width;
		} else if ((anchor & Graphics.HCENTER) != 0) {
			dx = x_dest - width / 2.0f;
		} else {
			dx = x_dest;
		}
		float dy;
		if ((anchor & Graphics.BOTTOM) != 0) {
			dy = y_dest - height;
		} else if ((anchor & Graphics.VCENTER) != 0) {
			dy = y_dest - height / 2.0f;
		} else {
			dy = y_dest;
		}
		canvas.drawBitmap(pixels, 0, width, dx, dy, width, height, false, null);
	}

	public void getPixels(int[] pixels, int offset, int stride,
						  int x, int y, int width, int height) {
		Bitmap b = image.getBitmap();
		int w = Math.min(width, b.getWidth() - x);
		int h = Math.min(height, b.getHeight() - y);
		b.getPixels(pixels, offset, stride, x, y, w, h);
	}

	public Bitmap getBitmap() {
		return image.getBitmap();
	}

	void flush(Image image, int x, int y, int width, int height) {
		rect.set(x, y, x + width, y + height);
		canvas.drawBitmap(image.getBitmap(), rect, rect, null);
	}

	@Override
	public synchronized void drawFigure(com.vodafone.v10.graphics.j3d.Figure figure,
										int x, int y,
										com.vodafone.v10.graphics.j3d.FigureLayout layout,
										com.vodafone.v10.graphics.j3d.Effect3D effect) {
		if (g3d == null) g3d = new Graphics3D();
		g3d.bind(this);
		g3d.drawFigure(figure, x, y, layout, effect);
		g3d.release(this);
	}

	@Override
	public synchronized void drawFigure(com.motorola.graphics.j3d.Figure figure,
										int x, int y,
										com.motorola.graphics.j3d.FigureLayout layout,
										com.motorola.graphics.j3d.Effect3D effect) {
		if (g3d == null) g3d = new Graphics3D();
		g3d.bind(this);
		g3d.drawFigure(figure, x, y, layout, effect);
		g3d.release(this);
	}

	@Override
	public synchronized void drawFigure(com.jblend.graphics.j3d.Figure figure,
										int x, int y,
										com.jblend.graphics.j3d.FigureLayout layout,
										com.jblend.graphics.j3d.Effect3D effect) {
		if (g3d == null) g3d = new Graphics3D();
		g3d.bind(this);
		g3d.drawFigure(figure, x, y, layout, effect);
		g3d.release(this);
	}

	// ===== TEXT DUMPING FEATURE =====

	/**
	 * Mengaktifkan atau menonaktifkan text dumping
	 */
	public void setTextDumpEnabled(boolean enabled) {
		this.textDumpEnabled = enabled;
		if (!enabled) {
			textDumpEntries.clear();
		}
	}

	/**
	 * Menambahkan entry text ke dump list
	 */
	private void addTextDumpEntry(String text, int x, int y, int anchor) {
		if (text != null && !text.isEmpty()) {
			textDumpEntries.add(new TextDumpEntry(text, x, y, anchor, fillPaint.getColor()));
		}
	}

	/**
	 * Menyimpan text dump ke file JSON
	 */
	public void saveTextDumpToJSON(File outputFile) {
		if (textDumpEntries.isEmpty()) {
			Log.w("Graphics", "Text dump entries is empty");
			return;
		}

		try {
			JSONObject root = new JSONObject();
			JSONArray textArray = new JSONArray();

			for (TextDumpEntry entry : textDumpEntries) {
				textArray.put(entry.toJSON());
			}

			root.put("version", "1.0");
			root.put("timestamp", System.currentTimeMillis());
			root.put("textCount", textDumpEntries.size());
			root.put("texts", textArray);

			// Write to file
			try (FileWriter writer = new FileWriter(outputFile)) {
				writer.write(root.toString(2)); // Pretty print with 2 space indentation
			}

			Log.i("Graphics", "Text dump saved to: " + outputFile.getAbsolutePath());
		} catch (IOException | org.json.JSONException e) {
			Log.e("Graphics", "Error saving text dump to JSON", e);
		}
	}

	/**
	 * Menyimpan text dump ke file JSON dengan path string
	 */
	public void saveTextDumpToJSON(String filePath) {
		saveTextDumpToJSON(new File(filePath));
	}

	/**
	 * Mendapatkan list dari semua text entries
	 */
	public List<TextDumpEntry> getTextDumpEntries() {
		return new ArrayList<>(textDumpEntries);
	}

	/**
	 * Menghapus semua text entries
	 */
	public void clearTextDumpEntries() {
		textDumpEntries.clear();
	}

	/**
	 * Mendapatkan jumlah text entries
	 */
	public int getTextDumpCount() {
		return textDumpEntries.size();
	}
}
