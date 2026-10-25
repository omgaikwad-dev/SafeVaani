package com.example.woshield;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OfflineNightMapView — 100% Offline Interactive Canvas Vector Radar Map.
 *
 * Runs without requiring internet or tile servers (operates seamlessly in Airplane Mode).
 * Renders:
 * - Real-world coordinates of the 100 Pune/Nagpur seed records from CSV
 * - The mathematically calculated illuminated safe corridor polyline
 * - User's live GPS beacon with pulsing radar ring
 * - Concentric safety rings (500m, 1km, 2km)
 * - Multi-touch pinch-to-zoom and pan
 * - Tap on any POI to inspect details & trigger offline navigation
 */
public class OfflineNightMapView extends View {

    public interface OnPoiSelectedListener {
        void onPoiSelected(NightOwlRecord record);
    }

    // Colors
    private static final int COLOR_BG = Color.parseColor("#0A0D16");
    private static final int COLOR_GRID = Color.parseColor("#151B2B");
    private static final int COLOR_RING = Color.parseColor("#1E273D");
    private static final int COLOR_CORRIDOR_HALO = Color.parseColor("#3300FFB2");
    private static final int COLOR_CORRIDOR_CORE = Color.parseColor("#00E676");
    private static final int COLOR_USER_BEACON = Color.parseColor("#00E5FF");
    private static final int COLOR_DEST_BEACON = Color.parseColor("#FFD600");

    // Coordinates and Points
    private double userLat = 18.5204;
    private double userLng = 73.8567;
    private double destLat = 18.5018;
    private double destLng = 73.9260;

    private final List<NightOwlRecord> allPois = new ArrayList<>();
    private final List<NightOwlRecord> routeWaypoints = new ArrayList<>();
    private NightOwlRecord selectedPoi = null;

    // Viewport transform state
    private float scaleFactor = 1.0f;
    private float panOffsetX = 0f;
    private float panOffsetY = 0f;
    private float lastTouchX;
    private float lastTouchY;
    private boolean isDragging = false;

    // Bounding Box
    private double minLat = 18.45;
    private double maxLat = 18.60;
    private double minLng = 73.75;
    private double maxLng = 73.95;

    // Gesture Detectors
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private OnPoiSelectedListener poiListener;

    // Paints
    private Paint bgPaint;
    private Paint gridPaint;
    private Paint ringPaint;
    private Paint textPaint;
    private Paint badgePaint;
    private Paint corridorHaloPaint;
    private Paint corridorCorePaint;
    private Paint markerPaint;
    private Paint userBeaconPaint;
    private Paint cardPaint;

    // Animation
    private float pulseRadius = 14f;
    private boolean pulseExpanding = true;

    public OfflineNightMapView(Context context) {
        super(context);
        init(context);
    }

    public OfflineNightMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public OfflineNightMapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(COLOR_BG);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(COLOR_GRID);
        gridPaint.setStrokeWidth(1.5f);

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setColor(COLOR_RING);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2f);
        ringPaint.setPathEffect(new DashPathEffect(new float[]{10f, 10f}, 0f));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#B0BEC5"));
        textPaint.setTextSize(spToPx(10));

        badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        corridorHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        corridorHaloPaint.setColor(COLOR_CORRIDOR_HALO);
        corridorHaloPaint.setStyle(Paint.Style.STROKE);
        corridorHaloPaint.setStrokeWidth(dpToPx(14));
        corridorHaloPaint.setStrokeCap(Paint.Cap.ROUND);
        corridorHaloPaint.setStrokeJoin(Paint.Join.ROUND);

        corridorCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        corridorCorePaint.setColor(COLOR_CORRIDOR_CORE);
        corridorCorePaint.setStyle(Paint.Style.STROKE);
        corridorCorePaint.setStrokeWidth(dpToPx(4));
        corridorCorePaint.setStrokeCap(Paint.Cap.ROUND);
        corridorCorePaint.setStrokeJoin(Paint.Join.ROUND);

        markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        userBeaconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        userBeaconPaint.setColor(COLOR_USER_BEACON);

        cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardPaint.setColor(Color.parseColor("#1C2333"));
        cardPaint.setStyle(Paint.Style.FILL);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(0.6f, Math.min(scaleFactor, 5.0f));
                invalidate();
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                return handleMarkerTap(e.getX(), e.getY());
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                scaleFactor = Math.min(scaleFactor * 1.4f, 4.0f);
                invalidate();
                return true;
            }
        });
    }

    public void setOnPoiSelectedListener(OnPoiSelectedListener listener) {
        this.poiListener = listener;
    }

    public void setRouteData(double userLat, double userLng, double destLat, double destLng,
                             List<NightOwlRecord> waypoints, List<NightOwlRecord> allRecords) {
        this.userLat = userLat;
        this.userLng = userLng;
        this.destLat = destLat;
        this.destLng = destLng;

        this.routeWaypoints.clear();
        if (waypoints != null) this.routeWaypoints.addAll(waypoints);

        this.allPois.clear();
        if (allRecords != null) this.allPois.addAll(allRecords);

        calculateBoundingBox();
        scaleFactor = 1.0f;
        panOffsetX = 0f;
        panOffsetY = 0f;
        invalidate();
    }

    public void updateUserLocation(double lat, double lng) {
        this.userLat = lat;
        this.userLng = lng;
        invalidate();
    }

    private void calculateBoundingBox() {
        minLat = Math.min(userLat, destLat);
        maxLat = Math.max(userLat, destLat);
        minLng = Math.min(userLng, destLng);
        maxLng = Math.max(userLng, destLng);

        for (NightOwlRecord w : routeWaypoints) {
            minLat = Math.min(minLat, w.latitude);
            maxLat = Math.max(maxLat, w.latitude);
            minLng = Math.min(minLng, w.longitude);
            maxLng = Math.max(maxLng, w.longitude);
        }

        // Add 25% padding so corridor isn't cropped at edge
        double latPad = Math.max(0.015, (maxLat - minLat) * 0.25);
        double lngPad = Math.max(0.015, (maxLng - minLng) * 0.25);
        minLat -= latPad;
        maxLat += latPad;
        minLng -= lngPad;
        maxLng += lngPad;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // 1. Background
        canvas.drawRect(0, 0, w, h, bgPaint);

        // 2. Grid Mesh
        drawMeshGrid(canvas, w, h);

        // 3. User Concentric Radar Rings
        float[] userScreen = projectGpsToScreen(userLat, userLng, w, h);
        drawRadarRings(canvas, userScreen[0], userScreen[1]);

        // 4. Safe Corridor Glowing Polyline
        drawSafeCorridor(canvas, w, h);

        // 5. All 100 CSV POIs as vector pins
        drawPoiPins(canvas, w, h);

        // 6. User GPS Beacon & Destination Marker
        drawBeacons(canvas, userScreen, projectGpsToScreen(destLat, destLng, w, h));

        // 7. HUD Overlays (Offline Badge & Scale)
        drawHudOverlay(canvas, w, h);

        // 8. Selected POI floating card (if any)
        if (selectedPoi != null) {
            drawSelectedPoiCard(canvas, w, h);
        }
    }

    private void drawMeshGrid(Canvas canvas, int w, int h) {
        int step = (int) dpToPx(38);
        for (int x = 0; x < w; x += step) {
            canvas.drawLine(x, 0, x, h, gridPaint);
        }
        for (int y = 0; y < h; y += step) {
            canvas.drawLine(0, y, w, y, gridPaint);
        }
    }

    private void drawRadarRings(Canvas canvas, float cx, float cy) {
        float r500m = dpToPx(42) * scaleFactor;
        float r1km = dpToPx(84) * scaleFactor;
        float r2km = dpToPx(160) * scaleFactor;

        canvas.drawCircle(cx, cy, r500m, ringPaint);
        canvas.drawCircle(cx, cy, r1km, ringPaint);
        canvas.drawCircle(cx, cy, r2km, ringPaint);

        textPaint.setColor(Color.parseColor("#4A5568"));
        canvas.drawText("500m", cx + r500m + 4, cy - 4, textPaint);
        canvas.drawText("1km", cx + r1km + 4, cy - 4, textPaint);
        canvas.drawText("2km", cx + r2km + 4, cy - 4, textPaint);
    }

    private void drawSafeCorridor(Canvas canvas, int w, int h) {
        if (routeWaypoints.isEmpty()) {
            float[] u = projectGpsToScreen(userLat, userLng, w, h);
            float[] d = projectGpsToScreen(destLat, destLng, w, h);
            canvas.drawLine(u[0], u[1], d[0], d[1], corridorHaloPaint);
            canvas.drawLine(u[0], u[1], d[0], d[1], corridorCorePaint);
            return;
        }

        Path path = new Path();
        float[] start = projectGpsToScreen(userLat, userLng, w, h);
        path.moveTo(start[0], start[1]);

        for (NightOwlRecord wpt : routeWaypoints) {
            float[] pt = projectGpsToScreen(wpt.latitude, wpt.longitude, w, h);
            path.lineTo(pt[0], pt[1]);
        }
        float[] end = projectGpsToScreen(destLat, destLng, w, h);
        path.lineTo(end[0], end[1]);

        canvas.drawPath(path, corridorHaloPaint);
        canvas.drawPath(path, corridorCorePaint);
    }

    private void drawPoiPins(Canvas canvas, int w, int h) {
        for (NightOwlRecord r : allPois) {
            if (r.latitude < minLat || r.latitude > maxLat || r.longitude < minLng || r.longitude > maxLng) {
                continue;
            }

            float[] pt = projectGpsToScreen(r.latitude, r.longitude, w, h);
            boolean isRouteNode = routeWaypoints.contains(r);

            int color = getPoiCategoryColor(r.category);
            markerPaint.setColor(color);

            float radius = isRouteNode ? dpToPx(8) : dpToPx(5);
            if (r == selectedPoi) radius = dpToPx(11);

            markerPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(pt[0], pt[1], radius, markerPaint);

            markerPaint.setStyle(Paint.Style.STROKE);
            markerPaint.setColor(Color.WHITE);
            markerPaint.setStrokeWidth(1.5f);
            canvas.drawCircle(pt[0], pt[1], radius, markerPaint);

            if (isRouteNode || r == selectedPoi) {
                textPaint.setColor(Color.WHITE);
                textPaint.setTextSize(spToPx(9));
                String label = r.name.length() > 14 ? r.name.substring(0, 12) + "…" : r.name;
                canvas.drawText(label, pt[0] + radius + 4, pt[1] + 3, textPaint);
            }
        }
    }

    private void drawBeacons(Canvas canvas, float[] u, float[] d) {
        // User Beacon (Cyan + Radar Pulse)
        userBeaconPaint.setColor(Color.parseColor("#4000E5FF"));
        canvas.drawCircle(u[0], u[1], pulseRadius, userBeaconPaint);
        userBeaconPaint.setColor(COLOR_USER_BEACON);
        canvas.drawCircle(u[0], u[1], dpToPx(7), userBeaconPaint);
        userBeaconPaint.setColor(Color.WHITE);
        canvas.drawCircle(u[0], u[1], dpToPx(3), userBeaconPaint);

        textPaint.setColor(COLOR_USER_BEACON);
        textPaint.setTextSize(spToPx(10));
        canvas.drawText("YOU (GPS)", u[0] + dpToPx(10), u[1] + dpToPx(4), textPaint);

        // Destination Marker (Gold Star/Flag)
        markerPaint.setColor(COLOR_DEST_BEACON);
        markerPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(d[0], d[1], dpToPx(8), markerPaint);
        markerPaint.setColor(Color.BLACK);
        canvas.drawCircle(d[0], d[1], dpToPx(3), markerPaint);

        textPaint.setColor(COLOR_DEST_BEACON);
        canvas.drawText("DESTINATION", d[0] + dpToPx(11), d[1] + dpToPx(4), textPaint);

        // Animate radar pulse
        if (pulseExpanding) {
            pulseRadius += 0.4f;
            if (pulseRadius > dpToPx(20)) pulseExpanding = false;
        } else {
            pulseRadius -= 0.4f;
            if (pulseRadius < dpToPx(10)) pulseExpanding = true;
        }
        if (getVisibility() == VISIBLE) {
            postInvalidateDelayed(60);
        }
    }

    private void drawHudOverlay(Canvas canvas, int w, int h) {
        // Top Offline Vector Radar Pill
        badgePaint.setColor(Color.parseColor("#E60B0E14"));
        badgePaint.setStyle(Paint.Style.FILL);
        RectF badgeRect = new RectF(dpToPx(10), dpToPx(10), dpToPx(270), dpToPx(36));
        canvas.drawRoundRect(badgeRect, dpToPx(8), dpToPx(8), badgePaint);

        badgePaint.setColor(Color.parseColor("#00E676"));
        canvas.drawCircle(dpToPx(22), dpToPx(23), dpToPx(4), badgePaint);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(spToPx(10));
        canvas.drawText("OFFLINE VECTOR RADAR • 100 SEED POIS", dpToPx(32), dpToPx(27), textPaint);

        // Bottom right zoom indicator
        textPaint.setColor(Color.parseColor("#90A4AE"));
        textPaint.setTextSize(spToPx(9));
        canvas.drawText(String.format(Locale.getDefault(), "Zoom: %.1fx", scaleFactor),
                w - dpToPx(70), h - dpToPx(12), textPaint);
    }

    private void drawSelectedPoiCard(Canvas canvas, int w, int h) {
        float cardH = dpToPx(64);
        RectF cardRect = new RectF(dpToPx(12), h - cardH - dpToPx(12), w - dpToPx(12), h - dpToPx(12));

        cardPaint.setColor(Color.parseColor("#F2171E2D"));
        canvas.drawRoundRect(cardRect, dpToPx(12), dpToPx(12), cardPaint);

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.parseColor("#00E676"));
        borderPaint.setStrokeWidth(1.5f);
        canvas.drawRoundRect(cardRect, dpToPx(12), dpToPx(12), borderPaint);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(spToPx(12));
        canvas.drawText(selectedPoi.getCategoryEmoji() + " " + selectedPoi.name,
                cardRect.left + dpToPx(12), cardRect.top + dpToPx(22), textPaint);

        textPaint.setColor(Color.parseColor("#90A4AE"));
        textPaint.setTextSize(spToPx(10));
        canvas.drawText(selectedPoi.areaOrLocation + " • " + selectedPoi.hoursNote + " • " + selectedPoi.distanceKm + " km",
                cardRect.left + dpToPx(12), cardRect.top + dpToPx(40), textPaint);

        textPaint.setColor(Color.parseColor("#00E5FF"));
        canvas.drawText("🧭 TAP AGAIN TO NAVIGATE VIA GOOGLE MAPS",
                cardRect.left + dpToPx(12), cardRect.top + dpToPx(55), textPaint);
    }

    private float[] projectGpsToScreen(double lat, double lng, int w, int h) {
        float pad = dpToPx(24);
        float mapW = w - pad * 2;
        float mapH = h - pad * 2;

        double normX = (lng - minLng) / (maxLng - minLng > 0.0001 ? maxLng - minLng : 0.01);
        double normY = (maxLat - lat) / (maxLat - minLat > 0.0001 ? maxLat - minLat : 0.01);

        float screenX = pad + (float) normX * mapW;
        float screenY = pad + (float) normY * mapH;

        float cx = w / 2f;
        float cy = h / 2f;

        float transformedX = (screenX - cx) * scaleFactor + cx + panOffsetX;
        float transformedY = (screenY - cy) * scaleFactor + cy + panOffsetY;

        return new float[]{transformedX, transformedY};
    }

    private boolean handleMarkerTap(float tapX, float tapY) {
        int w = getWidth();
        int h = getHeight();
        float hitThreshold = dpToPx(26);

        // Check if tap was on the selected POI card to navigate
        if (selectedPoi != null) {
            float cardH = dpToPx(64);
            RectF cardRect = new RectF(dpToPx(12), h - cardH - dpToPx(12), w - dpToPx(12), h - dpToPx(12));
            if (cardRect.contains(tapX, tapY)) {
                if (poiListener != null) {
                    poiListener.onPoiSelected(selectedPoi);
                }
                return true;
            }
        }

        // Check if tapped on any POI
        for (NightOwlRecord r : allPois) {
            float[] pt = projectGpsToScreen(r.latitude, r.longitude, w, h);
            float dx = pt[0] - tapX;
            float dy = pt[1] - tapY;
            if (Math.hypot(dx, dy) <= hitThreshold) {
                selectedPoi = r;
                invalidate();
                if (poiListener != null) {
                    poiListener.onPoiSelected(r);
                }
                return true;
            }
        }

        // Tap on empty space deselects
        if (selectedPoi != null) {
            selectedPoi = null;
            invalidate();
            return true;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                isDragging = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (isDragging && !scaleDetector.isInProgress()) {
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;
                    panOffsetX += dx;
                    panOffsetY += dy;
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                break;
        }
        return true;
    }

    private int getPoiCategoryColor(String cat) {
        if ("hospital".equalsIgnoreCase(cat)) return Color.parseColor("#FF5252");
        if ("transport".equalsIgnoreCase(cat)) return Color.parseColor("#FF9100");
        if ("pharmacy".equalsIgnoreCase(cat)) return Color.parseColor("#00E676");
        if ("public_facility".equalsIgnoreCase(cat)) return Color.parseColor("#2979FF");
        if ("fuel".equalsIgnoreCase(cat)) return Color.parseColor("#FFD600");
        return Color.parseColor("#AB47BC");
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}
