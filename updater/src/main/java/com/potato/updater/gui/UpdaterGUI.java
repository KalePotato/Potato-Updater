package com.potato.updater.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.potato.updater.core.DiffEngine;
import com.potato.updater.config.UpdaterConfig;
import com.potato.updater.config.UpdaterConfigManager;
import com.potato.updater.model.DeleteZone;
import com.potato.updater.model.FileEntry;
import com.potato.updater.model.ModsControl;
import com.potato.updater.model.UpdateInfo;
import com.potato.updater.util.PathResolver;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JScrollBar;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.DefaultCaret;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.RenderingHints.Key;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdaterGUI extends JFrame {

    public enum LogoSlot {
        SMALL_LIGHT,
        SMALL_DARK,
        LARGE_LIGHT,
        LARGE_DARK
    }

    public enum QuickMenuResult {
        CONTINUE,
        MANUAL_SYNC
    }

    private enum QuickMenuChoice {
        THEME,
        MANUAL_SYNC,
        CONTINUE
    }

    private record LogoVariantKey(LogoSlot slot, int height) {
    }

    private static final int SMALL_W = 296;
    private static final int SMALL_H = 130;
    private static final int LARGE_W = 800;
    private static final int LARGE_H = 500;
    private static final int WINDOW_SHADOW_SIZE = 8;
    private static final int LARGE_WINDOW_CORNER_ARC = 18;
    private static final int SMALL_WINDOW_CORNER_ARC = 21;
    private static final float LARGE_WINDOW_SHADOW_STROKE_SCALE = 2.35f;
    private static final float SMALL_WINDOW_SHADOW_STROKE_SCALE = 2.05f;
    private static final float LARGE_WINDOW_SHADOW_ALPHA_RANGE = 8.0f;
    private static final float SMALL_WINDOW_SHADOW_ALPHA_RANGE = 6.25f;
    private static final int SMALL_LOGO_HEIGHT = 42;
    private static final int LARGE_LOGO_HEIGHT = 44;
    private static final int LARGE_WINDOW_BASE_INSET = 20;
    private static final int LARGE_LOGO_TOP_SHIFT = 6;
    private static final int LARGE_HEADER_CONTENT_GAP = 0;
    private static final int LARGE_REVIEW_SECTION_TOP_INSET = 2;
    private static final int LARGE_REVIEW_SECTION_SIDE_INSET = 12;
    private static final int LARGE_REVIEW_SECTION_BOTTOM_INSET = 12;
    private static final int LARGE_WINDOW_ACTION_BOTTOM_INSET = LARGE_WINDOW_BASE_INSET - 1;
    private static final int LARGE_ACTION_TOP_INSET = 5;
    private static final float LARGE_ACTION_BUTTON_VISUAL_Y_OFFSET = 0.0f;
    private static final int LARGE_TOOLBAR_BUTTON_SIZE = 30;
    private static final double[] LOGO_SCALE_BUCKETS = {1.0d, 1.25d, 1.5d, 1.75d, 2.0d, 2.5d, 3.0d, 4.0d};
    private static final Pattern STATUS_FILE_SPLIT_PATTERN = Pattern.compile("^(.*?\\b\\d+/\\d+)(?:\\s+(.*))?$");

    private static final String PAGE_INFO = "PAGE_0";
    private static final String PAGE_SCANNING = "PAGE_SCAN";
    private static final String PAGE_DOWNLOAD = "PAGE_1";
    private static final String PAGE_DELETE = "PAGE_2";
    private static final String PAGE_RESOURCE_PACK = "PAGE_3";
    private static final int PAGE_TRANSITION_DURATION_MS = 350;
    private static final int PAGE_TRANSITION_TIMER_DELAY_MS = 4;
    private static final int PAGE_SCANNING_MIN_VISIBLE_MS = 180;
    private static final String SCROLL_ANIMATION_TIMER_KEY = "potato.scroll.animation.timer";
    private static final int SMOOTH_SCROLL_BASE_DELTA = 52;
    private static final int SMOOTH_SCROLL_DURATION_MS = 160;
    private static final String UPDATER_VERSION_LABEL = "v2.1";
    private static final int COMPACT_PROGRESS_BAR_WIDTH = 80;
    private static final int COMPACT_PROGRESS_BAR_HEIGHT = 4;
    private static final int COMPACT_PROGRESS_SEGMENT_WIDTH = 24;
    private static final int COMPACT_ANIMATION_TIMER_DELAY_MS = 8;
    private static final float COMPACT_LOADING_PROGRESS_SPEED_PX_PER_SECOND = 120.0f;
    private static final float COMPACT_ANIMATION_BASE_FRAME_SECONDS = 0.016f;
    private static final float COMPACT_ANIMATION_MAX_FRAME_SECONDS = 0.05f;
    private static final int THEME_TRANSITION_DURATION_MS = 210;
    private static final Color DIALOG_BACKGROUND = new Color(31, 35, 42);
    private static final Color DIALOG_SURFACE = new Color(37, 42, 50);
    private static final Color DIALOG_TEXT_PRIMARY = new Color(234, 238, 246);
    private static final Color DIALOG_TEXT_SECONDARY = new Color(168, 177, 191);
    private static final Color DIALOG_BUTTON_BACKGROUND = new Color(52, 59, 71);
    private static final Color DIALOG_BUTTON_HOVER = new Color(65, 74, 88);
    private static final Color DIALOG_ACCENT = new Color(45, 133, 230);
    private static final Color DIALOG_ACCENT_HOVER = new Color(58, 149, 241);
    private static final Color DIALOG_ACCENT_PRESSED = new Color(36, 111, 200);
    private static boolean modernDialogLookInitialized = false;

    private final WindowRuntime windowRuntime = WindowRuntime.detect();
    private UpdaterTheme theme;
    private Color bgColor;
    private Color accentColor;
    private String activeThemeMode;
    private final Font titleFontLarge = createUiTitleFont(15);
    private final Font subtitleFontSmall = createUiTextFont(Font.PLAIN, 12);

    private final CountDownLatch latch;
    private final CountDownLatch scanLatch = new CountDownLatch(1);
    private final PathResolver pathResolver;

    private int mouseX;
    private int mouseY;
    private String currentStatusText = "";
    private String persistentWarningText = "";
    private final Map<LogoSlot, BufferedImage> logoImages = new EnumMap<>(LogoSlot.class);
    private final Map<LogoVariantKey, BufferedImage> logoVariantCache = new HashMap<>();
    private UpdateInfo updateInfo;
    private float hoverAnim = 0.0f;
    private float progressOffset = -COMPACT_PROGRESS_SEGMENT_WIDTH;
    private float compactProgressTargetRatio = -1.0f;
    private float compactProgressDisplayRatio = 0.0f;
    private float logoFadeAnim = 0.0f;
    private Timer progressTimer;
    private long compactAnimationLastFrameNanos = System.nanoTime();
    private long loadingProgressStartNanos = compactAnimationLastFrameNanos;
    private volatile boolean hovered = false;
    private volatile boolean isFinishedState = false;
    private volatile boolean isSuccess = true;
    private String finishMessage = "";
    private JPanel cardsPanel;
    private CardLayout cardLayout;
    private int currentWizardStep = 0;
    private DiffEngine.DiffResult originalResult;
    private final Map<FileEntry, Boolean> downloadSelections = new HashMap<>();
    private final Map<DeleteZone.DeleteItem, Boolean> deleteSelections = new HashMap<>();
    private final Map<String, Boolean> resourcePackSelections = new LinkedHashMap<>();
    private final List<ResourcePackReviewItem> resourcePackReviewItems = new ArrayList<>();
    private final Set<String> optionalResourcePackNames = new LinkedHashSet<>();
    private ModsControl modsControl = new ModsControl();
    private final Set<String> optionalModPaths = new LinkedHashSet<>();
    private final Map<String, ModsControl.ModEntry> modEntriesByPath = new HashMap<>();
    private boolean bottomDownloadGroupExpanded = false;
    private volatile boolean userConfirmed = false;
    private volatile boolean userForceContinue = false;
    private volatile boolean decisionMade = false;
    private volatile boolean scanCancelled = false;
    private volatile boolean forceRescanRequested = false;
    private volatile boolean transitionRunning = false;
    private volatile long brandingResolvedAt = 0L;
    private volatile boolean brandingResolved = false;

    private boolean errorHoveredLeft = false;
    private boolean errorHoveredRight = false;
    private boolean windowDragArmed = false;
    private float errorLeftAnim = 0.0f;
    private float errorRightAnim = 0.0f;
    private volatile boolean compactPhase = true;
    private volatile boolean forceRescanGearHovered = false;
    private volatile boolean forceRescanGearInteractionActive = false;
    private volatile boolean forceRescanGearEnabled = false;
    private volatile boolean quickMenuRequested = false;
    private volatile boolean quickMenuDialogOpen = false;
    private volatile boolean compactStatusPinned = false;
    private volatile boolean compactLoadingVisualsReady = true;
    private float forceRescanGearAnim = 0.0f;

    private Timer pageFadeTimer;
    private Timer queuedPageTransitionTimer;
    private volatile Thread pageFadeWorker;
    private volatile long pageFadeSequence = 0L;
    private Timer windowOpacityTimer;
    private Timer themeTransitionTimer;
    private boolean themeTransitionRunning = false;
    private float themeTransitionProgress = 1.0f;
    private UpdaterTheme themeTransitionTargetTheme;
    private LogoSlot themeTransitionFromLogoSlot;
    private LogoSlot themeTransitionToLogoSlot;
    private Point quickMenuDialogLocation;
    private volatile Runnable closeRequestHandler;
    private volatile boolean suppressCloseRequestHandler = false;
    private volatile boolean closeRequestDispatched = false;
    private JLabel scanTitleLabel;
    private JLabel scanDetailLabel;
    private float scanProgressRatio = 0.0f;
    private JPanel scanBar;
    private JButton reviewPreviousButton;
    private JButton reviewNextButton;
    private JButton reviewConfirmButton;
    private JButton reviewCancelButton;
    private float compactContentAlpha = 1.0f;
    private FadablePanel reviewContentPanel;

    private static final class ResourcePackReviewItem {
        private final String fileName;
        private final boolean selectable;
        private final String disabledReason;

        private ResourcePackReviewItem(String fileName, boolean selectable, String disabledReason) {
            this.fileName = fileName;
            this.selectable = selectable;
            this.disabledReason = disabledReason;
        }
    }

    private static final class UpdaterTheme {
        private final boolean dark;
        private final Color windowBackground;
        private final Color windowBorder;
        private final Color textPrimary;
        private final Color textSecondary;
        private final Color textMuted;
        private final Color textSubtle;
        private final Color textDisabled;
        private final Color surface;
        private final Color surfaceBorder;
        private final Color divider;
        private final Color strongDivider;
        private final Color progressTrack;
        private final Color scanTrack;
        private final Color accent;
        private final Color accentHover;
        private final Color success;
        private final Color successText;
        private final Color danger;
        private final Color dangerText;
        private final Color secondaryButtonBackground;
        private final Color secondaryButtonHover;
        private final Color secondaryButtonText;
        private final Color ghostButtonBackground;
        private final Color ghostButtonHover;
        private final Color ghostButtonText;
        private final Color toolbarButtonBackground;
        private final Color toolbarButtonHover;
        private final Color toolbarButtonText;
        private final Color buttonBorder;
        private final Color scrollbarThumb;
        private final Color checkboxFill;
        private final Color checkboxBorder;
        private final Color checkboxDisabledFill;
        private final Color checkboxDisabledBorder;
        private final Color checkboxMark;
        private final Color gearBackground;
        private final Color gearHoverBackground;
        private final Color gearBorder;
        private final Color gearIcon;

        private UpdaterTheme(boolean dark,
                             Color windowBackground,
                             Color windowBorder,
                             Color textPrimary,
                             Color textSecondary,
                             Color textMuted,
                             Color textSubtle,
                             Color textDisabled,
                             Color surface,
                             Color surfaceBorder,
                             Color divider,
                             Color strongDivider,
                             Color progressTrack,
                             Color scanTrack,
                             Color accent,
                             Color accentHover,
                             Color success,
                             Color successText,
                             Color danger,
                             Color dangerText,
                             Color secondaryButtonBackground,
                             Color secondaryButtonHover,
                             Color secondaryButtonText,
                             Color ghostButtonBackground,
                             Color ghostButtonHover,
                             Color ghostButtonText,
                             Color toolbarButtonBackground,
                             Color toolbarButtonHover,
                             Color toolbarButtonText,
                             Color buttonBorder,
                             Color scrollbarThumb,
                             Color checkboxFill,
                             Color checkboxBorder,
                             Color checkboxDisabledFill,
                             Color checkboxDisabledBorder,
                             Color checkboxMark,
                             Color gearBackground,
                             Color gearHoverBackground,
                             Color gearBorder,
                             Color gearIcon) {
            this.dark = dark;
            this.windowBackground = windowBackground;
            this.windowBorder = windowBorder;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.textMuted = textMuted;
            this.textSubtle = textSubtle;
            this.textDisabled = textDisabled;
            this.surface = surface;
            this.surfaceBorder = surfaceBorder;
            this.divider = divider;
            this.strongDivider = strongDivider;
            this.progressTrack = progressTrack;
            this.scanTrack = scanTrack;
            this.accent = accent;
            this.accentHover = accentHover;
            this.success = success;
            this.successText = successText;
            this.danger = danger;
            this.dangerText = dangerText;
            this.secondaryButtonBackground = secondaryButtonBackground;
            this.secondaryButtonHover = secondaryButtonHover;
            this.secondaryButtonText = secondaryButtonText;
            this.ghostButtonBackground = ghostButtonBackground;
            this.ghostButtonHover = ghostButtonHover;
            this.ghostButtonText = ghostButtonText;
            this.toolbarButtonBackground = toolbarButtonBackground;
            this.toolbarButtonHover = toolbarButtonHover;
            this.toolbarButtonText = toolbarButtonText;
            this.buttonBorder = buttonBorder;
            this.scrollbarThumb = scrollbarThumb;
            this.checkboxFill = checkboxFill;
            this.checkboxBorder = checkboxBorder;
            this.checkboxDisabledFill = checkboxDisabledFill;
            this.checkboxDisabledBorder = checkboxDisabledBorder;
            this.checkboxMark = checkboxMark;
            this.gearBackground = gearBackground;
            this.gearHoverBackground = gearHoverBackground;
            this.gearBorder = gearBorder;
            this.gearIcon = gearIcon;
        }

        private static UpdaterTheme fromDark(boolean dark) {
            return dark ? dark() : light();
        }

        private static UpdaterTheme light() {
            return new UpdaterTheme(
                    false,
                    new Color(250, 250, 250, 255),
                    new Color(230, 230, 230),
                    new Color(60, 60, 60),
                    new Color(95, 95, 95),
                    new Color(120, 120, 120),
                    new Color(150, 150, 150),
                    new Color(170, 170, 170),
                    new Color(245, 245, 245),
                    new Color(230, 230, 230),
                    new Color(232, 232, 232),
                    new Color(226, 226, 226),
                    new Color(230, 230, 230),
                    new Color(235, 235, 235),
                    new Color(40, 150, 255),
                    new Color(74, 168, 255),
                    new Color(60, 180, 60),
                    new Color(60, 160, 60),
                    new Color(220, 60, 60),
                    new Color(220, 80, 80),
                    new Color(220, 220, 220),
                    new Color(235, 235, 235),
                    new Color(100, 100, 100),
                    new Color(240, 242, 245),
                    new Color(228, 233, 239),
                    new Color(90, 100, 110),
                    new Color(244, 246, 248),
                    new Color(232, 237, 242),
                    new Color(120, 128, 138),
                    new Color(0, 0, 0, 22),
                    new Color(201, 212, 226, 215),
                    new Color(250, 251, 252),
                    new Color(198, 206, 216),
                    new Color(250, 251, 252, 180),
                    new Color(160, 170, 182, 140),
                    Color.WHITE,
                    new Color(245, 246, 248),
                    new Color(232, 238, 244),
                    new Color(205, 212, 220),
                    new Color(120, 128, 138));
        }

        private static UpdaterTheme dark() {
            return new UpdaterTheme(
                    true,
                    new Color(24, 28, 36, 255),
                    new Color(54, 63, 76),
                    new Color(232, 237, 243),
                    new Color(188, 197, 207),
                    new Color(142, 153, 166),
                    new Color(111, 123, 137),
                    new Color(92, 103, 116),
                    new Color(31, 36, 46),
                    new Color(55, 64, 77),
                    new Color(53, 62, 74),
                    new Color(64, 74, 88),
                    new Color(53, 62, 74),
                    new Color(49, 58, 70),
                    new Color(62, 145, 235),
                    new Color(80, 158, 244),
                    new Color(57, 186, 108),
                    new Color(83, 205, 130),
                    new Color(255, 103, 112),
                    new Color(255, 125, 132),
                    new Color(36, 43, 54),
                    new Color(46, 54, 67),
                    new Color(203, 212, 222),
                    new Color(35, 42, 52),
                    new Color(47, 56, 68),
                    new Color(205, 215, 225),
                    new Color(35, 42, 52),
                    new Color(47, 56, 68),
                    new Color(197, 207, 218),
                    new Color(255, 255, 255, 22),
                    new Color(82, 96, 116, 220),
                    new Color(24, 29, 37),
                    new Color(82, 95, 113),
                    new Color(24, 29, 37, 180),
                    new Color(72, 83, 100, 150),
                    Color.WHITE,
                    new Color(35, 42, 52),
                    new Color(47, 57, 70),
                    new Color(76, 89, 107),
                    new Color(194, 205, 216));
        }

        private static UpdaterTheme mix(UpdaterTheme from, UpdaterTheme to, float ratio) {
            float t = Math.max(0.0f, Math.min(1.0f, ratio));
            return new UpdaterTheme(
                    t >= 0.5f ? to.dark : from.dark,
                    mixColor(from.windowBackground, to.windowBackground, t),
                    mixColor(from.windowBorder, to.windowBorder, t),
                    mixColor(from.textPrimary, to.textPrimary, t),
                    mixColor(from.textSecondary, to.textSecondary, t),
                    mixColor(from.textMuted, to.textMuted, t),
                    mixColor(from.textSubtle, to.textSubtle, t),
                    mixColor(from.textDisabled, to.textDisabled, t),
                    mixColor(from.surface, to.surface, t),
                    mixColor(from.surfaceBorder, to.surfaceBorder, t),
                    mixColor(from.divider, to.divider, t),
                    mixColor(from.strongDivider, to.strongDivider, t),
                    mixColor(from.progressTrack, to.progressTrack, t),
                    mixColor(from.scanTrack, to.scanTrack, t),
                    mixColor(from.accent, to.accent, t),
                    mixColor(from.accentHover, to.accentHover, t),
                    mixColor(from.success, to.success, t),
                    mixColor(from.successText, to.successText, t),
                    mixColor(from.danger, to.danger, t),
                    mixColor(from.dangerText, to.dangerText, t),
                    mixColor(from.secondaryButtonBackground, to.secondaryButtonBackground, t),
                    mixColor(from.secondaryButtonHover, to.secondaryButtonHover, t),
                    mixColor(from.secondaryButtonText, to.secondaryButtonText, t),
                    mixColor(from.ghostButtonBackground, to.ghostButtonBackground, t),
                    mixColor(from.ghostButtonHover, to.ghostButtonHover, t),
                    mixColor(from.ghostButtonText, to.ghostButtonText, t),
                    mixColor(from.toolbarButtonBackground, to.toolbarButtonBackground, t),
                    mixColor(from.toolbarButtonHover, to.toolbarButtonHover, t),
                    mixColor(from.toolbarButtonText, to.toolbarButtonText, t),
                    mixColor(from.buttonBorder, to.buttonBorder, t),
                    mixColor(from.scrollbarThumb, to.scrollbarThumb, t),
                    mixColor(from.checkboxFill, to.checkboxFill, t),
                    mixColor(from.checkboxBorder, to.checkboxBorder, t),
                    mixColor(from.checkboxDisabledFill, to.checkboxDisabledFill, t),
                    mixColor(from.checkboxDisabledBorder, to.checkboxDisabledBorder, t),
                    mixColor(from.checkboxMark, to.checkboxMark, t),
                    mixColor(from.gearBackground, to.gearBackground, t),
                    mixColor(from.gearHoverBackground, to.gearHoverBackground, t),
                    mixColor(from.gearBorder, to.gearBorder, t),
                    mixColor(from.gearIcon, to.gearIcon, t));
        }

        private static Color mixColor(Color from, Color to, float ratio) {
            int red = Math.round(from.getRed() + (to.getRed() - from.getRed()) * ratio);
            int green = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * ratio);
            int blue = Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * ratio);
            int alpha = Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * ratio);
            return new Color(red, green, blue, alpha);
        }
    }

    public UpdaterGUI(CountDownLatch latch, PathResolver pathResolver, String themeMode) {
        this.latch = latch;
        this.pathResolver = pathResolver;
        this.activeThemeMode = normalizeThemeMode(themeMode);
        setThemeImmediate(resolveTheme(activeThemeMode));

        configureWindowChrome();
        configureThemeDefaults();
        setSize(windowWidthForContent(SMALL_W), windowHeightForContent(SMALL_H));
        setResizable(false);
        setLocationRelativeTo(null);
        setAlwaysOnTop(windowRuntime.useAlwaysOnTop());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        System.out.println("[PotatoUpdater] Window runtime: " + windowRuntime.describe()
                + " / themeMode=" + activeThemeMode
                + " / effectiveTheme=" + (theme.dark ? "dark" : "light"));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispatchCloseRequest();
            }
        });

        MouseAdapter universalAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (handleQuickMenuInteraction(e)) {
                    windowDragArmed = false;
                    return;
                }
                mouseX = e.getX();
                mouseY = e.getY();
                windowDragArmed = true;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (transitionRunning) {
                    return;
                }
                if (handleQuickMenuInteraction(e)) {
                    return;
                }
                if (isFinishedState && !isSuccess) {
                    Point contentPoint = toContentPoint(e.getPoint());
                    int w = contentWidth();
                    int h = contentHeight();
                    if (contentPoint.x >= w / 2 - 80 && contentPoint.x <= w / 2 - 10 && contentPoint.y >= h - 40 && contentPoint.y <= h - 12) {
                        userForceContinue = true;
                        decisionMade = true;
                    }
                    if (contentPoint.x >= w / 2 + 10 && contentPoint.x <= w / 2 + 80 && contentPoint.y >= h - 40 && contentPoint.y <= h - 12) {
                        userForceContinue = false;
                        decisionMade = true;
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!windowDragArmed || transitionRunning) {
                    return;
                }
                setLocation(getX() + e.getX() - mouseX, getY() + e.getY() - mouseY);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                windowDragArmed = false;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                boolean showForceRescanGear = shouldShowForceRescanGear();
                Point contentPoint = toContentPoint(e.getPoint());
                forceRescanGearHovered = showForceRescanGear && getForceRescanGearBounds().contains(contentPoint);
                forceRescanGearInteractionActive = showForceRescanGear && getForceRescanGearHitBounds().contains(contentPoint);
                if (isFinishedState && !isSuccess) {
                    int w = contentWidth();
                    int h = contentHeight();
                    errorHoveredLeft = contentPoint.x >= w / 2 - 80 && contentPoint.x <= w / 2 - 10 && contentPoint.y >= h - 40 && contentPoint.y <= h - 12;
                    errorHoveredRight = contentPoint.x >= w / 2 + 10 && contentPoint.x <= w / 2 + 80 && contentPoint.y >= h - 40 && contentPoint.y <= h - 12;
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!getContentPane().getBounds().contains(e.getPoint())) {
                    hovered = false;
                }
                forceRescanGearHovered = false;
                forceRescanGearInteractionActive = false;
            }
        };

        addMouseListener(universalAdapter);
        addMouseMotionListener(universalAdapter);

        StagePanel stagePanel = new StagePanel();
        stagePanel.addMouseListener(universalAdapter);
        stagePanel.addMouseMotionListener(universalAdapter);
        setContentPane(stagePanel);

        progressTimer = new Timer(COMPACT_ANIMATION_TIMER_DELAY_MS, e -> {
            long now = System.nanoTime();
            float deltaSeconds = compactAnimationDeltaSeconds(now);
            if (compactProgressTargetRatio >= 0.0f) {
                float clampedTarget = Math.max(0.0f, Math.min(1.0f, compactProgressTargetRatio));
                if (compactProgressDisplayRatio <= clampedTarget) {
                    compactProgressDisplayRatio = smoothToward(compactProgressDisplayRatio, clampedTarget, 0.24f, deltaSeconds);
                }
            } else {
                updateLoadingProgressOffset(now);
            }
            if (brandingResolved && hasDisplayableLogo() && logoFadeAnim < 1.0f) {
                logoFadeAnim = smoothToward(logoFadeAnim, 1.0f, 0.14f, deltaSeconds);
            }
            hoverAnim = smoothToward(hoverAnim, hovered ? 1.0f : 0.0f, 0.18f, deltaSeconds);
            if (isFinishedState && !isSuccess) {
                errorLeftAnim = smoothToward(errorLeftAnim, errorHoveredLeft ? 1.0f : 0.0f, 0.22f, deltaSeconds);
                errorRightAnim = smoothToward(errorRightAnim, errorHoveredRight ? 1.0f : 0.0f, 0.22f, deltaSeconds);
            }
            float gearTarget = shouldShowForceRescanGear() && forceRescanGearHovered ? 1.0f : 0.0f;
            forceRescanGearAnim = smoothToward(forceRescanGearAnim, gearTarget, 0.2f, deltaSeconds);
            repaint();
        });
        progressTimer.start();
    }

    public void setCloseRequestHandler(Runnable closeRequestHandler) {
        this.closeRequestHandler = closeRequestHandler;
    }

    public void resolveBranding(Path logoPath) {
        Map<LogoSlot, Path> logoPaths = null;
        if (logoPath != null) {
            logoPaths = new EnumMap<>(LogoSlot.class);
            logoPaths.put(LogoSlot.SMALL_LIGHT, logoPath);
        }
        setLogos(logoPaths);
    }

    public void resolveBrandingLogos(Map<LogoSlot, Path> logoPaths) {
        setLogos(logoPaths);
    }

    private void setLogos(Map<LogoSlot, Path> logoPaths) {
        logoImages.clear();
        logoVariantCache.clear();
        logoFadeAnim = 0.0f;
        if (logoPaths != null) {
            for (Map.Entry<LogoSlot, Path> entry : logoPaths.entrySet()) {
                LogoSlot slot = entry.getKey();
                BufferedImage image = loadLogoImage(entry.getValue());
                if (slot != null && image != null) {
                    logoImages.put(slot, image);
                    preloadLogoVariants(slot, image);
                }
            }
        }
        if (!hasDisplayableLogo()) {
            logoImages.clear();
            logoVariantCache.clear();
            logoFadeAnim = 1.0f;
        }
        brandingResolved = true;
        brandingResolvedAt = System.currentTimeMillis();
        repaint();
    }

    private BufferedImage loadLogoImage(Path logoPath) {
        if (logoPath == null) {
            return null;
        }
        try {
            if (Files.exists(logoPath)) {
                return ImageIO.read(logoPath.toFile());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public void setUpdateInfo(UpdateInfo info) {
        this.updateInfo = info;
    }

    public void setOptionalResourcePackNames(Set<String> fileNames) {
        optionalResourcePackNames.clear();
        if (fileNames == null) {
            return;
        }
        for (String fileName : fileNames) {
            if (fileName != null && !fileName.isBlank()) {
                optionalResourcePackNames.add(fileName.trim());
            }
        }
    }

    public void setModsControl(ModsControl control) {
        modsControl = control != null ? control : new ModsControl();
        rebuildModsControlIndex();
    }

    public void showLoadingPhase(String initialText) {
        compactPhase = true;
        currentStatusText = initialText;
        persistentWarningText = "";
        isFinishedState = false;
        isSuccess = true;
        finishMessage = "";
        userConfirmed = false;
        userForceContinue = false;
        decisionMade = false;
        scanCancelled = false;
        forceRescanRequested = false;
        forceRescanGearEnabled = false;
        compactStatusPinned = false;
        compactLoadingVisualsReady = true;
        setCompactProgressTarget(-1.0f);
        compactProgressDisplayRatio = 0.0f;
        resetCompactProgressAnimation();
        transitionRunning = false;
        compactContentAlpha = 1.0f;
        reviewContentPanel = null;
        if (windowRuntime.useWindowOpacityEffects()) {
            setWindowOpacitySafe(0.0f);
        }
        setVisible(true);
        configureWindowChrome();
        if (windowRuntime.useStableLinuxWindowMode()) {
            centerWindowWithCurrentSize();
        }
        if (!progressTimer.isRunning()) {
            progressTimer.start();
        }
        if (windowRuntime.useWindowOpacityEffects()) {
            animateWindowOpacity(0.0f, 1.0f, 280, null);
        } else {
            setWindowOpacitySafe(1.0f);
        }
    }

    public void awaitBrandingMinimumDisplay(long minMillis) {
        if (!brandingResolved) {
            resolveBranding(null);
        }
        while (brandingResolvedAt == 0L) {
            if (forceRescanRequested) {
                return;
            }
            sleepQuietly(10L);
        }
        while (System.currentTimeMillis() - brandingResolvedAt < minMillis) {
            if (forceRescanRequested) {
                return;
            }
            sleepQuietly(20L);
        }
    }

    public boolean consumeForceRescanRequested() {
        boolean requested = forceRescanRequested;
        forceRescanRequested = false;
        return requested;
    }

    public void prepareForForcedRescan() {
        compactPhase = true;
        updateStatusText("force check");
        clearPersistentWarningText();
        hovered = true;
        compactStatusPinned = false;
        compactLoadingVisualsReady = true;
        forceRescanGearEnabled = true;
        forceRescanGearHovered = false;
    }

    public void setCompactForceRescanAvailable(boolean available) {
        setCompactQuickMenuAvailable(available);
    }

    public void setCompactQuickMenuAvailable(boolean available) {
        forceRescanGearEnabled = available;
        if (!available) {
            forceRescanGearHovered = false;
            forceRescanGearInteractionActive = false;
            forceRescanGearAnim = 0.0f;
            quickMenuRequested = false;
            quickMenuDialogOpen = false;
        }
        repaint();
    }

    public void showFinishState(boolean success, String message) {
        currentStatusText = message;
        finishMessage = message;
        isSuccess = success;
        forceRescanGearEnabled = false;
        compactStatusPinned = false;
        setCompactProgressTarget(1.0f);
        if (isFinishedState) {
            repaint();
            return;
        }
        animateCompactContentAlpha(compactContentAlpha, 0.0f, 150, () -> {
            isFinishedState = true;
            hoverAnim = 0.0f;
            animateCompactContentAlpha(0.0f, 1.0f, 230, this::repaint);
        });
    }

    public void updateStatusText(String text) {
        currentStatusText = text;
        repaint();
    }

    public void setPersistentWarningText(String text) {
        persistentWarningText = text == null ? "" : text;
        repaint();
    }

    public void clearPersistentWarningText() {
        setPersistentWarningText("");
    }

    public void awaitProgressCompletion(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            sleepQuietly(16L);
        }
    }

    public void updateCompactProgressForStatus(String status, long transferredBytes, long plannedBytes) {
        String normalized = status == null ? "" : status.trim();
        if (normalized.startsWith("download")) {
            if (plannedBytes > 0L) {
                setCompactProgressTarget(transferredBytes / (float) plannedBytes);
            } else {
                setCompactProgressTarget(-1.0f);
            }
        } else if (normalized.startsWith("apply") || normalized.startsWith("finalize")) {
            setCompactProgressTarget(1.0f);
        }
        repaint();
    }

    public void executeExpansionAndShowReview(DiffEngine.DiffResult result) {
        if (transitionRunning) {
            return;
        }
        compactPhase = false;
        forceRescanGearEnabled = false;
        compactStatusPinned = false;
        compactLoadingVisualsReady = false;
        originalResult = result;
        transitionRunning = true;
        animateCompactContentAlpha(1.0f, 0.0f, 160, () -> {
            progressTimer.stop();
            getContentPane().removeAll();
            ((StagePanel) getContentPane()).setExpanded(true);
            getContentPane().setLayout(new BorderLayout());
            currentWizardStep = 0;
            revalidate();
            repaint();

            if (!windowRuntime.useWindowBoundsAnimations()) {
                resizeWindowForCurrentRuntime(LARGE_W, LARGE_H);
                buildReviewPanel();
                if (reviewContentPanel != null) {
                    reviewContentPanel.setAlpha(0.0f);
                }
                animateReviewContentAlpha(0.0f, 1.0f, 180, () -> transitionRunning = false);
                return;
            }

            int startW = getWidth();
            int startH = getHeight();
            int startX = getX();
            int startY = getY();

            Timer expandTimer = createHighFrequencyTimer();
            long startTime = System.nanoTime();
            long duration = 420_000_000L;
            int targetW = windowWidthForContent(LARGE_W);
            int targetH = windowHeightForContent(LARGE_H);
            expandTimer.addActionListener(e -> {
                long elapsed = System.nanoTime() - startTime;
                if (elapsed >= duration) {
                    expandTimer.stop();
                    setBounds(startX - (targetW - startW) / 2, startY - (targetH - startH) / 2, targetW, targetH);
                    buildReviewPanel();
                    if (reviewContentPanel != null) {
                        reviewContentPanel.setAlpha(0.0f);
                    }
                    animateReviewContentAlpha(0.0f, 1.0f, 180, () -> transitionRunning = false);
                } else {
                    float t = elapsed / (float) duration;
                    t = 0.5f - 0.5f * (float) Math.cos(Math.PI * t);
                    int currentW = (int) (startW + (targetW - startW) * t);
                    int currentH = (int) (startH + (targetH - startH) * t);
                    setBounds(startX - (currentW - startW) / 2, startY - (currentH - startH) / 2, currentW, currentH);
                }
            });
            expandTimer.start();
        });
    }

    private void buildReviewPanel() {
        reviewContentPanel = new FadablePanel(new BorderLayout(20, LARGE_HEADER_CONTENT_GAP));
        JPanel mainContent = reviewContentPanel;
        mainContent.setOpaque(false);
        mainContent.setBorder(BorderFactory.createEmptyBorder(
                LARGE_WINDOW_BASE_INSET - LARGE_LOGO_TOP_SHIFT,
                LARGE_WINDOW_BASE_INSET,
                LARGE_WINDOW_ACTION_BOTTOM_INSET,
                LARGE_WINDOW_BASE_INSET));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setPreferredSize(new Dimension(1, LARGE_LOGO_HEIGHT + LARGE_LOGO_TOP_SHIFT));

        JButton minBtn = createToolbarButton("–");
        minBtn.addActionListener(e -> setExtendedState(JFrame.ICONIFIED));

        JPanel rightTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightTools.setOpaque(false);
        rightTools.setBorder(BorderFactory.createEmptyBorder(LARGE_LOGO_TOP_SHIFT, 0, 0, 0));
        rightTools.add(minBtn);
        headerPanel.add(rightTools, BorderLayout.EAST);

        JPanel centerTitles = new JPanel();
        centerTitles.setLayout(new BoxLayout(centerTitles, BoxLayout.Y_AXIS));
        centerTitles.setOpaque(false);
        JPanel logoLabel = createLogoLabel();
        if (logoLabel != null) {
            logoLabel.setAlignmentX(0.5f);
            centerTitles.add(logoLabel);
        } else {
            JLabel headerLabel = new JLabel("Potato Updater 准备就绪");
            headerLabel.setFont(createUiTitleFont(20));
            headerLabel.setForeground(theme.textPrimary);
            headerLabel.setAlignmentX(0.5f);
            centerTitles.add(headerLabel);
        }
        centerTitles.add(Box.createVerticalStrut(0));
        headerPanel.add(centerTitles, BorderLayout.CENTER);

        JPanel leftPlaceholder = new JPanel();
        leftPlaceholder.setOpaque(false);
        leftPlaceholder.setPreferredSize(rightTools.getPreferredSize());
        headerPanel.add(leftPlaceholder, BorderLayout.WEST);
        mainContent.add(headerPanel, BorderLayout.NORTH);

        AnimatedCardsPanel animatedCardsPanel = new AnimatedCardsPanel();
        cardsPanel = animatedCardsPanel;
        cardLayout = new CardLayout();
        cardsPanel.setLayout(cardLayout);
        cardsPanel.setOpaque(false);

        JPanel pageInfo = new JPanel(new BorderLayout());
        pageInfo.setOpaque(false);
        pageInfo.setBorder(BorderFactory.createEmptyBorder(
                LARGE_REVIEW_SECTION_TOP_INSET,
                LARGE_REVIEW_SECTION_SIDE_INSET,
                LARGE_REVIEW_SECTION_BOTTOM_INSET,
                LARGE_REVIEW_SECTION_SIDE_INSET));
        JPanel infoPanel = new OutlinedRoundedPanel(18, theme.surface, theme.surfaceBorder);
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel mainTitle = new JLabel(updateInfo != null ? safeMainTitle(updateInfo) : "发现新版本变动");
        mainTitle.setFont(createUiLogTitleFont(18));
        mainTitle.setForeground(theme.textPrimary);
        mainTitle.setAlignmentX(0.5f);

        JLabel subtitle = new JLabel(updateInfo != null ? safeContentTitle(updateInfo) : "需同步远端基准...");
        subtitle.setFont(createUiLogSubtitleFont(14));
        subtitle.setForeground(accentColor);
        subtitle.setAlignmentX(0.5f);

        JTextArea bodyArea = createBodyTextArea(updateInfo != null ? safeBody(updateInfo)
                : "已发现新的资源更新。请点击【下一步】开始分析具体文件差异并构建部署清单。");

        infoPanel.add(mainTitle);
        infoPanel.add(Box.createVerticalStrut(8));
        infoPanel.add(subtitle);
        infoPanel.add(Box.createVerticalStrut(15));
        infoPanel.add(bodyArea);
        pageInfo.add(infoPanel, BorderLayout.CENTER);
        cardsPanel.add(pageInfo, PAGE_INFO);

        JPanel pageScanning = new JPanel(new GridBagLayout());
        pageScanning.setName(PAGE_SCANNING);
        pageScanning.setOpaque(false);

        JPanel scanContainer = new JPanel();
        scanContainer.setLayout(new BoxLayout(scanContainer, BoxLayout.Y_AXIS));
        scanContainer.setOpaque(false);

        scanTitleLabel = new JLabel("正在准备更新");
        scanTitleLabel.setFont(createUiTitleFont(16));
        scanTitleLabel.setForeground(theme.textPrimary);
        scanTitleLabel.setAlignmentX(0.5f);

        scanDetailLabel = new JLabel("等待开始扫描...");
        scanDetailLabel.setFont(createUiTextFont(Font.PLAIN, 12));
        scanDetailLabel.setForeground(theme.textSubtle);
        scanDetailLabel.setAlignmentX(0.5f);

        scanBar = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                applyQualityRenderingHints(g2);
                g2.setColor(theme.scanTrack);
                g2.fillRoundRect(0, 0, getWidth(), 8, 8, 8);
                int barW = getWidth();
                int fillW = Math.max(18, (int) (barW * scanProgressRatio));
                g2.setClip(new RoundRectangle2D.Float(0, 0, barW, 8.0f, 8.0f, 8.0f));
                g2.setColor(accentColor);
                g2.fillRoundRect(0, 0, fillW, 8, 8, 8);
                g2.setClip(null);
                g2.dispose();
            }
        };
        scanBar.setPreferredSize(new Dimension(400, 10));
        scanBar.setMaximumSize(new Dimension(400, 10));

        scanContainer.add(scanTitleLabel);
        scanContainer.add(Box.createVerticalStrut(10));
        scanContainer.add(scanDetailLabel);
        scanContainer.add(Box.createVerticalStrut(18));
        scanContainer.add(scanBar);
        pageScanning.add(scanContainer);
        cardsPanel.add(pageScanning, PAGE_SCANNING);

        JPanel pageDownload = new JPanel(new BorderLayout());
        pageDownload.setName("pageDownload");
        pageDownload.setOpaque(false);
        cardsPanel.add(pageDownload, PAGE_DOWNLOAD);

        JPanel pageDelete = new JPanel(new BorderLayout());
        pageDelete.setName("pageDelete");
        pageDelete.setOpaque(false);
        cardsPanel.add(pageDelete, PAGE_DELETE);

        JPanel pageResourcePack = new JPanel(new BorderLayout());
        pageResourcePack.setName("pageResourcePack");
        pageResourcePack.setOpaque(false);
        cardsPanel.add(pageResourcePack, PAGE_RESOURCE_PACK);

        mainContent.add(cardsPanel, BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new BorderLayout());
        actionPanel.setOpaque(false);
        actionPanel.setBorder(BorderFactory.createEmptyBorder(LARGE_ACTION_TOP_INSET, 0, 0, 0));

        JPanel leftActionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        leftActionPanel.setOpaque(false);

        JPanel rightActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        rightActionPanel.setOpaque(false);

        Color secondaryButtonBackground = theme.secondaryButtonBackground;
        Color secondaryButtonForeground = theme.secondaryButtonText;

        reviewPreviousButton = createStyledButton("上一步", secondaryButtonBackground, secondaryButtonForeground);
        reviewPreviousButton.setVisible(false);
        reviewPreviousButton.addActionListener(e -> {
            if (transitionRunning) {
                return;
            }
            goToPreviousReviewStep();
        });

        reviewCancelButton = createStyledButton("放弃更新退出", theme.secondaryButtonBackground, theme.secondaryButtonText);
        reviewCancelButton.addActionListener(e -> {
            if (currentWizardStep <= 1) {
                scanCancelled = true;
                reviewCancelButton.setEnabled(false);
                if (reviewPreviousButton != null) {
                    reviewPreviousButton.setEnabled(false);
                }
                if (reviewNextButton != null) {
                    reviewNextButton.setEnabled(false);
                }
                updateStatusText("cancel");
                scanLatch.countDown();
                return;
            }
            userConfirmed = false;
            latch.countDown();
        });

        reviewNextButton = createStyledButton("下一步", accentColor, Color.WHITE);
        Color confirmButtonBackground = blend(theme.success, Color.BLACK, theme.dark ? 0.09f : 0.05f);
        reviewConfirmButton = createStyledButton("确认部署选定项", confirmButtonBackground, Color.WHITE);
        reviewConfirmButton.setVisible(false);
        reviewConfirmButton.setText("开始部署");

        reviewNextButton.addActionListener(e -> {
            if (transitionRunning) {
                return;
            }
            if (currentWizardStep == 0) {
                currentWizardStep = 1;
                showCardAnimated(PAGE_SCANNING, scanLatch::countDown);
                reviewNextButton.setEnabled(false);
                reviewCancelButton.setEnabled(false);
                if (reviewPreviousButton != null) {
                    reviewPreviousButton.setEnabled(false);
                }
            } else if (currentWizardStep == 2) {
                goToDeleteReviewStep();
            } else if (currentWizardStep == 3) {
                goToResourcePackReviewStep();
            }
        });

        reviewConfirmButton.addActionListener(e -> {
            if (transitionRunning) {
                return;
            }
            userConfirmed = true;
            shrinkToLoadingPhase("执行底层部署中...");
        });

        leftActionPanel.add(reviewPreviousButton);
        rightActionPanel.add(reviewCancelButton);
        rightActionPanel.add(reviewNextButton);
        rightActionPanel.add(reviewConfirmButton);
        actionPanel.add(leftActionPanel, BorderLayout.WEST);
        actionPanel.add(rightActionPanel, BorderLayout.EAST);
        mainContent.add(actionPanel, BorderLayout.SOUTH);

        currentWizardStep = 0;
        cardLayout.show(cardsPanel, PAGE_INFO);
        getContentPane().add(mainContent, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    public void updateScanningStatus(String text) {
        SwingUtilities.invokeLater(() -> {
            if (scanTitleLabel != null) {
                scanTitleLabel.setText("ready".equalsIgnoreCase(text) ? "扫描完成" : "正在准备更新");
            }
            updateStatusText(text);
        });
    }

    public void updateScanningProgress(int current, int total, String path, long processedBytes, long totalBytes) {
        SwingUtilities.invokeLater(() -> {
            float ratio;
            if (total <= 0) {
                ratio = 0.0f;
            } else if (totalBytes > 0) {
                ratio = ((current - 1f) + (processedBytes / (float) totalBytes)) / total;
            } else {
                ratio = current / (float) total;
            }
            scanProgressRatio = Math.max(0.04f, Math.min(1.0f, ratio));
            if (scanDetailLabel != null) {
                scanDetailLabel.setText("已扫描 " + current + "/" + total + " · " + abbreviatePath(getContextMappedPath(path)));
            }
            if (scanBar != null) {
                scanBar.repaint();
            }
        });
    }

    public void populateReviewData(DiffEngine.DiffResult result) {
        originalResult = result;
        downloadSelections.clear();
        deleteSelections.clear();
        resourcePackSelections.clear();
        resourcePackReviewItems.clear();
        bottomDownloadGroupExpanded = false;

        for (FileEntry entry : result.toDownloadOrUpdate) {
            downloadSelections.put(entry, !isOptionalModEntry(entry));
        }
        for (DeleteZone.DeleteItem item : result.toDelete) {
            deleteSelections.put(item, true);
        }

        SwingUtilities.invokeLater(() -> {
            Container downloadPage = findPage("pageDownload");
            Container deletePage = findPage("pageDelete");
            Container resourcePackPage = findPage("pageResourcePack");
            if (downloadPage != null) {
                downloadPage.removeAll();
                downloadPage.add(createDownloadListSection("预备载入内容（新增 / 覆盖）", result.toDownloadOrUpdate), BorderLayout.CENTER);
            }
            if (deletePage != null) {
                deletePage.removeAll();
                deletePage.add(createListSection("预备清理内容（移除）", result.toDelete, false), BorderLayout.CENTER);
            }
            if (resourcePackPage != null) {
                resourcePackPage.removeAll();
                resourcePackPage.add(createResourcePackListSection(), BorderLayout.CENTER);
            }

            configureReviewNavigationForResults();

            revalidate();
            repaint();
        });
    }

    public CountDownLatch getScanLatch() {
        return scanLatch;
    }

    public void shrinkToLoadingPhase(String loadingText) {
        compactPhase = true;
        currentStatusText = loadingText;
        forceRescanGearEnabled = false;
        compactStatusPinned = true;
        compactLoadingVisualsReady = false;
        transitionRunning = true;
        animateReviewContentAlpha(1.0f, 0.0f, 150, () -> {
            int startW = getWidth();
            int startH = getHeight();
            int startX = getX();
            int startY = getY();

            getContentPane().removeAll();
            ((StagePanel) getContentPane()).setExpanded(false);
            compactContentAlpha = 0.0f;
            progressTimer.stop();

            if (!windowRuntime.useWindowBoundsAnimations()) {
                resizeWindowForCurrentRuntime(SMALL_W, SMALL_H);
                prepareCompactLoadingFadeIn();
                if (!progressTimer.isRunning()) {
                    progressTimer.start();
                }
                animateCompactContentAlpha(0.0f, 1.0f, 170, () -> {
                    transitionRunning = false;
                    latch.countDown();
                });
                return;
            }

            Timer shrinkTimer = createHighFrequencyTimer();
            long startTime = System.nanoTime();
            long duration = 360_000_000L;
            int targetW = windowWidthForContent(SMALL_W);
            int targetH = windowHeightForContent(SMALL_H);
            shrinkTimer.addActionListener(e -> {
                long elapsed = System.nanoTime() - startTime;
                if (elapsed >= duration) {
                    shrinkTimer.stop();
                    setBounds(startX + (startW - targetW) / 2, startY + (startH - targetH) / 2, targetW, targetH);
                    prepareCompactLoadingFadeIn();
                    if (!progressTimer.isRunning()) {
                        progressTimer.start();
                    }
                    animateCompactContentAlpha(0.0f, 1.0f, 170, () -> {
                        transitionRunning = false;
                        latch.countDown();
                    });
                } else {
                    float t = elapsed / (float) duration;
                    t = 0.5f - 0.5f * (float) Math.cos(Math.PI * t);
                    int currentW = (int) (startW - (startW - targetW) * t);
                    int currentH = (int) (startH - (startH - targetH) * t);
                    setBounds(startX + (startW - currentW) / 2, startY + (startH - currentH) / 2, currentW, currentH);
                }
            });
            shrinkTimer.start();
        });
    }

    private void configureReviewNavigationForResults() {
        if (reviewNextButton != null) {
            reviewNextButton.setEnabled(true);
            reviewNextButton.setVisible(true);
            reviewNextButton.setText("下一步");
        }
        if (reviewConfirmButton != null) {
            reviewConfirmButton.setVisible(false);
        }
        if (reviewCancelButton != null) {
            reviewCancelButton.setEnabled(true);
        }

        currentWizardStep = 2;
        showCardAnimated(PAGE_DOWNLOAD);
        updateReviewActionButtons();
    }

    private void goToDeleteReviewStep() {
        currentWizardStep = 3;
        showCardAnimated(PAGE_DELETE);
        if (reviewNextButton != null) {
            reviewNextButton.setVisible(true);
            reviewNextButton.setText("下一步");
        }
        if (reviewConfirmButton != null) {
            reviewConfirmButton.setVisible(false);
        }
        updateReviewActionButtons();
    }

    private void goToResourcePackReviewStep() {
        rebuildResourcePackReviewItems();
        Container resourcePackPage = findPage("pageResourcePack");
        if (resourcePackPage != null) {
            resourcePackPage.removeAll();
            resourcePackPage.add(createResourcePackListSection(), BorderLayout.CENTER);
            resourcePackPage.revalidate();
            resourcePackPage.repaint();
        }

        currentWizardStep = 4;
        showCardAnimated(PAGE_RESOURCE_PACK);
        if (reviewNextButton != null) {
            reviewNextButton.setVisible(false);
        }
        if (reviewConfirmButton != null) {
            reviewConfirmButton.setVisible(true);
        }
        updateReviewActionButtons();
    }

    private void goToDownloadReviewStep() {
        currentWizardStep = 2;
        showCardAnimated(PAGE_DOWNLOAD);
        updateReviewActionButtons();
    }

    private void goToPreviousReviewStep() {
        if (currentWizardStep == 4) {
            goToDeleteReviewStep();
        } else if (currentWizardStep == 3) {
            goToDownloadReviewStep();
        }
    }

    private void updateReviewActionButtons() {
        if (reviewPreviousButton != null) {
            boolean canGoBack = currentWizardStep >= 3;
            reviewPreviousButton.setEnabled(canGoBack);
            reviewPreviousButton.setVisible(canGoBack);
        }
        if (reviewNextButton != null) {
            reviewNextButton.setEnabled(true);
            reviewNextButton.setVisible(currentWizardStep < 4);
        }
        if (reviewConfirmButton != null) {
            reviewConfirmButton.setVisible(currentWizardStep == 4);
        }
        if (reviewCancelButton != null) {
            reviewCancelButton.setEnabled(true);
        }
    }

    static String determineInitialReviewPage(DiffEngine.DiffResult result) {
        return PAGE_DOWNLOAD;
    }

    private JPanel createDownloadListSection(String title, List<FileEntry> items) {
        List<FileEntry> orderedItems = orderDownloadItems(items);
        JPanel section = new JPanel(new BorderLayout(0, 10));
        section.setOpaque(false);
        section.setLayout(new BorderLayout(0, 10));
        section.setBorder(BorderFactory.createEmptyBorder(
                LARGE_REVIEW_SECTION_TOP_INSET,
                LARGE_REVIEW_SECTION_SIDE_INSET,
                LARGE_REVIEW_SECTION_BOTTOM_INSET,
                LARGE_REVIEW_SECTION_SIDE_INSET));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(createUiSectionTitleFont(16));
        titleLabel.setForeground(theme.successText);

        JLabel countLabel = new JLabel();
        countLabel.setFont(createUiSectionMetaFont(12));
        countLabel.setForeground(theme.textMuted);

        JButton bulkButton = createGhostButton();

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        titleLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        countLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        left.add(titleLabel);
        left.add(Box.createHorizontalStrut(10));
        left.add(countLabel);
        titleRow.add(left, BorderLayout.WEST);

        JLabel hintLineOne = new JLabel("列表中折叠的内容为可选优化模组。请谨慎勾选，不要重复安装。");
        hintLineOne.setFont(createUiSectionMetaFont(12));
        hintLineOne.setForeground(theme.textMuted);

        JPanel textBlock = new JPanel();
        textBlock.setOpaque(false);
        textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        hintLineOne.setAlignmentX(Component.LEFT_ALIGNMENT);
        textBlock.add(titleRow);
        textBlock.add(Box.createVerticalStrut(5));
        textBlock.add(hintLineOne);

        JPanel buttonColumn = new JPanel(new BorderLayout());
        buttonColumn.setOpaque(false);
        buttonColumn.add(bulkButton, BorderLayout.SOUTH);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(textBlock, BorderLayout.WEST);
        headerPanel.add(buttonColumn, BorderLayout.EAST);
        section.add(headerPanel, BorderLayout.NORTH);

        JPanel listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setOpaque(false);

        Runnable refreshAction = () -> {
            int total = countSelectableDownloadItems(orderedItems);
            int selected = countSelectedDownloadItems(orderedItems);
            int requiredTotal = countRequiredSelectableDownloadItems(orderedItems);
            int selectedRequired = countSelectedRequiredDownloadItems(orderedItems);
            countLabel.setText(selected + "/" + total);
            if (total == 0) {
                bulkButton.setText("无可选项");
                bulkButton.setEnabled(false);
            } else if (selectedRequired > 0) {
                bulkButton.setText("清除必选项");
                bulkButton.setEnabled(true);
            } else if (requiredTotal > 0) {
                bulkButton.setText("已选必选项");
                bulkButton.setEnabled(true);
            } else if (selected > 0) {
                bulkButton.setText("清空");
                bulkButton.setEnabled(true);
            } else {
                bulkButton.setText("无必选项");
                bulkButton.setEnabled(false);
            }
        };

        bulkButton.addActionListener(e -> {
            int selectedRequired = countSelectedRequiredDownloadItems(orderedItems);
            int requiredTotal = countRequiredSelectableDownloadItems(orderedItems);
            if (selectedRequired > 0) {
                setRequiredDownloadSelectionState(orderedItems, false);
            } else if (requiredTotal > 0) {
                setRequiredDownloadSelectionState(orderedItems, true);
            } else if (countSelectedDownloadItems(orderedItems) > 0) {
                setAllDownloadSelectionState(orderedItems, false);
            }
            rebuildDownloadListItems(listContainer, orderedItems, refreshAction);
            refreshAction.run();
            listContainer.revalidate();
            listContainer.repaint();
        });

        rebuildDownloadListItems(listContainer, orderedItems, refreshAction);
        refreshAction.run();

        JPanel listViewportPanel = new JPanel(new BorderLayout());
        listViewportPanel.setOpaque(false);
        listViewportPanel.add(listContainer, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(listViewportPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        applyMinimalScrollPaneStyle(scrollPane);

        JPanel listSurface = new OutlinedRoundedPanel(18, theme.surface, theme.surfaceBorder);
        listSurface.setLayout(new BorderLayout());
        listSurface.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        listSurface.add(scrollPane, BorderLayout.CENTER);

        section.add(listSurface, BorderLayout.CENTER);
        return section;
    }

    private JPanel createListSection(String title, List<?> items, boolean isDownloadColumn) {
        JPanel section = new JPanel(new BorderLayout(0, 10));
        section.setOpaque(false);
        section.setLayout(new BorderLayout(0, 10));
        section.setBorder(BorderFactory.createEmptyBorder(
                LARGE_REVIEW_SECTION_TOP_INSET,
                LARGE_REVIEW_SECTION_SIDE_INSET,
                LARGE_REVIEW_SECTION_BOTTOM_INSET,
                LARGE_REVIEW_SECTION_SIDE_INSET));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(createUiSectionTitleFont(16));
        titleLabel.setForeground(isDownloadColumn ? theme.successText : theme.dangerText);

        JLabel countLabel = new JLabel();
        countLabel.setFont(createUiSectionMetaFont(12));
        countLabel.setForeground(theme.textMuted);

        JButton bulkButton = createGhostButton();

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        titleLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        countLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        left.add(titleLabel);
        left.add(Box.createHorizontalStrut(10));
        left.add(countLabel);
        titleRow.add(left, BorderLayout.WEST);
        titleRow.add(bulkButton, BorderLayout.EAST);
        section.add(titleRow, BorderLayout.NORTH);

        JPanel listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setOpaque(false);

        Runnable refreshAction = () -> {
            int total = countSelectableItems(items);
            int selected = countSelectedItems(items, isDownloadColumn);
            countLabel.setText(selected + "/" + total);
            if (total == 0) {
                bulkButton.setText("无可选项");
                bulkButton.setEnabled(false);
            } else if (selected > 0) {
                bulkButton.setText("清空");
                bulkButton.setEnabled(true);
            } else {
                bulkButton.setText("全选");
                bulkButton.setEnabled(true);
            }
        };

        bulkButton.addActionListener(e -> {
            boolean selectAll = countSelectedItems(items, isDownloadColumn) == 0;
            setAllSelectionState(items, isDownloadColumn, selectAll);
            rebuildListItems(listContainer, items, isDownloadColumn, refreshAction);
            refreshAction.run();
            listContainer.revalidate();
            listContainer.repaint();
        });

        rebuildListItems(listContainer, items, isDownloadColumn, refreshAction);
        refreshAction.run();

        JPanel listViewportPanel = new JPanel(new BorderLayout());
        listViewportPanel.setOpaque(false);
        listViewportPanel.add(listContainer, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(listViewportPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        applyMinimalScrollPaneStyle(scrollPane);

        JPanel listSurface = new OutlinedRoundedPanel(18, theme.surface, theme.surfaceBorder);
        listSurface.setLayout(new BorderLayout());
        listSurface.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        listSurface.add(scrollPane, BorderLayout.CENTER);

        section.add(listSurface, BorderLayout.CENTER);
        return section;
    }

    private JPanel createResourcePackListSection() {
        JPanel section = new JPanel(new BorderLayout(0, 10));
        section.setOpaque(false);
        section.setBorder(BorderFactory.createEmptyBorder(
                LARGE_REVIEW_SECTION_TOP_INSET,
                LARGE_REVIEW_SECTION_SIDE_INSET,
                LARGE_REVIEW_SECTION_BOTTOM_INSET,
                LARGE_REVIEW_SECTION_SIDE_INSET));

        JLabel titleLabel = new JLabel("资源包自动安装");
        titleLabel.setFont(createUiSectionTitleFont(16));
        titleLabel.setForeground(accentColor);

        JLabel countLabel = new JLabel();
        countLabel.setFont(createUiSectionMetaFont(12));
        countLabel.setForeground(theme.textMuted);

        JButton bulkButton = createGhostButton();

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        titleLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        countLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        left.add(titleLabel);
        left.add(Box.createHorizontalStrut(10));
        left.add(countLabel);
        titleRow.add(left, BorderLayout.WEST);

        JLabel hintLineOne = new JLabel("选中的资源包将自动添加到游戏中。");
        hintLineOne.setFont(createUiSectionMetaFont(12));
        hintLineOne.setForeground(theme.textMuted);

        JPanel textBlock = new JPanel();
        textBlock.setOpaque(false);
        textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        hintLineOne.setAlignmentX(Component.LEFT_ALIGNMENT);
        textBlock.add(titleRow);
        textBlock.add(Box.createVerticalStrut(5));
        textBlock.add(hintLineOne);

        JPanel buttonColumn = new JPanel(new BorderLayout());
        buttonColumn.setOpaque(false);
        buttonColumn.add(bulkButton, BorderLayout.SOUTH);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(textBlock, BorderLayout.WEST);
        headerPanel.add(buttonColumn, BorderLayout.EAST);
        section.add(headerPanel, BorderLayout.NORTH);

        JPanel listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setOpaque(false);

        Runnable refreshAction = () -> {
            int total = countSelectableResourcePacks();
            int selected = countSelectedResourcePacks();
            countLabel.setText(selected + "/" + total);
            if (total == 0) {
                bulkButton.setText("无可选项");
                bulkButton.setEnabled(false);
            } else if (selected > 0) {
                bulkButton.setText("清空");
                bulkButton.setEnabled(true);
            } else {
                bulkButton.setText("全选");
                bulkButton.setEnabled(true);
            }
        };

        bulkButton.addActionListener(e -> {
            boolean selectAll = countSelectedResourcePacks() == 0;
            setAllResourcePackSelectionState(selectAll);
            rebuildResourcePackListItems(listContainer, refreshAction);
            refreshAction.run();
            listContainer.revalidate();
            listContainer.repaint();
        });

        rebuildResourcePackListItems(listContainer, refreshAction);
        refreshAction.run();

        JPanel listViewportPanel = new JPanel(new BorderLayout());
        listViewportPanel.setOpaque(false);
        listViewportPanel.add(listContainer, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(listViewportPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        applyMinimalScrollPaneStyle(scrollPane);

        JPanel listSurface = new OutlinedRoundedPanel(18, theme.surface, theme.surfaceBorder);
        listSurface.setLayout(new BorderLayout());
        listSurface.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        listSurface.add(scrollPane, BorderLayout.CENTER);

        section.add(listSurface, BorderLayout.CENTER);
        return section;
    }

    private void rebuildResourcePackReviewItems() {
        resourcePackReviewItems.clear();
        if (originalResult == null || originalResult.toDownloadOrUpdate == null) {
            resourcePackSelections.clear();
            return;
        }

        Map<String, Boolean> deployableByName = new LinkedHashMap<>();
        for (FileEntry entry : originalResult.toDownloadOrUpdate) {
            String resourcePackName = extractResourcePackFileName(entry.getPath());
            if (resourcePackName == null) {
                continue;
            }
            boolean deployable = downloadSelections.getOrDefault(entry, false) && isPathBaseValid(entry.getPath());
            deployableByName.put(resourcePackName,
                    deployableByName.getOrDefault(resourcePackName, true) && deployable);
        }

        Map<String, Boolean> nextSelections = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : deployableByName.entrySet()) {
            String fileName = entry.getKey();
            boolean selectable = entry.getValue();
            if (!selectable) {
                continue;
            }
            if (!resourcePackSelections.containsKey(fileName) && optionalResourcePackNames.contains(fileName)) {
                resourcePackSelections.put(fileName, false);
            }
            resourcePackReviewItems.add(new ResourcePackReviewItem(
                    fileName,
                    selectable,
                    selectable ? "" : "部署列表中没有完整选中这个资源包，暂不写入 options.txt"));
            boolean selected = resourcePackSelections.getOrDefault(fileName, selectable);
            nextSelections.put(fileName, selectable && selected);
        }

        resourcePackSelections.clear();
        resourcePackSelections.putAll(nextSelections);
    }

    private void rebuildResourcePackListItems(JPanel listContainer, Runnable refreshAction) {
        listContainer.removeAll();
        if (resourcePackReviewItems.isEmpty()) {
            listContainer.add(createResourcePackEmptyPlaceholder());
            return;
        }
        for (ResourcePackReviewItem item : resourcePackReviewItems) {
            listContainer.add(createResourcePackListItemPanel(item, refreshAction));
            listContainer.add(Box.createVerticalStrut(0));
        }
    }

    private JPanel createResourcePackEmptyPlaceholder() {
        JPanel row = createListItemSurface();
        row.setLayout(new BorderLayout(8, 0));
        row.setBorder(BorderFactory.createEmptyBorder(9, 6, 9, 6));

        JLabel emptyLabel = new JLabel("本次更新没有资源包项目");
        emptyLabel.setFont(createUiSectionMetaFont(13));
        emptyLabel.setForeground(theme.textSubtle);

        row.add(Box.createHorizontalStrut(18), BorderLayout.WEST);
        row.add(emptyLabel, BorderLayout.CENTER);
        return row;
    }

    private JPanel createResourcePackListItemPanel(ResourcePackReviewItem item, Runnable refreshAction) {
        JPanel row = createListItemSurface();
        row.setLayout(new BorderLayout(8, 0));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, theme.divider),
                BorderFactory.createEmptyBorder(9, 6, 9, 6)));

        JCheckBox checkBox = new JCheckBox("", resourcePackSelections.getOrDefault(item.fileName, item.selectable));
        checkBox.setOpaque(false);
        checkBox.setIcon(new ModernCheckBoxIcon(false, true));
        checkBox.setSelectedIcon(new ModernCheckBoxIcon(true, true));
        checkBox.setDisabledIcon(new ModernCheckBoxIcon(false, false));
        checkBox.setDisabledSelectedIcon(new ModernCheckBoxIcon(true, false));
        checkBox.setFocusPainted(false);
        checkBox.setBorderPainted(false);
        checkBox.setContentAreaFilled(false);
        checkBox.setBorder(BorderFactory.createEmptyBorder());
        checkBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        checkBox.setPreferredSize(new Dimension(20, 20));
        checkBox.setEnabled(item.selectable);
        checkBox.addActionListener(e -> {
            resourcePackSelections.put(item.fileName, checkBox.isSelected());
            refreshAction.run();
        });

        JLabel pathLabel = new JLabel(item.fileName);
        pathLabel.setFont(subtitleFontSmall);
        if (item.selectable) {
            pathLabel.setForeground(theme.textPrimary);
        } else {
            pathLabel.setForeground(theme.danger);
            pathLabel.setToolTipText(item.disabledReason);
            checkBox.setSelected(false);
            resourcePackSelections.put(item.fileName, false);
        }

        row.add(checkBox, BorderLayout.WEST);
        row.add(pathLabel, BorderLayout.CENTER);
        return row;
    }

    private int countSelectableResourcePacks() {
        int total = 0;
        for (ResourcePackReviewItem item : resourcePackReviewItems) {
            if (item.selectable) {
                total++;
            }
        }
        return total;
    }

    private int countSelectedResourcePacks() {
        int total = 0;
        for (ResourcePackReviewItem item : resourcePackReviewItems) {
            if (item.selectable && resourcePackSelections.getOrDefault(item.fileName, false)) {
                total++;
            }
        }
        return total;
    }

    private void setAllResourcePackSelectionState(boolean selected) {
        for (ResourcePackReviewItem item : resourcePackReviewItems) {
            if (item.selectable) {
                resourcePackSelections.put(item.fileName, selected);
            }
        }
    }

    private void rebuildDownloadListItems(JPanel listContainer, List<FileEntry> items, Runnable refreshAction) {
        listContainer.removeAll();
        if (items.isEmpty()) {
            listContainer.add(createEmptyListPlaceholder());
            return;
        }

        List<FileEntry> normalItems = new ArrayList<>();
        List<FileEntry> bottomItems = new ArrayList<>();
        for (FileEntry item : items) {
            if ("bottom".equals(placementForDownloadEntry(item))) {
                bottomItems.add(item);
            } else {
                normalItems.add(item);
            }
        }

        for (FileEntry item : normalItems) {
            listContainer.add(createDownloadListItemPanel(item, refreshAction));
            listContainer.add(Box.createVerticalStrut(0));
        }
        if (!bottomItems.isEmpty()) {
            if (!normalItems.isEmpty()) {
                listContainer.add(Box.createVerticalStrut(6));
            }
            listContainer.add(createBottomDownloadGroupPanel(bottomItems, refreshAction));
        }
    }

    private JPanel createBottomDownloadGroupPanel(List<FileEntry> items, Runnable refreshAction) {
        return new CollapsibleDownloadGroupPanel(items, refreshAction);
    }

    private JPanel createDownloadListItemPanel(FileEntry entry, Runnable refreshAction) {
        JPanel row = createListItemSurface();
        row.setLayout(new BorderLayout(8, 0));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, theme.divider),
                BorderFactory.createEmptyBorder(8, 6, 8, 6)));

        String path = entry.getPath();
        boolean valid = isPathBaseValid(path);
        boolean selected = valid && downloadSelections.getOrDefault(entry, !isOptionalModEntry(entry));

        JCheckBox checkBox = new JCheckBox("", selected);
        configureReviewCheckBox(checkBox, valid);
        checkBox.addActionListener(e -> {
            downloadSelections.put(entry, checkBox.isSelected());
            refreshAction.run();
        });

        JLabel nameLabel = new JLabel(displayNameForDownloadEntry(entry));
        nameLabel.setFont(subtitleFontSmall);
        nameLabel.setForeground(valid ? theme.textPrimary : theme.danger);
        nameLabel.setToolTipText(getContextMappedPath(path));

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(nameLabel);

        String remark = displayRemarkForDownloadEntry(entry);
        if (!remark.isEmpty()) {
            JLabel remarkLabel = new JLabel(toFixedWidthHtml(remark, 600));
            remarkLabel.setFont(createUiSectionMetaFont(12));
            remarkLabel.setForeground(theme.textMuted);
            remarkLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            remarkLabel.setToolTipText(null);
            textPanel.add(Box.createVerticalStrut(3));
            textPanel.add(remarkLabel);
        }

        if (!valid) {
            nameLabel.setToolTipText("警告：本地基准路径未挂载或路径非法（" + path + "）");
            checkBox.setSelected(false);
            checkBox.setEnabled(false);
            downloadSelections.put(entry, false);
        }

        row.add(checkBox, BorderLayout.WEST);
        row.add(textPanel, BorderLayout.CENTER);
        return row;
    }

    private void configureReviewCheckBox(JCheckBox checkBox, boolean enabled) {
        checkBox.setOpaque(false);
        checkBox.setIcon(new ModernCheckBoxIcon(false, true));
        checkBox.setSelectedIcon(new ModernCheckBoxIcon(true, true));
        checkBox.setDisabledIcon(new ModernCheckBoxIcon(false, false));
        checkBox.setDisabledSelectedIcon(new ModernCheckBoxIcon(true, false));
        checkBox.setFocusPainted(false);
        checkBox.setBorderPainted(false);
        checkBox.setContentAreaFilled(false);
        checkBox.setBorder(BorderFactory.createEmptyBorder());
        checkBox.setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        checkBox.setPreferredSize(new Dimension(20, 20));
        checkBox.setEnabled(enabled);
    }

    private int countSelectableDownloadItems(List<FileEntry> items) {
        int total = 0;
        for (FileEntry item : items) {
            if (isPathBaseValid(item.getPath())) {
                total++;
            }
        }
        return total;
    }

    private int countRequiredSelectableDownloadItems(List<FileEntry> items) {
        int total = 0;
        for (FileEntry item : items) {
            if (isPathBaseValid(item.getPath()) && !isOptionalModEntry(item)) {
                total++;
            }
        }
        return total;
    }

    private int countSelectedDownloadItems(List<FileEntry> items) {
        int total = 0;
        for (FileEntry item : items) {
            if (isPathBaseValid(item.getPath()) && downloadSelections.getOrDefault(item, false)) {
                total++;
            }
        }
        return total;
    }

    private int countSelectedRequiredDownloadItems(List<FileEntry> items) {
        int total = 0;
        for (FileEntry item : items) {
            if (isPathBaseValid(item.getPath())
                    && !isOptionalModEntry(item)
                    && downloadSelections.getOrDefault(item, false)) {
                total++;
            }
        }
        return total;
    }

    private void setAllDownloadSelectionState(List<FileEntry> items, boolean selected) {
        for (FileEntry item : items) {
            if (!isPathBaseValid(item.getPath())) {
                continue;
            }
            if (selected && isOptionalModEntry(item)) {
                continue;
            }
            downloadSelections.put(item, selected);
        }
    }

    private void setRequiredDownloadSelectionState(List<FileEntry> items, boolean selected) {
        for (FileEntry item : items) {
            if (!isPathBaseValid(item.getPath()) || isOptionalModEntry(item)) {
                continue;
            }
            downloadSelections.put(item, selected);
        }
    }

    private void rebuildListItems(JPanel listContainer, List<?> items, boolean isDownloadColumn, Runnable refreshAction) {
        listContainer.removeAll();
        if (items.isEmpty()) {
            listContainer.add(createEmptyListPlaceholder());
            return;
        }
        for (Object item : items) {
            boolean selected = isDownloadColumn
                    ? downloadSelections.getOrDefault(item, true)
                    : deleteSelections.getOrDefault(item, true);
            listContainer.add(createListItemPanel(item, selected, isDownloadColumn, refreshAction));
            listContainer.add(Box.createVerticalStrut(0));
        }
    }

    private JPanel createEmptyListPlaceholder() {
        JPanel row = createListItemSurface();
        row.setLayout(new BorderLayout(8, 0));
        row.setBorder(BorderFactory.createEmptyBorder(9, 6, 9, 6));

        JLabel emptyLabel = new JLabel("无变更项目");
        emptyLabel.setFont(createUiSectionMetaFont(13));
        emptyLabel.setForeground(theme.textSubtle);

        row.add(Box.createHorizontalStrut(18), BorderLayout.WEST);
        row.add(emptyLabel, BorderLayout.CENTER);
        return row;
    }

    private JPanel createListItemPanel(Object obj, boolean selected, boolean isDownloadColumn, Runnable refreshAction) {
        JPanel row = createListItemSurface();
        row.setLayout(new BorderLayout(8, 0));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, theme.divider),
                BorderFactory.createEmptyBorder(9, 6, 9, 6)));

        JCheckBox checkBox = new JCheckBox("", selected);
        checkBox.setOpaque(false);
        checkBox.setIcon(new ModernCheckBoxIcon(false, true));
        checkBox.setSelectedIcon(new ModernCheckBoxIcon(true, true));
        checkBox.setDisabledIcon(new ModernCheckBoxIcon(false, false));
        checkBox.setDisabledSelectedIcon(new ModernCheckBoxIcon(true, false));
        checkBox.setFocusPainted(false);
        checkBox.setBorderPainted(false);
        checkBox.setContentAreaFilled(false);
        checkBox.setBorder(BorderFactory.createEmptyBorder());
        checkBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        checkBox.setPreferredSize(new Dimension(20, 20));
        checkBox.addActionListener(e -> {
            if (obj instanceof FileEntry) {
                downloadSelections.put((FileEntry) obj, checkBox.isSelected());
            } else if (obj instanceof DeleteZone.DeleteItem) {
                deleteSelections.put((DeleteZone.DeleteItem) obj, checkBox.isSelected());
            }
            refreshAction.run();
        });

        String path = obj instanceof FileEntry ? ((FileEntry) obj).getPath() : ((DeleteZone.DeleteItem) obj).getPath();
        boolean valid = isPathBaseValid(path);
        JLabel pathLabel = new JLabel(getContextMappedPath(path));
        pathLabel.setFont(subtitleFontSmall);
        if (!valid) {
            pathLabel.setForeground(theme.danger);
            pathLabel.setToolTipText("警告：本地基准路径未挂载或路径非法（" + path + "）");
            checkBox.setSelected(false);
            checkBox.setEnabled(false);
            if (isDownloadColumn) {
                downloadSelections.put((FileEntry) obj, false);
            } else {
                deleteSelections.put((DeleteZone.DeleteItem) obj, false);
            }
        } else {
            pathLabel.setForeground(theme.textPrimary);
        }

        row.add(checkBox, BorderLayout.WEST);
        row.add(pathLabel, BorderLayout.CENTER);
        return row;
    }

    private JPanel createListItemSurface() {
        JPanel row = new JPanel();
        row.setOpaque(false);
        return row;
    }

    private class CollapsibleDownloadGroupPanel extends JPanel {
        private static final int ANIMATION_DURATION_MS = 260;
        private static final int ARROW_ICON_SIZE = 12;

        private final JLabel indicatorLabel;
        private final JPanel contentPanel;
        private final JPanel contentViewport;
        private boolean expanded;
        private float animationProgress;
        private Timer animationTimer;

        CollapsibleDownloadGroupPanel(List<FileEntry> items, Runnable refreshAction) {
            setOpaque(false);
            setLayout(new BorderLayout(0, 0));

            expanded = bottomDownloadGroupExpanded;
            animationProgress = expanded ? 1.0f : 0.0f;

            JPanel header = createListItemSurface();
            header.setLayout(new BorderLayout(8, 0));
            header.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 1, 0, theme.strongDivider),
                    BorderFactory.createEmptyBorder(8, 6, 8, 6)));
            header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            indicatorLabel = new JLabel();
            indicatorLabel.setIcon(new SolidArrowIcon());
            indicatorLabel.setPreferredSize(new Dimension(ARROW_ICON_SIZE, ARROW_ICON_SIZE));
            indicatorLabel.setMinimumSize(new Dimension(ARROW_ICON_SIZE, ARROW_ICON_SIZE));
            indicatorLabel.setMaximumSize(new Dimension(ARROW_ICON_SIZE, ARROW_ICON_SIZE));
            indicatorLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel titleLabel = new JLabel("可选优化功能");
            titleLabel.setFont(subtitleFontSmall);
            titleLabel.setForeground(theme.textSecondary);
            titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel countLabel = new JLabel(items.size() + " 项");
            countLabel.setFont(subtitleFontSmall);
            countLabel.setForeground(theme.textMuted);
            countLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JPanel left = new JPanel();
            left.setOpaque(false);
            left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
            left.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            indicatorLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
            titleLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
            countLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
            left.add(indicatorLabel);
            left.add(Box.createHorizontalStrut(7));
            left.add(titleLabel);
            left.add(Box.createHorizontalStrut(8));
            left.add(countLabel);

            MouseAdapter headerClickHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        toggleExpanded();
                    }
                }
            };
            header.addMouseListener(headerClickHandler);
            left.addMouseListener(headerClickHandler);
            indicatorLabel.addMouseListener(headerClickHandler);
            titleLabel.addMouseListener(headerClickHandler);
            countLabel.addMouseListener(headerClickHandler);

            header.add(left, BorderLayout.CENTER);

            contentPanel = new JPanel();
            contentPanel.setOpaque(false);
            contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
            for (FileEntry item : items) {
                contentPanel.add(createDownloadListItemPanel(item, refreshAction));
                contentPanel.add(Box.createVerticalStrut(0));
            }

            contentViewport = new JPanel(new BorderLayout()) {
                @Override
                public Dimension getPreferredSize() {
                    Dimension full = contentPanel.getPreferredSize();
                    return new Dimension(full.width, Math.round(full.height * animationProgress));
                }

                @Override
                public Dimension getMinimumSize() {
                    return new Dimension(0, 0);
                }

                @Override
                public Dimension getMaximumSize() {
                    Dimension preferred = getPreferredSize();
                    return new Dimension(Integer.MAX_VALUE, preferred.height);
                }
            };
            contentViewport.setOpaque(false);
            contentViewport.add(contentPanel, BorderLayout.NORTH);
            contentViewport.setVisible(animationProgress > 0.0f);

            updateToggleAffordance();
            add(header, BorderLayout.NORTH);
            add(contentViewport, BorderLayout.CENTER);
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension preferred = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, preferred.height);
        }

        private void toggleExpanded() {
            expanded = !expanded;
            bottomDownloadGroupExpanded = expanded;
            updateToggleAffordance();
            animateTo(expanded ? 1.0f : 0.0f);
        }

        private void updateToggleAffordance() {
            indicatorLabel.repaint();
        }

        private void animateTo(float targetProgress) {
            if (animationTimer != null && animationTimer.isRunning()) {
                animationTimer.stop();
            }

            float startProgress = animationProgress;
            long startedAt = System.nanoTime();
            long durationNanos = ANIMATION_DURATION_MS * 1_000_000L;
            contentViewport.setVisible(true);

            animationTimer = createHighFrequencyTimer();
            animationTimer.addActionListener(e -> {
                float t = Math.min(1.0f, (System.nanoTime() - startedAt) / (float) durationNanos);
                float eased = 0.5f - 0.5f * (float) Math.cos(Math.PI * t);
                animationProgress = startProgress + (targetProgress - startProgress) * eased;
                refreshAnimatedHeight();

                if (t >= 1.0f) {
                    animationTimer.stop();
                    animationProgress = targetProgress;
                    contentViewport.setVisible(animationProgress > 0.0f);
                    refreshAnimatedHeight();
                }
            });
            animationTimer.start();
        }

        private void refreshAnimatedHeight() {
            revalidate();
            repaint();
            Container parent = getParent();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
        }

        @Override
        public void removeNotify() {
            if (animationTimer != null && animationTimer.isRunning()) {
                animationTimer.stop();
            }
            super.removeNotify();
        }

        private class SolidArrowIcon implements Icon {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                applyQualityRenderingHints(g2);
                g2.setColor(theme.textSecondary);
                int centerX = x + getIconWidth() / 2;
                int centerY = y + getIconHeight() / 2;
                int half = 4;
                int[] xs;
                int[] ys;
                if (expanded) {
                    xs = new int[]{centerX - half, centerX + half, centerX};
                    ys = new int[]{centerY - 2, centerY - 2, centerY + 5};
                } else {
                    xs = new int[]{centerX - 2, centerX - 2, centerX + 5};
                    ys = new int[]{centerY - half, centerY + half, centerY};
                }
                g2.fillPolygon(xs, ys, 3);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return ARROW_ICON_SIZE;
            }

            @Override
            public int getIconHeight() {
                return ARROW_ICON_SIZE;
            }
        }
    }

    private int countSelectableItems(List<?> items) {
        int total = 0;
        for (Object item : items) {
            String path = item instanceof FileEntry ? ((FileEntry) item).getPath() : ((DeleteZone.DeleteItem) item).getPath();
            if (isPathBaseValid(path)) {
                total++;
            }
        }
        return total;
    }

    private int countSelectedItems(List<?> items, boolean isDownloadColumn) {
        int total = 0;
        for (Object item : items) {
            String path = item instanceof FileEntry ? ((FileEntry) item).getPath() : ((DeleteZone.DeleteItem) item).getPath();
            if (!isPathBaseValid(path)) {
                continue;
            }
            boolean selected = isDownloadColumn
                    ? downloadSelections.getOrDefault(item, true)
                    : deleteSelections.getOrDefault(item, true);
            if (selected) {
                total++;
            }
        }
        return total;
    }

    private void setAllSelectionState(List<?> items, boolean isDownloadColumn, boolean selected) {
        for (Object item : items) {
            String path = item instanceof FileEntry ? ((FileEntry) item).getPath() : ((DeleteZone.DeleteItem) item).getPath();
            if (!isPathBaseValid(path)) {
                continue;
            }
            if (isDownloadColumn) {
                downloadSelections.put((FileEntry) item, selected);
            } else {
                deleteSelections.put((DeleteZone.DeleteItem) item, selected);
            }
        }
    }

    private void rebuildModsControlIndex() {
        optionalModPaths.clear();
        modEntriesByPath.clear();

        for (String optionalPath : modsControl.getOptionalMods()) {
            String normalized = normalizeModsControlPath(optionalPath);
            if (normalized != null) {
                optionalModPaths.add(normalized);
            }
        }

        for (ModsControl.ModEntry entry : modsControl.getMods()) {
            if (entry == null) {
                continue;
            }
            String normalized = normalizeModsControlPath(entry.getPath());
            if (normalized != null) {
                modEntriesByPath.put(normalized, entry);
            }
        }
    }

    private List<FileEntry> orderDownloadItems(List<FileEntry> items) {
        List<FileEntry> top = new ArrayList<>();
        List<FileEntry> middle = new ArrayList<>();
        List<FileEntry> bottom = new ArrayList<>();
        if (items == null) {
            return middle;
        }

        for (FileEntry item : items) {
            String placement = placementForDownloadEntry(item);
            if ("top".equals(placement)) {
                top.add(item);
            } else if ("bottom".equals(placement)) {
                bottom.add(item);
            } else {
                middle.add(item);
            }
        }

        Comparator<FileEntry> fileNameComparator = Comparator
                .comparing(this::downloadFileName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(this::normalizedPathForSorting, String.CASE_INSENSITIVE_ORDER);
        top.sort(fileNameComparator);
        bottom.sort(fileNameComparator);

        List<FileEntry> ordered = new ArrayList<>(top.size() + middle.size() + bottom.size());
        ordered.addAll(top);
        ordered.addAll(middle);
        ordered.addAll(bottom);
        return ordered;
    }

    private boolean isOptionalModEntry(FileEntry entry) {
        String normalized = normalizeEntryModPath(entry);
        return normalized != null && optionalModPaths.contains(normalized);
    }

    private String displayNameForDownloadEntry(FileEntry entry) {
        ModsControl.ModEntry controlEntry = metadataForDownloadEntry(entry);
        if (controlEntry != null && controlEntry.getName() != null && !controlEntry.getName().isBlank()) {
            return controlEntry.getName().trim();
        }
        return downloadFileName(entry);
    }

    private String displayRemarkForDownloadEntry(FileEntry entry) {
        ModsControl.ModEntry controlEntry = metadataForDownloadEntry(entry);
        if (controlEntry == null || controlEntry.getRemark() == null || controlEntry.getRemark().isBlank()) {
            return "";
        }
        return controlEntry.getRemark().trim();
    }

    private String placementForDownloadEntry(FileEntry entry) {
        ModsControl.ModEntry controlEntry = metadataForDownloadEntry(entry);
        if (controlEntry == null || controlEntry.getPlacement() == null) {
            return "normal";
        }
        String placement = controlEntry.getPlacement().trim().toLowerCase(Locale.ROOT);
        return "top".equals(placement) || "bottom".equals(placement) ? placement : "normal";
    }

    private ModsControl.ModEntry metadataForDownloadEntry(FileEntry entry) {
        String normalized = normalizeEntryModPath(entry);
        return normalized == null ? null : modEntriesByPath.get(normalized);
    }

    private String normalizeEntryModPath(FileEntry entry) {
        return entry == null ? null : normalizeModsControlPath(entry.getPath());
    }

    private String normalizeModsControlPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String normalized = rawPath.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String packPrefix = "Potato_Pack/";
        if (normalized.regionMatches(true, 0, packPrefix, 0, packPrefix.length())) {
            normalized = normalized.substring(packPrefix.length());
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        normalized = pathResolver.normalizeVirtualPath(normalized);
        if (!normalized.toLowerCase(Locale.ROOT).startsWith("game_core_dir/mods/")) {
            return null;
        }
        return normalized;
    }

    private String downloadFileName(FileEntry entry) {
        String path = entry == null ? null : entry.getPath();
        if (path == null || path.isBlank()) {
            return "Unknown";
        }
        String normalized = pathResolver.normalizeVirtualPath(path);
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0 && slash < normalized.length() - 1) {
            return normalized.substring(slash + 1);
        }
        return normalized.isBlank() ? "Unknown" : normalized;
    }

    private String normalizedPathForSorting(FileEntry entry) {
        String path = entry == null ? "" : entry.getPath();
        return path == null ? "" : pathResolver.normalizeVirtualPath(path);
    }

    private String toFixedWidthHtml(String text, int widthPx) {
        return "<html><body style='width:" + widthPx + "px;color:" + toHtmlColor(theme.textMuted) + "'>"
                + escapeHtml(text).replace("\n", "<br>")
                + "</body></html>";
    }

    private String toHtmlColor(Color color) {
        if (color == null) {
            return "#000000";
        }
        return String.format(Locale.ROOT, "#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '&' -> builder.append("&amp;");
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                case '"' -> builder.append("&quot;");
                case '\'' -> builder.append("&#39;");
                default -> builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String getContextMappedPath(String rawPath) {
        if (rawPath == null) {
            return "Unknown";
        }
        String normalized = pathResolver.normalizeVirtualPath(rawPath);
        if (pathResolver.isGameCorePath(normalized)) {
            String relativePath = pathResolver.stripKnownVirtualRoot(normalized);
            return composeDisplayPath(pathResolver.getGameCoreDirectory(), relativePath, PathResolver.GAME_CORE_DIR_ROOT);
        }
        if (pathResolver.isLauncherPath(normalized)) {
            String relativePath = pathResolver.stripKnownVirtualRoot(normalized);
            if (pathResolver.getMinecraftUpperDirectory() == null) {
                return "[未获取启动器目录]" + (relativePath.isEmpty() ? "" : "/" + relativePath);
            }
            return composeDisplayPath(pathResolver.getMinecraftUpperDirectory(), relativePath, PathResolver.LAUNCHER_DIR_ROOT);
        }
        return normalized;
    }

    static String composeDisplayPath(Path basePath, String relativePath, String unavailableLabel) {
        String baseLabel = displayBasePathLabel(basePath, unavailableLabel);
        if (relativePath == null || relativePath.isEmpty()) {
            return baseLabel;
        }
        return baseLabel.endsWith("/") ? baseLabel + relativePath : baseLabel + "/" + relativePath;
    }

    private static String displayBasePathLabel(Path path, String unavailableLabel) {
        if (path == null) {
            return unavailableLabel;
        }

        Path fileName = path.getFileName();
        if (fileName != null) {
            return fileName.toString();
        }

        String normalized = path.toAbsolutePath().normalize().toString().replace('\\', '/');
        return normalized.isBlank() ? unavailableLabel : normalized;
    }

    private boolean isPathBaseValid(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return false;
        }
        String normalized = pathResolver.normalizeVirtualPath(rawPath);
        if (pathResolver.isGameCorePath(normalized)) {
            return Files.exists(pathResolver.getGameCoreDirectory());
        }
        if (pathResolver.isLauncherPath(normalized)) {
            return pathResolver.getMinecraftUpperDirectory() != null && Files.exists(pathResolver.getMinecraftUpperDirectory());
        }
        return false;
    }

    private String extractResourcePackFileName(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return null;
        }
        String normalized = pathResolver.normalizeVirtualPath(rawPath);
        String prefix = "game_core_dir/resourcepacks/";
        if (!normalized.startsWith(prefix)) {
            return null;
        }

        String relative = normalized.substring(prefix.length());
        if (relative.isEmpty()) {
            return null;
        }
        int slashIndex = relative.indexOf('/');
        return slashIndex >= 0 ? relative.substring(0, slashIndex) : relative;
    }

    private JButton createStyledButton(String text, Color bg, Color fg) {
        Color hoverColor = theme.dark ? blend(bg, Color.WHITE, 0.10f) : blend(bg, Color.WHITE, 0.12f);
        RoundedButton button = new RoundedButton(text, 14, bg, hoverColor, fg, theme.buttonBorder);
        button.setVisualYOffset(LARGE_ACTION_BUTTON_VISUAL_Y_OFFSET);
        button.setFont(createUiButtonFont(13));
        button.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        return button;
    }

    private JButton createGhostButton() {
        RoundedButton button = new RoundedButton("", 12,
                theme.ghostButtonBackground,
                theme.ghostButtonHover,
                theme.ghostButtonText,
                theme.buttonBorder);
        button.setForeground(theme.ghostButtonText);
        button.setFont(createUiSectionMetaFont(12));
        button.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        return button;
    }

    private JButton createToolbarButton(String text) {
        RoundedButton button = new RoundedButton(text, 14,
                theme.toolbarButtonBackground,
                theme.toolbarButtonHover,
                theme.toolbarButtonText,
                theme.buttonBorder);
        button.setFont(createUiLabelFont(14));
        Dimension size = new Dimension(LARGE_TOOLBAR_BUTTON_SIZE, LARGE_TOOLBAR_BUTTON_SIZE);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.setBorder(BorderFactory.createEmptyBorder(0, 0, 1, 0));
        return button;
    }

    private JPanel createLogoLabel() {
        LogoSlot slot = selectLogoSlot(true);
        return resolveLogoImage(slot) == null ? null : new LogoPanel(slot, LARGE_LOGO_HEIGHT, 1.0f);
    }

    private boolean hasDisplayableLogo() {
        return logoImages.get(LogoSlot.SMALL_LIGHT) != null;
    }

    private LogoSlot selectLogoSlot(boolean largeWindow) {
        if (largeWindow) {
            return theme.dark ? LogoSlot.LARGE_DARK : LogoSlot.LARGE_LIGHT;
        }
        return theme.dark ? LogoSlot.SMALL_DARK : LogoSlot.SMALL_LIGHT;
    }

    private BufferedImage resolveLogoImage(LogoSlot slot) {
        BufferedImage baseImage = logoImages.get(LogoSlot.SMALL_LIGHT);
        if (baseImage == null) {
            return null;
        }
        BufferedImage selectedImage = logoImages.get(slot);
        return selectedImage != null ? selectedImage : baseImage;
    }

    private Dimension getLogoDrawSize(LogoSlot slot, int targetHeight) {
        BufferedImage logoImage = resolveLogoImage(slot);
        if (logoImage == null || targetHeight <= 0) {
            return new Dimension(0, 0);
        }
        int targetWidth = Math.max(1, Math.round(logoImage.getWidth() * (targetHeight / (float) logoImage.getHeight())));
        return new Dimension(targetWidth, targetHeight);
    }

    private void preloadLogoVariants(LogoSlot slot, BufferedImage source) {
        List<Integer> heights = new ArrayList<>();
        for (int baseHeight : new int[]{SMALL_LOGO_HEIGHT, LARGE_LOGO_HEIGHT}) {
            for (double scale : LOGO_SCALE_BUCKETS) {
                int deviceHeight = Math.max(baseHeight, (int) Math.ceil(baseHeight * scale));
                if (!heights.contains(deviceHeight)) {
                    heights.add(deviceHeight);
                }
            }
        }
        for (Integer height : heights) {
            logoVariantCache.put(new LogoVariantKey(slot, height),
                    createHighQualityLogoVariant(source, height, shouldUseCrispLogoScaling(slot)));
        }
    }

    private BufferedImage getBestLogoVariant(Graphics2D g2, LogoSlot slot, int targetHeight) {
        BufferedImage logoImage = resolveLogoImage(slot);
        if (logoImage == null) {
            return null;
        }
        int deviceHeight = Math.max(targetHeight, (int) Math.ceil(targetHeight * resolveDeviceScale(g2)));
        LogoVariantKey cacheKey = new LogoVariantKey(slot, deviceHeight);
        BufferedImage cached = logoVariantCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        BufferedImage created = createHighQualityLogoVariant(logoImage, deviceHeight, shouldUseCrispLogoScaling(slot));
        logoVariantCache.put(cacheKey, created);
        return created;
    }

    private boolean shouldUseCrispLogoScaling(LogoSlot slot) {
        return slot == LogoSlot.LARGE_LIGHT || slot == LogoSlot.LARGE_DARK;
    }

    private double resolveDeviceScale(Graphics2D g2) {
        double scale = 1.0d;
        AffineTransform transform = g2 != null ? g2.getTransform() : null;
        if (transform != null) {
            scale = Math.max(scale, Math.max(Math.abs(transform.getScaleX()), Math.abs(transform.getScaleY())));
        }
        if (getGraphicsConfiguration() != null) {
            AffineTransform defaultTransform = getGraphicsConfiguration().getDefaultTransform();
            scale = Math.max(scale, Math.max(Math.abs(defaultTransform.getScaleX()), Math.abs(defaultTransform.getScaleY())));
        }
        return scale;
    }

    private BufferedImage createHighQualityLogoVariant(BufferedImage source, int targetHeight, boolean crispScaling) {
        if (source == null || targetHeight <= 0) {
            return source;
        }
        int targetWidth = Math.max(1, Math.round(source.getWidth() * (targetHeight / (float) source.getHeight())));
        BufferedImage current = toPremultipliedLogoImage(source);
        int currentWidth = current.getWidth();
        int currentHeight = current.getHeight();

        while (currentWidth / 2 >= targetWidth && currentHeight / 2 >= targetHeight) {
            currentWidth = Math.max(targetWidth, currentWidth / 2);
            currentHeight = Math.max(targetHeight, currentHeight / 2);
            BufferedImage intermediate = new BufferedImage(currentWidth, currentHeight, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g2 = intermediate.createGraphics();
            applyLogoScalingRenderingHints(g2, crispScaling);
            g2.drawImage(current, 0, 0, currentWidth, currentHeight, null);
            g2.dispose();
            current = intermediate;
        }

        if (currentWidth == targetWidth && currentHeight == targetHeight) {
            return current;
        }

        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g2 = scaled.createGraphics();
        applyLogoScalingRenderingHints(g2, crispScaling);
        g2.drawImage(current, 0, 0, targetWidth, targetHeight, null);
        g2.dispose();
        return scaled;
    }

    private BufferedImage toPremultipliedLogoImage(BufferedImage source) {
        if (source == null) {
            return null;
        }
        if (source.getType() == BufferedImage.TYPE_INT_ARGB_PRE && source.isAlphaPremultiplied()) {
            return source;
        }
        BufferedImage premultiplied = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g2 = premultiplied.createGraphics();
        applyQualityRenderingHints(g2);
        g2.setComposite(AlphaComposite.Src);
        g2.drawImage(source, 0, 0, null);
        g2.dispose();
        return premultiplied;
    }

    private void drawCenteredLogo(Graphics2D g2, LogoSlot slot, int centerX, int topY, int targetHeight, float alpha) {
        BufferedImage logoImage = resolveLogoImage(slot);
        if (logoImage == null) {
            return;
        }
        Dimension size = getLogoDrawSize(slot, targetHeight);
        int x = centerX - size.width / 2;
        BufferedImage renderImage = getBestLogoVariant(g2, slot, targetHeight);
        if (renderImage == null) {
            return;
        }
        CompositeState state = CompositeState.capture(g2);
        applyLogoScalingRenderingHints(g2, shouldUseCrispLogoScaling(slot));
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.0f, Math.min(1.0f, alpha))));
        g2.drawImage(renderImage, x, topY, size.width, size.height, null);
        state.restore(g2);
    }

    private void drawCompactLogo(Graphics2D g2, int centerX, int topY, float alpha) {
        if (themeTransitionRunning
                && themeTransitionFromLogoSlot != null
                && themeTransitionToLogoSlot != null
                && themeTransitionFromLogoSlot != themeTransitionToLogoSlot) {
            float progress = Math.max(0.0f, Math.min(1.0f, themeTransitionProgress));
            drawCenteredLogo(g2, themeTransitionFromLogoSlot, centerX, topY, SMALL_LOGO_HEIGHT, alpha * (1.0f - progress));
            drawCenteredLogo(g2, themeTransitionToLogoSlot, centerX, topY, SMALL_LOGO_HEIGHT, alpha * progress);
            return;
        }
        drawCenteredLogo(g2, selectLogoSlot(false), centerX, topY, SMALL_LOGO_HEIGHT, alpha);
    }

    private void applyQualityRenderingHints(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private void applyLogoScalingRenderingHints(Graphics2D g2, boolean crispScaling) {
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                crispScaling ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static final class CompositeState {
        private final AlphaComposite composite;
        private final Map<Key, Object> hints = new HashMap<>();

        private CompositeState(Graphics2D g2) {
            this.composite = (AlphaComposite) g2.getComposite();
            captureHint(g2, RenderingHints.KEY_INTERPOLATION);
            captureHint(g2, RenderingHints.KEY_RENDERING);
            captureHint(g2, RenderingHints.KEY_ANTIALIASING);
            captureHint(g2, RenderingHints.KEY_ALPHA_INTERPOLATION);
            captureHint(g2, RenderingHints.KEY_COLOR_RENDERING);
            captureHint(g2, RenderingHints.KEY_STROKE_CONTROL);
        }

        static CompositeState capture(Graphics2D g2) {
            return new CompositeState(g2);
        }

        void restore(Graphics2D g2) {
            g2.setComposite(composite);
            for (Map.Entry<Key, Object> entry : hints.entrySet()) {
                if (entry.getValue() != null) {
                    g2.setRenderingHint(entry.getKey(), entry.getValue());
                }
            }
        }

        private void captureHint(Graphics2D g2, Key key) {
            hints.put(key, g2.getRenderingHint(key));
        }
    }

    private static final class CompactStatusLines {
        private final String primary;
        private final String secondary;

        private CompactStatusLines(String primary, String secondary) {
            this.primary = primary == null ? "" : primary;
            this.secondary = secondary == null ? "" : secondary;
        }
    }

    private Color blend(Color base, Color overlay, float ratio) {
        float clamped = Math.max(0.0f, Math.min(1.0f, ratio));
        int r = (int) (base.getRed() + (overlay.getRed() - base.getRed()) * clamped);
        int g = (int) (base.getGreen() + (overlay.getGreen() - base.getGreen()) * clamped);
        int b = (int) (base.getBlue() + (overlay.getBlue() - base.getBlue()) * clamped);
        return new Color(r, g, b);
    }

    private Color withAlpha(Color color, int alpha) {
        int clampedAlpha = Math.max(0, Math.min(255, alpha));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clampedAlpha);
    }

    private Font createUiTextFont(int style, int size) {
        String[] preferredFamilies = { "Microsoft YaHei UI", "Microsoft YaHei", "Segoe UI", "Dialog" };
        for (String family : preferredFamilies) {
            Font candidate = new Font(family, style, size);
            if (candidate != null && family.equalsIgnoreCase(candidate.getFamily())) {
                return candidate;
            }
        }
        return new Font("Dialog", style, size);
    }

    private Font createUiLabelFont(int size) {
        Font base = createUiTextFont(Font.PLAIN, size);
        Map<TextAttribute, Object> attributes = new HashMap<>(base.getAttributes());
        attributes.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_SEMIBOLD);
        return base.deriveFont(attributes);
    }

    private Font createUiButtonFont(int size) {
        Font base = createUiTextFont(Font.BOLD, size);
        Map<TextAttribute, Object> attributes = new HashMap<>(base.getAttributes());
        attributes.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_SEMIBOLD);
        return base.deriveFont(attributes);
    }

    private Font createUiSectionMetaFont(int size) {
        return createUiTextFont(Font.PLAIN, size);
    }

    private Font createUiSectionTitleFont(int size) {
        Font base = createUiTextFont(Font.BOLD, size);
        Map<TextAttribute, Object> attributes = new HashMap<>(base.getAttributes());
        attributes.put(TextAttribute.TRACKING, 0.018f);
        return base.deriveFont(attributes);
    }

    private Font createUiTitleFont(int size) {
        Font base = createUiTextFont(Font.PLAIN, size);
        Map<TextAttribute, Object> attributes = new HashMap<>(base.getAttributes());
        attributes.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_MEDIUM);
        return base.deriveFont(attributes);
    }

    private Font createUiLogTitleFont(int size) {
        Font base = createUiTextFont(Font.BOLD, size);
        Map<TextAttribute, Object> attributes = new HashMap<>(base.getAttributes());
        attributes.put(TextAttribute.TRACKING, 0.02f);
        return base.deriveFont(attributes);
    }

    private Font createUiLogSubtitleFont(int size) {
        Font base = createUiTextFont(Font.BOLD, size);
        Map<TextAttribute, Object> attributes = new HashMap<>(base.getAttributes());
        attributes.put(TextAttribute.TRACKING, 0.01f);
        return base.deriveFont(attributes);
    }

    private static void applyTextRenderingHints(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    }

    private static void applySnapshotTextRenderingHints(Graphics2D g2) {
        // LCD text AA does not composite cleanly during alpha fades and can look blurry.
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    }

    private JTextArea createBodyTextArea(String text) {
        JTextArea area = new JTextArea(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                applyTextRenderingHints(g2);
                super.paintComponent(g2);
                g2.dispose();
            }
        };
        area.setFont(createUiTextFont(Font.PLAIN, 14));
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(theme.surface);
        area.setForeground(theme.textPrimary);
        area.setBorder(null);
        area.setFocusable(false);
        area.setRequestFocusEnabled(false);
        area.setSelectionColor(area.getBackground());
        area.setSelectedTextColor(theme.textPrimary);
        DefaultCaret caret = new DefaultCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        area.setCaret(caret);
        MouseAdapter bodyDragAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                area.getCaret().setDot(0);
                mouseX = e.getXOnScreen() - getX();
                mouseY = e.getYOnScreen() - getY();
                windowDragArmed = true;
                e.consume();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                area.getCaret().setDot(0);
                if (windowDragArmed && !transitionRunning) {
                    setLocation(e.getXOnScreen() - mouseX, e.getYOnScreen() - mouseY);
                }
                e.consume();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                windowDragArmed = false;
                e.consume();
            }
        };
        area.addMouseListener(bodyDragAdapter);
        area.addMouseMotionListener(bodyDragAdapter);
        return area;
    }

    private CompactStatusLines splitCompactStatusText(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return new CompactStatusLines("", "");
        }
        Matcher matcher = STATUS_FILE_SPLIT_PATTERN.matcher(normalized);
        if (matcher.matches()) {
            String primary = matcher.group(1) == null ? "" : matcher.group(1).trim();
            String secondary = matcher.group(2) == null ? "" : matcher.group(2).trim();
            if (!secondary.isEmpty()) {
                return new CompactStatusLines(primary, secondary);
            }
        }
        return new CompactStatusLines(normalized, "");
    }

    private String ellipsizeToWidth(String text, FontMetrics metrics, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = metrics.stringWidth(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return ellipsis;
        }

        for (int end = text.length(); end > 0; end--) {
            String candidate = text.substring(0, end).trim() + ellipsis;
            if (metrics.stringWidth(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return ellipsis;
    }

    private void drawCompactStatusBlock(Graphics2D g2, int width, int height, float alpha) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2.setFont(subtitleFontSmall);
        FontMetrics metrics = g2.getFontMetrics();
        int maxTextWidth = Math.max(40, width - 32);
        int textX = 16;

        CompactStatusLines statusLines = splitCompactStatusText(currentStatusText);
        List<String> lines = new ArrayList<>();
        if (!statusLines.primary.isBlank()) {
            lines.add(ellipsizeToWidth(statusLines.primary, metrics, maxTextWidth));
        }
        if (!statusLines.secondary.isBlank()) {
            lines.add(ellipsizeToWidth(statusLines.secondary, metrics, maxTextWidth));
        }

        String warningLine = persistentWarningText == null ? "" : persistentWarningText.trim();
        if (!warningLine.isBlank()) {
            lines.add(ellipsizeToWidth(warningLine, metrics, maxTextWidth));
        }

        if (lines.isEmpty()) {
            return;
        }

        int baseline = height - 8 - (lines.size() - 1) * metrics.getHeight();
        for (int i = 0; i < lines.size(); i++) {
            boolean warning = i == lines.size() - 1 && !warningLine.isBlank();
            if (warning) {
                g2.setColor(theme.danger);
            } else if (i == 1 && !statusLines.secondary.isBlank()) {
                g2.setColor(theme.textSecondary);
            } else {
                g2.setColor(theme.textMuted);
            }
            g2.drawString(lines.get(i), textX, baseline + i * metrics.getHeight());
        }
    }

    private boolean shouldShowCompactVersionLabel() {
        if (!compactPhase || isFinishedState || !hovered) {
            return false;
        }
        if (!brandingResolved) {
            return true;
        }
        return hasDisplayableLogo() && logoFadeAnim < 0.995f;
    }

    private void drawCompactVersionLabel(Graphics2D g2, int width, int height, float alpha) {
        float labelAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
        if (labelAlpha <= 0.01f) {
            return;
        }

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, labelAlpha));
        g2.setFont(subtitleFontSmall);
        FontMetrics metrics = g2.getFontMetrics();
        int x = width - metrics.stringWidth(UPDATER_VERSION_LABEL) - 10;
        int y = height - 8;
        g2.setColor(theme.textMuted);
        g2.drawString(UPDATER_VERSION_LABEL, x, y);
    }

    public void showQuickMenuDialog(boolean manualSyncAllowed,
                                    UpdaterConfig updaterConfig,
                                    UpdaterConfigManager configManager,
                                    Consumer<QuickMenuResult> completion) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Updater menu must be shown on the EDT");
        }
        showQuickMenuDialogStep(manualSyncAllowed, updaterConfig, configManager, completion);
    }

    public void showInitialThemeSettingsDialog(UpdaterConfig updaterConfig,
                                               UpdaterConfigManager configManager,
                                               Runnable completion) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Initial theme settings must be shown on the EDT");
        }
        quickMenuDialogOpen = true;
        showThemeSettingsDialog(updaterConfig, configManager, () -> {
            finishQuickMenuDialog();
            completion.run();
        });
    }

    public void showSeedUpdateRequiredDialog(String remoteVersion, Consumer<Boolean> completion) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Seed update dialog must be shown on the EDT");
        }
        quickMenuDialogOpen = true;
        JDialog dialog = createModernDialog("Potato Seed");
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);

        JPanel content = createSeedDialogContent("Potato Seed需要更新",
                remoteVersion == null || remoteVersion.isBlank()
                        ? "需要更新启动组件，是否开始更新？"
                        : "远端版本：" + remoteVersion);
        JPanel buttons = createSeedDialogButtons();
        JButton cancelButton = createModernDialogButton("取消", false);
        JButton startButton = createModernDialogButton("开始更新", true);
        cancelButton.addActionListener(e -> closeSeedUpdateDialog(dialog, () -> completion.accept(false)));
        startButton.addActionListener(e -> closeSeedUpdateDialog(dialog, () -> completion.accept(true)));
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeSeedUpdateDialog(dialog, () -> completion.accept(false));
            }
        });
        buttons.add(cancelButton);
        buttons.add(startButton);
        content.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(startButton);
        dialog.pack();
        positionQuickMenuDialog(dialog);
        dialog.setVisible(true);
    }

    public void showSeedUpdateFailedDialog(String messageText, Consumer<Boolean> completion) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Seed update failure dialog must be shown on the EDT");
        }
        quickMenuDialogOpen = true;
        JDialog dialog = createModernDialog("Potato Seed");
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);

        JPanel content = createSeedDialogContent("Potato Seed更新失败",
                messageText == null || messageText.isBlank()
                        ? "请检查网络后重试。"
                        : messageText);
        JPanel buttons = createSeedDialogButtons();
        JButton cancelButton = createModernDialogButton("取消并继续", false);
        JButton retryButton = createModernDialogButton("重试", true);
        cancelButton.addActionListener(e -> closeSeedUpdateDialog(dialog, () -> completion.accept(false)));
        retryButton.addActionListener(e -> closeSeedUpdateDialog(dialog, () -> completion.accept(true)));
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeSeedUpdateDialog(dialog, () -> completion.accept(false));
            }
        });
        buttons.add(cancelButton);
        buttons.add(retryButton);
        content.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(retryButton);
        dialog.pack();
        positionQuickMenuDialog(dialog);
        dialog.setVisible(true);
    }

    private void showQuickMenuDialogStep(boolean manualSyncAllowed,
                                         UpdaterConfig updaterConfig,
                                         UpdaterConfigManager configManager,
                                         Consumer<QuickMenuResult> completion) {
        showUpdaterMenuOnce(manualSyncAllowed, choice -> {
            if (choice == QuickMenuChoice.THEME) {
                showThemeSettingsDialog(updaterConfig, configManager,
                        () -> showQuickMenuDialogStep(manualSyncAllowed, updaterConfig, configManager, completion));
                return;
            }
            if (choice == QuickMenuChoice.MANUAL_SYNC) {
                showManualSyncConfirmDialog(confirmed -> {
                    if (confirmed) {
                        completeQuickMenuFlow(completion, QuickMenuResult.MANUAL_SYNC);
                    } else {
                        showQuickMenuDialogStep(manualSyncAllowed, updaterConfig, configManager, completion);
                    }
                });
                return;
            }
            completeQuickMenuFlow(completion, QuickMenuResult.CONTINUE);
        });
    }

    private void completeQuickMenuFlow(Consumer<QuickMenuResult> completion, QuickMenuResult result) {
        completeThemeTransition();
        finishQuickMenuDialog();
        completion.accept(result);
    }

    private void showUpdaterMenuOnce(boolean manualSyncAllowed, Consumer<QuickMenuChoice> completion) {
        JDialog dialog = createModernDialog("Updater菜单");
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        content.setBackground(DIALOG_BACKGROUND);

        JButton themeButton = createModernDialogButton("主题设置", false);
        JButton manualButton = createModernDialogButton("手动同步", false);
        JButton doneButton = createModernDialogButton("完成", false);
        manualButton.setEnabled(manualSyncAllowed);

        Dimension buttonSize = new Dimension(160, 34);
        for (JButton button : new JButton[]{themeButton, manualButton, doneButton}) {
            button.setAlignmentX(Component.CENTER_ALIGNMENT);
            button.setMaximumSize(buttonSize);
            button.setPreferredSize(buttonSize);
        }

        themeButton.addActionListener(e -> {
            closeQuickMenuDialog(dialog, () -> completion.accept(QuickMenuChoice.THEME));
        });
        manualButton.addActionListener(e -> {
            closeQuickMenuDialog(dialog, () -> completion.accept(QuickMenuChoice.MANUAL_SYNC));
        });
        doneButton.addActionListener(e -> {
            closeQuickMenuDialog(dialog, () -> completion.accept(QuickMenuChoice.CONTINUE));
        });
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeQuickMenuDialog(dialog, () -> completion.accept(QuickMenuChoice.CONTINUE));
            }
        });

        content.add(themeButton);
        content.add(Box.createVerticalStrut(8));
        content.add(manualButton);
        content.add(Box.createVerticalStrut(8));
        content.add(doneButton);

        dialog.setContentPane(content);
        dialog.pack();
        positionQuickMenuDialog(dialog);
        dialog.setVisible(true);
    }

    private void showManualSyncConfirmDialog(Consumer<Boolean> completion) {
        JDialog dialog = createModernDialog("手动同步");
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        content.setBackground(DIALOG_BACKGROUND);
        JLabel message = new JLabel("将进行文件差异检查，是否继续？");
        message.setFont(createUiTextFont(Font.PLAIN, 13));
        message.setForeground(DIALOG_TEXT_PRIMARY);
        content.add(message, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancelButton = createModernDialogButton("取消", false);
        JButton startButton = createModernDialogButton("开始", true);
        cancelButton.addActionListener(e -> closeQuickMenuDialog(dialog, () -> completion.accept(false)));
        startButton.addActionListener(e -> {
            closeQuickMenuDialog(dialog, () -> completion.accept(true));
        });
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeQuickMenuDialog(dialog, () -> completion.accept(false));
            }
        });
        buttons.add(cancelButton);
        buttons.add(startButton);
        content.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(startButton);
        dialog.pack();
        positionQuickMenuDialog(dialog);
        dialog.setVisible(true);
    }

    private void showThemeSettingsDialog(UpdaterConfig updaterConfig,
                                         UpdaterConfigManager configManager,
                                         Runnable returnToMenu) {
        String originalMode = normalizeThemeMode(activeThemeMode);
        final String[] selectedMode = {originalMode};

        JDialog dialog = createModernDialog("主题设置");
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        content.setBackground(DIALOG_BACKGROUND);

        JRadioButton systemButton = createModernDialogRadio("跟随系统设置");
        JRadioButton brightButton = createModernDialogRadio("Bright Mode");
        JRadioButton darkButton = createModernDialogRadio("Dark Mode");
        ButtonGroup group = new ButtonGroup();
        group.add(systemButton);
        group.add(brightButton);
        group.add(darkButton);

        if ("dark".equals(originalMode)) {
            darkButton.setSelected(true);
        } else if ("light".equals(originalMode)) {
            brightButton.setSelected(true);
        } else {
            systemButton.setSelected(true);
        }

        systemButton.addActionListener(e -> previewThemeMode("system", selectedMode));
        brightButton.addActionListener(e -> previewThemeMode("light", selectedMode));
        darkButton.addActionListener(e -> previewThemeMode("dark", selectedMode));

        JPanel optionsPanel = new JPanel();
        optionsPanel.setOpaque(false);
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        Dimension optionPanelSize = new Dimension(178, 82);
        optionsPanel.setPreferredSize(optionPanelSize);
        optionsPanel.setMaximumSize(optionPanelSize);
        for (JRadioButton radioButton : new JRadioButton[]{systemButton, brightButton, darkButton}) {
            radioButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            radioButton.setMaximumSize(new Dimension(optionPanelSize.width, 24));
            radioButton.setPreferredSize(new Dimension(optionPanelSize.width, 24));
        }
        optionsPanel.add(systemButton);
        optionsPanel.add(Box.createVerticalStrut(5));
        optionsPanel.add(brightButton);
        optionsPanel.add(Box.createVerticalStrut(5));
        optionsPanel.add(darkButton);
        content.add(optionsPanel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancelButton = createModernDialogButton("取消", false);
        JButton saveButton = createModernDialogButton("保存", true);
        cancelButton.addActionListener(e -> {
            applyThemeMode(originalMode, true);
            closeQuickMenuDialog(dialog, returnToMenu);
        });
        saveButton.addActionListener(e -> {
            String previousConfigMode = updaterConfig.getThemeMode();
            boolean previousThemeConfigured = updaterConfig.isThemeConfigured();
            updaterConfig.setThemeMode(selectedMode[0]);
            try {
                updaterConfig.setThemeConfigured(true);
                configManager.save();
                closeQuickMenuDialog(dialog, returnToMenu);
            } catch (Exception saveError) {
                updaterConfig.setThemeMode(previousConfigMode);
                updaterConfig.setThemeConfigured(previousThemeConfigured);
                JOptionPane.showMessageDialog(dialog,
                        "主题设置保存失败：" + saveError.getMessage(),
                        "保存失败",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        buttons.add(cancelButton);
        buttons.add(saveButton);
        content.add(Box.createVerticalStrut(12));
        content.add(buttons);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(saveButton);
        dialog.pack();
        positionQuickMenuDialog(dialog);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                applyThemeMode(originalMode, true);
                closeQuickMenuDialog(dialog, returnToMenu);
            }
        });
        dialog.setVisible(true);
    }

    private JDialog createModernDialog(String title) {
        ensureModernDialogLookAndFeel();
        JDialog dialog = new JDialog(this, title, false);
        dialog.setResizable(false);
        dialog.setAlwaysOnTop(windowRuntime.useAlwaysOnTop());
        dialog.getRootPane().putClientProperty("JRootPane.useWindowDecorations", Boolean.TRUE);
        dialog.getRootPane().putClientProperty("JRootPane.titleBarBackground", DIALOG_BACKGROUND);
        dialog.getRootPane().putClientProperty("JRootPane.titleBarForeground", DIALOG_TEXT_PRIMARY);
        dialog.getRootPane().putClientProperty("JRootPane.titleBarShowIcon", Boolean.FALSE);
        dialog.getRootPane().putClientProperty("FlatLaf.fullWindowContent", Boolean.FALSE);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(new Color(66, 74, 88)));
        dialog.getContentPane().setBackground(DIALOG_BACKGROUND);
        return dialog;
    }

    private JPanel createSeedDialogContent(String title, String detail) {
        JPanel content = new JPanel(new BorderLayout(14, 14));
        content.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        content.setBackground(DIALOG_BACKGROUND);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(createUiLabelFont(15));
        titleLabel.setForeground(DIALOG_TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel detailLabel = new JLabel(detail);
        detailLabel.setFont(createUiTextFont(Font.PLAIN, 13));
        detailLabel.setForeground(DIALOG_TEXT_SECONDARY);
        detailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(6));
        textPanel.add(detailLabel);
        content.add(textPanel, BorderLayout.CENTER);
        return content;
    }

    private JPanel createSeedDialogButtons() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        return buttons;
    }

    private void closeSeedUpdateDialog(JDialog dialog, Runnable afterClose) {
        if (Boolean.TRUE.equals(dialog.getRootPane().getClientProperty("potato.seedDialogClosed"))) {
            return;
        }
        dialog.getRootPane().putClientProperty("potato.seedDialogClosed", Boolean.TRUE);
        rememberQuickMenuDialogLocation(dialog);
        dialog.dispose();
        quickMenuDialogOpen = false;
        repaint();
        afterClose.run();
    }

    public static void showStandaloneSeedUpdateMessage(String title, String message, boolean success) {
        if (SwingUtilities.isEventDispatchThread()) {
            showStandaloneSeedUpdateMessageOnEdt(title, message, success);
            return;
        }
        try {
            SwingUtilities.invokeAndWait(() -> showStandaloneSeedUpdateMessageOnEdt(title, message, success));
        } catch (Exception e) {
            System.err.println("[PotatoUpdater] Unable to show Seed update message: " + e.getMessage());
        }
    }

    private static void showStandaloneSeedUpdateMessageOnEdt(String title, String message, boolean success) {
        ensureModernDialogLookAndFeel();
        JDialog dialog = new JDialog((JFrame) null, title, true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        dialog.getRootPane().putClientProperty("JRootPane.useWindowDecorations", Boolean.TRUE);
        dialog.getRootPane().putClientProperty("JRootPane.titleBarBackground", DIALOG_BACKGROUND);
        dialog.getRootPane().putClientProperty("JRootPane.titleBarForeground", DIALOG_TEXT_PRIMARY);
        dialog.getRootPane().putClientProperty("JRootPane.titleBarShowIcon", Boolean.FALSE);
        dialog.getRootPane().putClientProperty("FlatLaf.fullWindowContent", Boolean.FALSE);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(new Color(66, 74, 88)));

        JPanel content = new JPanel(new BorderLayout(14, 14));
        content.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        content.setBackground(DIALOG_BACKGROUND);

        JLabel label = new JLabel(message);
        label.setForeground(success ? new Color(92, 210, 140) : new Color(255, 125, 132));
        label.setFont(createStandaloneDialogFont(Font.PLAIN, 13));
        content.add(label, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton doneButton = new DialogRoundedButton("完成", 22,
                DIALOG_BUTTON_BACKGROUND,
                DIALOG_BUTTON_HOVER,
                new Color(42, 48, 58),
                DIALOG_TEXT_PRIMARY,
                new Color(74, 83, 98),
                DIALOG_SURFACE,
                DIALOG_TEXT_SECONDARY);
        doneButton.setFont(createStandaloneDialogFont(Font.PLAIN, 13));
        doneButton.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        doneButton.addActionListener(e -> dialog.dispose());
        buttons.add(doneButton);
        content.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(doneButton);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    private static Font createStandaloneDialogFont(int style, int size) {
        String[] preferredFamilies = {"Microsoft YaHei UI", "Microsoft YaHei", "Segoe UI", "Dialog"};
        for (String family : preferredFamilies) {
            Font candidate = new Font(family, style, size);
            if (family.equalsIgnoreCase(candidate.getFamily())) {
                return candidate;
            }
        }
        return new Font("Dialog", style, size);
    }

    private static void ensureModernDialogLookAndFeel() {
        if (modernDialogLookInitialized) {
            return;
        }
        try {
            System.setProperty("flatlaf.useWindowDecorations", "true");
            System.setProperty("flatlaf.menuBarEmbedded", "true");
            FlatDarkLaf.setup();
            UIManager.put("Component.arc", 12);
            UIManager.put("Button.arc", 18);
            UIManager.put("Button.innerFocusWidth", 1);
            UIManager.put("Button.background", DIALOG_BUTTON_BACKGROUND);
            UIManager.put("Button.foreground", DIALOG_TEXT_PRIMARY);
            UIManager.put("Button.hoverBackground", DIALOG_BUTTON_HOVER);
            UIManager.put("Button.disabledBackground", DIALOG_SURFACE);
            UIManager.put("Button.disabledText", DIALOG_TEXT_SECONDARY);
            UIManager.put("Label.foreground", DIALOG_TEXT_PRIMARY);
            UIManager.put("Panel.background", DIALOG_BACKGROUND);
            UIManager.put("RadioButton.background", DIALOG_BACKGROUND);
            UIManager.put("RadioButton.foreground", DIALOG_TEXT_PRIMARY);
            UIManager.put("TitlePane.background", DIALOG_BACKGROUND);
            UIManager.put("TitlePane.foreground", DIALOG_TEXT_PRIMARY);
        } catch (RuntimeException ignored) {
            // Fall back to the current Swing look and feel if FlatLaf cannot initialize.
        }
        modernDialogLookInitialized = true;
    }

    private JButton createModernDialogButton(String text, boolean accent) {
        Color base = accent ? DIALOG_ACCENT : DIALOG_BUTTON_BACKGROUND;
        Color hover = accent ? DIALOG_ACCENT_HOVER : DIALOG_BUTTON_HOVER;
        Color pressed = accent ? DIALOG_ACCENT_PRESSED : new Color(42, 48, 58);
        Color border = accent ? DIALOG_ACCENT : new Color(74, 83, 98);
        JButton button = new DialogRoundedButton(text, 22, base, hover, pressed,
                DIALOG_TEXT_PRIMARY, border, DIALOG_SURFACE, DIALOG_TEXT_SECONDARY);
        button.setFont(createUiLabelFont(13));
        button.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        return button;
    }

    private JRadioButton createModernDialogRadio(String text) {
        JRadioButton radio = new JRadioButton(text);
        radio.setOpaque(false);
        radio.setForeground(DIALOG_TEXT_PRIMARY);
        radio.setFont(createUiTextFont(Font.PLAIN, 13));
        radio.putClientProperty("FlatLaf.style",
                "icon.selectedColor: #2D85E6; icon.focusColor: #67B3FF; "
                        + "icon.hoverColor: #3A95F1; icon.borderColor: #667085");
        return radio;
    }

    private void positionQuickMenuDialog(JDialog dialog) {
        if (quickMenuDialogLocation == null) {
            dialog.setLocationRelativeTo(this);
            quickMenuDialogLocation = dialog.getLocation();
            return;
        }
        dialog.setLocation(clampDialogLocation(dialog, quickMenuDialogLocation));
    }

    private Point clampDialogLocation(JDialog dialog, Point desiredLocation) {
        Rectangle bounds = getGraphicsConfiguration() != null
                ? getGraphicsConfiguration().getBounds()
                : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int maxX = Math.max(bounds.x, bounds.x + bounds.width - dialog.getWidth());
        int maxY = Math.max(bounds.y, bounds.y + bounds.height - dialog.getHeight());
        int x = Math.max(bounds.x, Math.min(desiredLocation.x, maxX));
        int y = Math.max(bounds.y, Math.min(desiredLocation.y, maxY));
        return new Point(x, y);
    }

    private void rememberQuickMenuDialogLocation(JDialog dialog) {
        if (dialog != null && dialog.getWidth() > 0 && dialog.getHeight() > 0) {
            quickMenuDialogLocation = dialog.getLocation();
        }
    }

    private void closeQuickMenuDialog(JDialog dialog, Runnable afterClose) {
        if (Boolean.TRUE.equals(dialog.getRootPane().getClientProperty("potato.quickDialogClosed"))) {
            return;
        }
        dialog.getRootPane().putClientProperty("potato.quickDialogClosed", Boolean.TRUE);
        rememberQuickMenuDialogLocation(dialog);
        dialog.dispose();
        afterClose.run();
    }

    private void previewThemeMode(String themeMode, String[] selectedMode) {
        String normalized = normalizeThemeMode(themeMode);
        selectedMode[0] = normalized;
        applyThemeMode(normalized, true);
    }

    public DiffEngine.DiffResult getApprovedResult() {
        if (!userConfirmed || originalResult == null) {
            return new DiffEngine.DiffResult();
        }
        DiffEngine.DiffResult result = new DiffEngine.DiffResult();
        for (FileEntry entry : originalResult.toDownloadOrUpdate) {
            if (downloadSelections.getOrDefault(entry, false) && isPathBaseValid(entry.getPath())) {
                result.toDownloadOrUpdate.add(entry);
            }
        }
        for (DeleteZone.DeleteItem item : originalResult.toDelete) {
            if (deleteSelections.getOrDefault(item, false) && isPathBaseValid(item.getPath())) {
                result.toDelete.add(item);
            }
        }
        for (ResourcePackReviewItem item : resourcePackReviewItems) {
            if (!item.selectable) {
                continue;
            }
            result.resourcePackOptionScope.add(item.fileName);
            if (resourcePackSelections.getOrDefault(item.fileName, false)) {
                result.resourcePacksToInstall.add(item.fileName);
            }
        }
        return result;
    }

    public boolean isUserConfirmed() {
        return userConfirmed;
    }

    public boolean isUserForceContinue() {
        return userForceContinue;
    }

    public boolean isDecisionMade() {
        return decisionMade;
    }

    public boolean isScanCancelled() {
        return scanCancelled;
    }

    public boolean isCompactWindowHovered() {
        return compactPhase && hovered && !isFinishedState;
    }

    public boolean isCompactForceRescanInteractionActive() {
        return isCompactQuickMenuInteractionActive();
    }

    public boolean isCompactQuickMenuInteractionActive() {
        return compactPhase
                && !isFinishedState
                && forceRescanGearEnabled
                && !quickMenuRequested
                && !quickMenuDialogOpen
                && forceRescanGearInteractionActive;
    }

    private boolean shouldShowForceRescanGear() {
        return forceRescanGearEnabled
                && compactPhase
                && hovered
                && brandingResolved
                && !isFinishedState
                && !quickMenuRequested
                && !quickMenuDialogOpen;
    }

    private Rectangle getForceRescanGearBounds() {
        int size = 22;
        int margin = 12;
        return new Rectangle(contentWidth() - size - margin, contentHeight() - size - margin, size, size);
    }

    private Rectangle getForceRescanGearHitBounds() {
        Rectangle bounds = getForceRescanGearBounds();
        bounds.grow(8, 8);
        return bounds;
    }

    private boolean isForceRescanActionAvailable() {
        return shouldShowForceRescanGear();
    }

    private boolean handleQuickMenuInteraction(MouseEvent e) {
        if (transitionRunning || !isForceRescanActionAvailable()) {
            return false;
        }
        if (!getForceRescanGearHitBounds().contains(toContentPoint(e.getPoint()))) {
            return false;
        }
        requestQuickMenu();
        return true;
    }

    public boolean consumeQuickMenuRequested() {
        boolean requested = quickMenuRequested;
        quickMenuRequested = false;
        return requested;
    }

    private void requestQuickMenu() {
        if (quickMenuRequested) {
            return;
        }
        quickMenuRequested = true;
        quickMenuDialogOpen = true;
        windowDragArmed = false;
        hovered = true;
        forceRescanGearHovered = false;
        forceRescanGearInteractionActive = false;
        forceRescanGearAnim = 0.0f;
        repaint();
    }

    private void finishQuickMenuDialog() {
        forceRescanGearEnabled = false;
        quickMenuRequested = false;
        quickMenuDialogOpen = false;
        forceRescanGearHovered = false;
        forceRescanGearInteractionActive = false;
        forceRescanGearAnim = 0.0f;
        repaint();
    }

    private boolean shouldPinCompactStatusText() {
        return compactPhase && brandingResolved && !isFinishedState && compactStatusPinned;
    }

    private void prepareCompactLoadingFadeIn() {
        compactContentAlpha = 0.0f;
        setCompactProgressTarget(-1.0f);
        compactProgressDisplayRatio = 0.0f;
        resetCompactProgressAnimation();
        forceRescanGearHovered = false;
        forceRescanGearInteractionActive = false;
        forceRescanGearAnim = 0.0f;
        compactLoadingVisualsReady = true;
        if (!compactStatusPinned) {
            hoverAnim = 0.0f;
        }
        if (brandingResolved && hasDisplayableLogo()) {
            logoFadeAnim = 0.0f;
        }
        repaint();
    }

    private void applyMinimalScrollPaneStyle(JScrollPane scrollPane) {
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setOpaque(false);
        scrollPane.getVerticalScrollBar().setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(16, 0));
        scrollPane.getVerticalScrollBar().setUI(new MinimalScrollBarUI());
        scrollPane.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 0));
        scrollPane.getHorizontalScrollBar().setUI(new MinimalScrollBarUI());
        installSmoothScrolling(scrollPane);
    }

    private void installSmoothScrolling(JScrollPane scrollPane) {
        scrollPane.setWheelScrollingEnabled(false);
        scrollPane.addMouseWheelListener(event -> {
            if (event.isConsumed()) {
                return;
            }

            JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
            if (verticalBar == null || !verticalBar.isVisible()) {
                return;
            }

            double rotation = event.getPreciseWheelRotation();
            if (Math.abs(rotation) < 0.001d) {
                return;
            }

            event.consume();

            int direction = rotation > 0 ? 1 : -1;
            int baseDelta = Math.max(SMOOTH_SCROLL_BASE_DELTA, verticalBar.getUnitIncrement(direction) * 2);
            int delta = (int) Math.round(rotation * baseDelta);
            if (delta == 0) {
                delta = direction * baseDelta;
            }

            int min = verticalBar.getMinimum();
            int max = Math.max(min, verticalBar.getMaximum() - verticalBar.getVisibleAmount());
            int target = Math.max(min, Math.min(max, verticalBar.getValue() + delta));
            animateScrollBarTo(verticalBar, target);
        });
    }

    private void animateScrollBarTo(JScrollBar scrollBar, int targetValue) {
        Timer previous = (Timer) scrollBar.getClientProperty(SCROLL_ANIMATION_TIMER_KEY);
        if (previous != null && previous.isRunning()) {
            previous.stop();
        }

        int startValue = scrollBar.getValue();
        if (startValue == targetValue) {
            return;
        }

        Timer timer = createHighFrequencyTimer();
        long startTime = System.nanoTime();
        long durationNanos = SMOOTH_SCROLL_DURATION_MS * 1_000_000L;
        timer.addActionListener(e -> {
            float t = Math.min(1.0f, (System.nanoTime() - startTime) / (float) durationNanos);
            float eased = 1.0f - (float) Math.pow(1.0f - t, 3.0);
            int value = startValue + Math.round((targetValue - startValue) * eased);
            scrollBar.setValue(value);
            if (t >= 1.0f) {
                timer.stop();
                scrollBar.setValue(targetValue);
                scrollBar.putClientProperty(SCROLL_ANIMATION_TIMER_KEY, null);
            }
        });
        scrollBar.putClientProperty(SCROLL_ANIMATION_TIMER_KEY, timer);
        timer.start();
    }

    public void fadeOutAndDispose() {
        suppressCloseRequestHandler = true;
        if (!windowRuntime.useWindowOpacityEffects()) {
            disposeSilently();
            return;
        }
        animateWindowOpacity(getWindowOpacitySafe(), 0.0f, 240, this::dispose);
    }

    public void disposeSilently() {
        suppressCloseRequestHandler = true;
        if (progressTimer != null && progressTimer.isRunning()) {
            progressTimer.stop();
        }
        if (pageFadeTimer != null && pageFadeTimer.isRunning()) {
            pageFadeTimer.stop();
        }
        if (queuedPageTransitionTimer != null && queuedPageTransitionTimer.isRunning()) {
            queuedPageTransitionTimer.stop();
        }
        pageFadeSequence++;
        Thread worker = pageFadeWorker;
        if (worker != null) {
            worker.interrupt();
            pageFadeWorker = null;
        }
        if (windowOpacityTimer != null && windowOpacityTimer.isRunning()) {
            windowOpacityTimer.stop();
        }
        dispose();
    }

    private void animateWindowOpacity(float startOpacity, float endOpacity, int durationMs, Runnable onComplete) {
        final float clampedStart = Math.max(0.0f, Math.min(1.0f, startOpacity));
        final float clampedEnd = Math.max(0.0f, Math.min(1.0f, endOpacity));
        if (!windowRuntime.useWindowOpacityEffects()) {
            setWindowOpacitySafe(clampedEnd);
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        if (windowOpacityTimer != null && windowOpacityTimer.isRunning()) {
            windowOpacityTimer.stop();
        }
        if (durationMs <= 0 || Math.abs(clampedEnd - clampedStart) < 0.001f) {
            setWindowOpacitySafe(clampedEnd);
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        long startTime = System.nanoTime();
        long durationNanos = durationMs * 1_000_000L;
        windowOpacityTimer = new Timer(16, null);
        windowOpacityTimer.addActionListener(e -> {
            float t = Math.min(1.0f, (System.nanoTime() - startTime) / (float) durationNanos);
            float eased = 0.5f - 0.5f * (float) Math.cos(Math.PI * t);
            float opacity = clampedStart + (clampedEnd - clampedStart) * eased;
            setWindowOpacitySafe(opacity);
            if (t >= 1.0f) {
                windowOpacityTimer.stop();
                setWindowOpacitySafe(clampedEnd);
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        windowOpacityTimer.start();
    }

    private void dispatchCloseRequest() {
        if (suppressCloseRequestHandler || closeRequestDispatched) {
            return;
        }
        closeRequestDispatched = true;
        Runnable handler = closeRequestHandler;
        if (handler == null) {
            disposeSilently();
            return;
        }
        Thread closeThread = new Thread(handler, "potato-updater-close");
        closeThread.setDaemon(true);
        closeThread.start();
    }

    private String normalizeThemeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "system";
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if ("bright".equals(normalized)) {
            return "light";
        }
        if ("dark_blurred".equals(normalized)
                || "dark-blurred".equals(normalized)
                || "darkblurred".equals(normalized)
                || "blurred".equals(normalized)) {
            return "dark";
        }
        if ("system".equals(normalized)
                || "light".equals(normalized)
                || "dark".equals(normalized)) {
            return normalized;
        }
        return "system";
    }

    private UpdaterTheme resolveTheme(String themeMode) {
        return UpdaterTheme.fromDark(windowRuntime.resolveDarkTheme(normalizeThemeMode(themeMode)));
    }

    private void setThemeImmediate(UpdaterTheme nextTheme) {
        theme = nextTheme;
        bgColor = nextTheme.windowBackground;
        accentColor = nextTheme.accent;
    }

    private void applyThemeMode(String themeMode, boolean animated) {
        String normalized = normalizeThemeMode(themeMode);
        UpdaterTheme targetTheme = resolveTheme(normalized);
        activeThemeMode = normalized;
        if (themeTransitionTimer != null) {
            themeTransitionTimer.stop();
            themeTransitionTimer = null;
        }
        if (!animated || theme == null || theme.dark == targetTheme.dark) {
            themeTransitionRunning = false;
            themeTransitionProgress = 1.0f;
            themeTransitionTargetTheme = null;
            themeTransitionFromLogoSlot = null;
            themeTransitionToLogoSlot = null;
            setThemeImmediate(targetTheme);
            configureThemeDefaults();
            configureWindowChrome();
            repaint();
            return;
        }

        UpdaterTheme startTheme = theme;
        LogoSlot fromLogoSlot = startTheme.dark ? LogoSlot.SMALL_DARK : LogoSlot.SMALL_LIGHT;
        LogoSlot toLogoSlot = targetTheme.dark ? LogoSlot.SMALL_DARK : LogoSlot.SMALL_LIGHT;
        themeTransitionRunning = true;
        themeTransitionProgress = 0.0f;
        themeTransitionTargetTheme = targetTheme;
        themeTransitionFromLogoSlot = fromLogoSlot;
        themeTransitionToLogoSlot = toLogoSlot;
        long startNanos = System.nanoTime();
        themeTransitionTimer = createHighFrequencyTimer();
        themeTransitionTimer.addActionListener(e -> {
            float raw = (System.nanoTime() - startNanos) / (THEME_TRANSITION_DURATION_MS * 1_000_000.0f);
            float progress = Math.max(0.0f, Math.min(1.0f, raw));
            float eased = 0.5f - 0.5f * (float) Math.cos(Math.PI * progress);
            themeTransitionProgress = eased;
            setThemeImmediate(UpdaterTheme.mix(startTheme, targetTheme, eased));
            configureThemeDefaults();
            configureWindowChrome();
            repaint();
            if (progress >= 1.0f) {
                themeTransitionTimer.stop();
                themeTransitionTimer = null;
                themeTransitionRunning = false;
                themeTransitionProgress = 1.0f;
                themeTransitionTargetTheme = null;
                themeTransitionFromLogoSlot = null;
                themeTransitionToLogoSlot = null;
                setThemeImmediate(targetTheme);
                configureThemeDefaults();
                configureWindowChrome();
                repaint();
            }
        });
        themeTransitionTimer.start();
    }

    private void completeThemeTransition() {
        if (themeTransitionTimer != null) {
            themeTransitionTimer.stop();
            themeTransitionTimer = null;
        }
        UpdaterTheme finalTheme = themeTransitionTargetTheme;
        themeTransitionRunning = false;
        themeTransitionProgress = 1.0f;
        themeTransitionTargetTheme = null;
        themeTransitionFromLogoSlot = null;
        themeTransitionToLogoSlot = null;
        if (finalTheme != null) {
            setThemeImmediate(finalTheme);
        }
        configureThemeDefaults();
        configureWindowChrome();
        repaint();
    }

    private void configureWindowChrome() {
        if (!isDisplayable()) {
            setUndecorated(windowRuntime.useUndecoratedWindow());
        }
        setBackground(windowRuntime.useTransparentWindowBackground() ? new Color(0, 0, 0, 0) : theme.windowBackground);
    }

    private void configureThemeDefaults() {
        UIManager.put("ToolTip.background", theme.surface);
        UIManager.put("ToolTip.foreground", theme.textPrimary);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(theme.surfaceBorder));
        UIManager.put("Label.foreground", theme.textPrimary);
        UIManager.put("Panel.background", theme.windowBackground);
    }

    private int currentWindowShadowInset() {
        return windowRuntime.useCustomWindowShadow() ? WINDOW_SHADOW_SIZE : 0;
    }

    private int windowWidthForContent(int contentWidth) {
        return contentWidth + currentWindowShadowInset() * 2;
    }

    private int windowHeightForContent(int contentHeight) {
        return contentHeight + currentWindowShadowInset() * 2;
    }

    private int contentWidth() {
        return Math.max(1, getWidth() - currentWindowShadowInset() * 2);
    }

    private int contentHeight() {
        return Math.max(1, getHeight() - currentWindowShadowInset() * 2);
    }

    private Point toContentPoint(Point point) {
        int inset = currentWindowShadowInset();
        return new Point(point.x - inset, point.y - inset);
    }

    private void centerWindowWithCurrentSize() {
        SwingUtilities.invokeLater(() -> {
            setLocationRelativeTo(null);
            repaint();
        });
    }

    private void resizeWindowForCurrentRuntime(int targetWidth, int targetHeight) {
        int windowTargetWidth = windowWidthForContent(targetWidth);
        int windowTargetHeight = windowHeightForContent(targetHeight);
        if (windowRuntime.useStableLinuxWindowMode()) {
            setSize(windowTargetWidth, windowTargetHeight);
            centerWindowWithCurrentSize();
            return;
        }
        int centerX = getX() + getWidth() / 2;
        int centerY = getY() + getHeight() / 2;
        setBounds(centerX - windowTargetWidth / 2, centerY - windowTargetHeight / 2, windowTargetWidth, windowTargetHeight);
    }

    private float getWindowOpacitySafe() {
        try {
            return getOpacity();
        } catch (RuntimeException ignored) {
            return 1.0f;
        }
    }

    private void setWindowOpacitySafe(float opacity) {
        if (!windowRuntime.useWindowOpacityEffects()) {
            return;
        }
        try {
            setOpacity(opacity);
        } catch (RuntimeException ignored) {
        }
    }

    private Timer createHighFrequencyTimer() {
        Timer timer = new Timer(windowRuntime.animationTimerDelayMs(), null);
        timer.setCoalesce(false);
        timer.setRepeats(true);
        timer.setInitialDelay(0);
        return timer;
    }

    private Timer createPageTransitionTimer() {
        Timer timer = new Timer(PAGE_TRANSITION_TIMER_DELAY_MS, null);
        timer.setCoalesce(true);
        timer.setRepeats(true);
        timer.setInitialDelay(0);
        return timer;
    }

    private void animateFloat(float start, float end, int durationMs, Consumer<Float> onUpdate, Runnable onComplete) {
        Timer timer = createHighFrequencyTimer();
        long startTime = System.nanoTime();
        long durationNanos = Math.max(1L, durationMs) * 1_000_000L;
        onUpdate.accept(start);
        timer.addActionListener(e -> {
            float t = Math.min(1.0f, (System.nanoTime() - startTime) / (float) durationNanos);
            float eased = 0.5f - 0.5f * (float) Math.cos(Math.PI * t);
            float value = start + (end - start) * eased;
            onUpdate.accept(value);
            if (t >= 1.0f) {
                timer.stop();
                onUpdate.accept(end);
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        timer.start();
    }

    private void animateCompactContentAlpha(float start, float end, int durationMs, Runnable onComplete) {
        animateFloat(start, end, durationMs, value -> {
            compactContentAlpha = value;
            repaint();
        }, onComplete);
    }

    private void animateReviewContentAlpha(float start, float end, int durationMs, Runnable onComplete) {
        if (reviewContentPanel == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        animateFloat(start, end, durationMs, reviewContentPanel::setAlpha, onComplete);
    }

    private float compactAnimationDeltaSeconds(long nowNanos) {
        long previous = compactAnimationLastFrameNanos;
        compactAnimationLastFrameNanos = nowNanos;
        if (previous <= 0L || nowNanos <= previous) {
            return COMPACT_ANIMATION_BASE_FRAME_SECONDS;
        }
        float deltaSeconds = (nowNanos - previous) / 1_000_000_000.0f;
        return Math.max(0.001f, Math.min(COMPACT_ANIMATION_MAX_FRAME_SECONDS, deltaSeconds));
    }

    private void resetCompactProgressAnimation() {
        long now = System.nanoTime();
        compactAnimationLastFrameNanos = now;
        resetLoadingProgressAnimation(now);
    }

    private void resetLoadingProgressAnimation(long nowNanos) {
        loadingProgressStartNanos = nowNanos;
        progressOffset = -COMPACT_PROGRESS_SEGMENT_WIDTH;
    }

    private void updateLoadingProgressOffset(long nowNanos) {
        long elapsedNanos = Math.max(0L, nowNanos - loadingProgressStartNanos);
        float elapsedSeconds = elapsedNanos / 1_000_000_000.0f;
        float travelDistance = COMPACT_PROGRESS_BAR_WIDTH + COMPACT_PROGRESS_SEGMENT_WIDTH;
        progressOffset = -COMPACT_PROGRESS_SEGMENT_WIDTH
                + ((elapsedSeconds * COMPACT_LOADING_PROGRESS_SPEED_PX_PER_SECOND) % travelDistance);
    }

    private void setCompactProgressTarget(float targetRatio) {
        float nextTarget = targetRatio < 0.0f ? -1.0f : Math.max(0.0f, Math.min(1.0f, targetRatio));
        boolean wasDeterminate = compactProgressTargetRatio >= 0.0f;
        boolean nextDeterminate = nextTarget >= 0.0f;
        compactProgressTargetRatio = nextTarget;
        if (wasDeterminate && !nextDeterminate) {
            resetCompactProgressAnimation();
        }
    }

    private float smoothToward(float current, float target, float factor, float deltaSeconds) {
        float clampedFactor = Math.max(0.0f, Math.min(0.99f, factor));
        float frameScale = Math.max(0.0f, deltaSeconds / COMPACT_ANIMATION_BASE_FRAME_SECONDS);
        float adjustedFactor = 1.0f - (float) Math.pow(1.0f - clampedFactor, frameScale);
        float next = current + (target - current) * adjustedFactor;
        if (Math.abs(next - target) < 0.01f) {
            return target;
        }
        return Math.max(0.0f, Math.min(1.0f, next));
    }

    private void showCardAnimated(String pageName) {
        showCardAnimated(pageName, null);
    }

    private void showCardAnimated(String pageName, Runnable onComplete) {
        if (!(cardsPanel instanceof AnimatedCardsPanel animatedCardsPanel)) {
            cardLayout.show(cardsPanel, pageName);
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        animatedCardsPanel.requestFade(pageName, cardLayout, onComplete);
    }

    private Container findPage(String name) {
        for (Component component : cardsPanel.getComponents()) {
            if (component instanceof Container && name.equals(component.getName())) {
                return (Container) component;
            }
        }
        return null;
    }

    private JButton findButton(String text) {
        for (Component root : getContentPane().getComponents()) {
            JButton found = findButtonRecursive(root, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private JButton findButtonRecursive(Component component, String text) {
        if (component instanceof JButton button && text.equals(button.getText())) {
            return button;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JButton found = findButtonRecursive(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String abbreviatePath(String path) {
        if (path == null) {
            return "";
        }
        if (path.length() <= 54) {
            return path;
        }
        return path.substring(0, 18) + "..." + path.substring(path.length() - 30);
    }

    private String safeMainTitle(UpdateInfo info) {
        String value = info.getMainTitle();
        return value == null || value.isBlank() || value.contains("绯荤粺") ? "Potato 系统更新" : value;
    }

    private String safeContentTitle(UpdateInfo info) {
        String value = info.getContentTitle();
        return value == null || value.isBlank() || value.contains("鍙戠幇") ? "发现新的增量内容" : value;
    }

    private String safeBody(UpdateInfo info) {
        String value = info.getBody();
        return value == null || value.isBlank() || value.contains("鏆傛棤") ? "请确认本轮需要更新和清理的内容。" : value;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private class StagePanel extends JPanel {
        private boolean stageExpanded = false;

        StagePanel() {
            setOpaque(false);
            setDoubleBuffered(true);
            int inset = currentWindowShadowInset();
            setBorder(BorderFactory.createEmptyBorder(inset, inset, inset, inset));
        }

        void setExpanded(boolean expanded) {
            this.stageExpanded = expanded;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            applyQualityRenderingHints(g2);
            applyTextRenderingHints(g2);
            int width = getWidth();
            int height = getHeight();
            int inset = currentWindowShadowInset();
            int cornerArc = stageExpanded ? LARGE_WINDOW_CORNER_ARC : SMALL_WINDOW_CORNER_ARC;
            float shadowStrokeScale = stageExpanded ? LARGE_WINDOW_SHADOW_STROKE_SCALE : SMALL_WINDOW_SHADOW_STROKE_SCALE;
            float shadowAlphaRange = stageExpanded ? LARGE_WINDOW_SHADOW_ALPHA_RANGE : SMALL_WINDOW_SHADOW_ALPHA_RANGE;

            if (windowRuntime.useStableLinuxWindowMode()) {
                g2.setColor(bgColor);
                g2.fillRect(0, 0, width, height);
                g2.setColor(theme.windowBorder);
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawRect(0, 0, width - 1, height - 1);
            } else {
                if (inset > 0) {
                    drawWindowShadow(g2, inset, width, height, cornerArc, shadowStrokeScale, shadowAlphaRange);
                }
                RoundRectangle2D.Float backgroundShape = new RoundRectangle2D.Float(
                        inset + 0.5f,
                        inset + 0.5f,
                        width - inset * 2.0f - 1.0f,
                        height - inset * 2.0f - 1.0f,
                        cornerArc,
                        cornerArc);
                g2.setColor(bgColor);
                g2.fill(backgroundShape);
                g2.setColor(theme.windowBorder);
                g2.setStroke(new BasicStroke(1.0f));
                g2.draw(backgroundShape);
            }

            if (!stageExpanded) {
                if (inset > 0) {
                    g2.translate(inset, inset);
                    width -= inset * 2;
                    height -= inset * 2;
                }
                CompositeState contentState = CompositeState.capture(g2);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, compactContentAlpha));
                int centerTitleY = height / 2 - 4;
                int titleY = centerTitleY;
                if (isFinishedState && isSuccess) {
                    titleY = height / 2 + 10;
                }

                if (isFinishedState) {
                    if (isSuccess) {
                        g2.setColor(theme.success);
                        g2.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        int cx = width / 2;
                        int cy = titleY - 15;
                        g2.drawLine(cx - 10, cy, cx - 3, cy + 8);
                        g2.drawLine(cx - 3, cy + 8, cx + 12, cy - 10);
                    } else {
                        int iconCenterY = 30;
                        g2.setColor(theme.danger);
                        g2.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        int cx = width / 2;
                        int cy = iconCenterY;
                        g2.drawLine(cx - 10, cy - 10, cx + 10, cy + 10);
                        g2.drawLine(cx + 10, cy - 10, cx - 10, cy + 10);
                    }
                } else if (!compactLoadingVisualsReady) {
                    contentState.restore(g2);
                    g2.dispose();
                    return;
                } else if (hasDisplayableLogo()) {
                    drawCompactLogo(g2, width / 2, titleY - 30, logoFadeAnim);
                } else if (brandingResolved) {
                    g2.setFont(titleFontLarge);
                    g2.setColor(theme.textPrimary);
                    FontMetrics metrics = g2.getFontMetrics();
                    String text = "Potato Updater";
                    g2.drawString(text, (width - metrics.stringWidth(text)) / 2, titleY);
                }

                if (!isFinishedState) {
                    int barWidth = COMPACT_PROGRESS_BAR_WIDTH;
                    int barHeight = COMPACT_PROGRESS_BAR_HEIGHT;
                    int barX = (width - barWidth) / 2;
                    int barY = titleY + 14;
                    float radius = barHeight / 2.0f;
                    float lineY = barY + radius;
                    float lineStart = barX + radius;
                    float lineEnd = barX + barWidth - radius;
                    Stroke oldStroke = g2.getStroke();
                    BasicStroke progressStroke = new BasicStroke(
                            barHeight,
                            BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND);
                    Line2D.Float progressLine = new Line2D.Float(lineStart, lineY, lineEnd, lineY);
                    Shape barShape = progressStroke.createStrokedShape(progressLine);
                    g2.setStroke(progressStroke);
                    g2.setColor(theme.progressTrack);
                    g2.fill(barShape);
                    if (compactProgressTargetRatio >= 0.0f) {
                        float displayRatio = Math.max(0.0f, Math.min(1.0f, compactProgressDisplayRatio));
                        if (displayRatio > 0.001f) {
                            g2.setColor(accentColor);
                            float filledEnd = displayRatio >= 0.999f
                                    ? lineEnd
                                    : lineStart + (lineEnd - lineStart) * displayRatio;
                            g2.draw(new Line2D.Float(lineStart, lineY, filledEnd, lineY));
                        }
                    } else {
                        Shape oldClip = g2.getClip();
                        g2.setClip(barShape);
                        g2.setColor(accentColor);
                        float segmentStart = barX + progressOffset + radius;
                        float segmentEnd = barX + progressOffset + COMPACT_PROGRESS_SEGMENT_WIDTH - radius;
                        g2.draw(new Line2D.Float(segmentStart, lineY, segmentEnd, lineY));
                        g2.setClip(oldClip);
                    }
                    g2.setStroke(oldStroke);

                    float statusAlpha = shouldPinCompactStatusText() ? 1.0f : hoverAnim;
                    if (statusAlpha > 0.01f) {
                        drawCompactStatusBlock(g2, width, height, statusAlpha);
                    }
                    if (shouldShowCompactVersionLabel()) {
                        drawCompactVersionLabel(g2, width, height, hoverAnim);
                    }
                    if (shouldShowForceRescanGear()) {
                        drawForceRescanGear(g2);
                    }
                } else if (isSuccess) {
                    if (hoverAnim > 0.01f) {
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, hoverAnim));
                        g2.setFont(subtitleFontSmall);
                        g2.setColor(theme.successText);
                        FontMetrics metrics = g2.getFontMetrics();
                        g2.drawString("更新成功", (width - metrics.stringWidth("更新成功")) / 2, height - 20);
                    }
                } else {
                    g2.setFont(subtitleFontSmall);
                    g2.setColor(theme.danger);
                    FontMetrics metrics = g2.getFontMetrics();
                    int messageY = 72;
                    int buttonY = height - 36;
                    g2.drawString(finishMessage, (width - metrics.stringWidth(finishMessage)) / 2, messageY);
                    drawErrorButton(g2, width / 2 - 80, height - 40, 70, 28, "继续启动", accentColor, errorLeftAnim);
                    drawErrorButton(g2, width / 2 + 10, height - 40, 70, 28, "退出", theme.secondaryButtonBackground, errorRightAnim);
                }
                contentState.restore(g2);
            }
            g2.dispose();
        }

        private void drawWindowShadow(Graphics2D g2, int inset, int width, int height,
                                      int cornerArc, float strokeScale, float alphaRange) {
            float contentX = inset + 0.5f;
            float contentY = inset + 0.5f;
            float contentW = width - inset * 2.0f - 1.0f;
            float contentH = height - inset * 2.0f - 1.0f;
            if (contentW <= 0.0f || contentH <= 0.0f) {
                return;
            }

            CompositeState shadowState = CompositeState.capture(g2);
            for (int i = inset; i >= 1; i--) {
                float progress = 1.0f - (i - 1.0f) / inset;
                int alpha = Math.round(1.0f + alphaRange * progress * progress);
                g2.setColor(new Color(0, 0, 0, alpha));
                g2.setStroke(new BasicStroke(i * strokeScale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new RoundRectangle2D.Float(
                        contentX,
                        contentY + 0.75f,
                        contentW,
                        contentH,
                        cornerArc,
                        cornerArc));
            }
            shadowState.restore(g2);
        }

        private void drawErrorButton(Graphics2D g2, int x, int y, int w, int h, String text, Color baseColor, float anim) {
            int drawY = y + 4;
            Color currentColor = mixColor(baseColor, baseColor.darker(), anim);
            g2.setColor(currentColor);
            g2.fillRoundRect(x, drawY, w, h, 10, 10);
            g2.setColor(readableTextColor(currentColor));
            applyTextRenderingHints(g2);
            g2.setFont(createUiLabelFont(12));
            FontMetrics metrics = g2.getFontMetrics();
            int strW = metrics.stringWidth(text);
            g2.drawString(text, x + (w - strW) / 2, drawY + h / 2 + 4);
        }

        private Color readableTextColor(Color background) {
            double luminance = 0.2126 * background.getRed()
                    + 0.7152 * background.getGreen()
                    + 0.0722 * background.getBlue();
            return luminance > 150.0d ? theme.secondaryButtonText : Color.WHITE;
        }

        private Color mixColor(Color c1, Color c2, float ratio) {
            int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * ratio);
            int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * ratio);
            int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * ratio);
            return new Color(r, g, b);
        }

        private void drawForceRescanGear(Graphics2D g2) {
            Rectangle bounds = getForceRescanGearBounds();
            Graphics2D gearGraphics = (Graphics2D) g2.create();
            gearGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color bg = mixColor(theme.gearBackground, theme.gearHoverBackground, forceRescanGearAnim);
            Color border = mixColor(theme.gearBorder, accentColor, forceRescanGearAnim);
            Color icon = mixColor(theme.gearIcon, theme.dark ? accentColor : accentColor.darker(), forceRescanGearAnim);

            gearGraphics.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 235));
            gearGraphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 10, 10);
            gearGraphics.setColor(border);
            gearGraphics.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 10, 10);

            int cx = bounds.x + bounds.width / 2;
            int cy = bounds.y + bounds.height / 2;
            int outerRadius = 6;
            int innerRadius = 3;

            gearGraphics.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gearGraphics.setColor(icon);
            for (int i = 0; i < 8; i++) {
                double angle = Math.toRadians(i * 45.0);
                int x1 = cx + (int) Math.round(Math.cos(angle) * (innerRadius + 1));
                int y1 = cy + (int) Math.round(Math.sin(angle) * (innerRadius + 1));
                int x2 = cx + (int) Math.round(Math.cos(angle) * outerRadius);
                int y2 = cy + (int) Math.round(Math.sin(angle) * outerRadius);
                gearGraphics.drawLine(x1, y1, x2, y2);
            }
            gearGraphics.drawOval(cx - 4, cy - 4, 8, 8);
            gearGraphics.fillOval(cx - 1, cy - 1, 2, 2);
            gearGraphics.dispose();
        }
    }

    private static class RoundedPanel extends JPanel {
        private final int arc;
        private final Color color;

        RoundedPanel(int arc, Color color) {
            this.arc = arc;
            this.color = color;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setColor(color);
            g2.fill(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1.0f, getHeight() - 1.0f, arc, arc));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class OutlinedRoundedPanel extends RoundedPanel {
        private final int arc;
        private final Color outlineColor;

        OutlinedRoundedPanel(int arc, Color fillColor, Color outlineColor) {
            super(arc, fillColor);
            this.arc = arc;
            this.outlineColor = outlineColor;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setColor(outlineColor);
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1.0f, getHeight() - 1.0f, arc, arc));
            g2.dispose();
        }
    }

    private static class RoundedButton extends JButton {
        private final int arc;
        private final Color baseColor;
        private final Color hoverColor;
        private final Color baseTextColor;
        private final Color borderColor;
        private float visualYOffset = 0.0f;

        private RoundedButton(String text, int arc, Color baseColor, Color hoverColor, Color textColor, Color borderColor) {
            super(text);
            this.arc = arc;
            this.baseColor = baseColor;
            this.hoverColor = hoverColor;
            this.baseTextColor = textColor;
            this.borderColor = borderColor;
            setOpaque(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setForeground(textColor);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setRolloverEnabled(true);
        }

        private void setVisualYOffset(float visualYOffset) {
            this.visualYOffset = visualYOffset;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            if (visualYOffset != 0.0f) {
                g2.translate(0.0d, visualYOffset);
            }
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            applyTextRenderingHints(g2);
            Color fill = getModel().isRollover() ? hoverColor : baseColor;
            if (!isEnabled()) {
                fill = new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 130);
            } else if (getModel().isPressed()) {
                fill = fill.darker();
            }
            RoundRectangle2D.Float buttonShape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1.0f, getHeight() - 1.0f, arc, arc);
            g2.setColor(fill);
            g2.fill(buttonShape);
            g2.setColor(borderColor);
            g2.draw(buttonShape);
            setForeground(isEnabled() ? baseTextColor : new Color(baseTextColor.getRed(), baseTextColor.getGreen(), baseTextColor.getBlue(), 140));
            super.paintComponent(g2);
            g2.dispose();
        }
    }

    private static class DialogRoundedButton extends JButton {
        private final int arc;
        private final Color baseColor;
        private final Color hoverColor;
        private final Color pressedColor;
        private final Color baseTextColor;
        private final Color borderColor;
        private final Color disabledColor;
        private final Color disabledTextColor;

        private DialogRoundedButton(String text,
                                    int arc,
                                    Color baseColor,
                                    Color hoverColor,
                                    Color pressedColor,
                                    Color textColor,
                                    Color borderColor,
                                    Color disabledColor,
                                    Color disabledTextColor) {
            super(text);
            this.arc = arc;
            this.baseColor = baseColor;
            this.hoverColor = hoverColor;
            this.pressedColor = pressedColor;
            this.baseTextColor = textColor;
            this.borderColor = borderColor;
            this.disabledColor = disabledColor;
            this.disabledTextColor = disabledTextColor;
            setOpaque(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setForeground(textColor);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setRolloverEnabled(true);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setCursor(enabled
                    ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    : Cursor.getDefaultCursor());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            applyTextRenderingHints(g2);
            Color fill = baseColor;
            Color text = baseTextColor;
            Color stroke = borderColor;
            if (!isEnabled()) {
                fill = disabledColor;
                text = disabledTextColor;
                stroke = new Color(disabledColor.getRed(), disabledColor.getGreen(), disabledColor.getBlue(), 180);
            } else if (getModel().isPressed()) {
                fill = pressedColor;
            } else if (getModel().isRollover()) {
                fill = hoverColor;
            }

            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(
                    0.5f,
                    0.5f,
                    getWidth() - 1.0f,
                    getHeight() - 1.0f,
                    arc,
                    arc);
            g2.setColor(fill);
            g2.fill(shape);
            g2.setColor(stroke);
            g2.draw(shape);
            setForeground(text);
            g2.setColor(text);
            g2.setFont(getFont());
            FontMetrics metrics = g2.getFontMetrics();
            String label = getText() == null ? "" : getText();
            int textX = (getWidth() - metrics.stringWidth(label)) / 2;
            int textY = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g2.drawString(label, textX, textY);
            g2.dispose();
        }
    }

    private class LogoPanel extends JPanel {
        private final LogoSlot slot;
        private final int targetHeight;
        private final float alpha;

        private LogoPanel(LogoSlot slot, int targetHeight, float alpha) {
            this.slot = slot;
            this.targetHeight = targetHeight;
            this.alpha = alpha;
            setOpaque(false);
            setDoubleBuffered(true);
            Dimension size = getLogoDrawSize(slot, targetHeight);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (resolveLogoImage(slot) == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            drawCenteredLogo(g2, slot, getWidth() / 2, 0, targetHeight, alpha);
            g2.dispose();
        }
    }

    private static class FadablePanel extends JPanel {
        private float alpha = 1.0f;

        private FadablePanel(BorderLayout layout) {
            super(layout);
            setOpaque(false);
            setDoubleBuffered(true);
        }

        void setAlpha(float alpha) {
            this.alpha = Math.max(0.0f, Math.min(1.0f, alpha));
            repaint();
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            super.paint(g2);
            g2.dispose();
        }
    }

    private class AnimatedCardsPanel extends JPanel {
        private float overlayAlpha = 0.0f;
        private String currentPageName = PAGE_INFO;
        private String queuedPageName;
        private Runnable queuedPageCompletion;
        private long currentPageVisibleSinceNanos = System.nanoTime();

        private AnimatedCardsPanel() {
            setDoubleBuffered(true);
        }

        void requestFade(String pageName, CardLayout layout) {
            requestFade(pageName, layout, null);
        }

        void requestFade(String pageName, CardLayout layout, Runnable onComplete) {
            if (pageName == null || pageName.equals(currentPageName)) {
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }
            if (transitionRunning) {
                queuedPageName = pageName;
                queuedPageCompletion = onComplete;
                return;
            }

            long minVisibleNanos = PAGE_SCANNING.equals(currentPageName)
                    ? PAGE_SCANNING_MIN_VISIBLE_MS * 1_000_000L
                    : 0L;
            long visibleFor = System.nanoTime() - currentPageVisibleSinceNanos;
            if (minVisibleNanos > 0L && visibleFor < minVisibleNanos) {
                queuedPageName = pageName;
                queuedPageCompletion = onComplete;
                long delayMs = Math.max(1L, (minVisibleNanos - visibleFor + 999_999L) / 1_000_000L);
                scheduleQueuedTransition(layout, (int) delayMs);
                return;
            }

            fadeTo(pageName, layout, onComplete);
        }

        void fadeTo(String pageName, CardLayout layout, Runnable onComplete) {
            transitionRunning = true;
            pageFadeSequence++;
            Thread previousWorker = pageFadeWorker;
            if (previousWorker != null) {
                previousWorker.interrupt();
            }
            if (pageFadeTimer != null && pageFadeTimer.isRunning()) {
                pageFadeTimer.stop();
            }
            overlayAlpha = 0.0f;
            final long sequence = pageFadeSequence;
            Thread worker = new Thread(() -> runTransition(sequence, pageName, layout, onComplete), "potato-page-fade");
            worker.setDaemon(true);
            pageFadeWorker = worker;
            worker.start();
        }

        private void scheduleQueuedTransition(CardLayout layout, int delayMs) {
            if (queuedPageTransitionTimer != null && queuedPageTransitionTimer.isRunning()) {
                queuedPageTransitionTimer.stop();
            }
            queuedPageTransitionTimer = new Timer(delayMs, e -> {
                if (queuedPageTransitionTimer != null) {
                    queuedPageTransitionTimer.stop();
                }
                drainQueuedTransition();
            });
            queuedPageTransitionTimer.setRepeats(false);
            queuedPageTransitionTimer.start();
        }

        private void drainQueuedTransition() {
            if (transitionRunning) {
                return;
            }
            String nextPage = queuedPageName;
            Runnable nextCompletion = queuedPageCompletion;
            queuedPageName = null;
            queuedPageCompletion = null;
            if (nextPage == null) {
                return;
            }
            if (nextPage.equals(currentPageName)) {
                if (nextCompletion != null) {
                    nextCompletion.run();
                }
                return;
            }
            requestFade(nextPage, cardLayout, nextCompletion);
        }

        private void runTransition(long sequence, String pageName, CardLayout layout, Runnable onComplete) {
            long start = System.nanoTime();
            long frameNanos = PAGE_TRANSITION_TIMER_DELAY_MS * 1_000_000L;
            long durationNanos = PAGE_TRANSITION_DURATION_MS * 1_000_000L;
            long fadeOutNanos = (durationNanos * 22L) / 100L;
            long fadeInNanos = durationNanos - fadeOutNanos;
            long nextFrame = start;
            boolean switched = false;

            while (!Thread.currentThread().isInterrupted()) {
                if (sequence != pageFadeSequence) {
                    return;
                }

                long now = System.nanoTime();
                long elapsed = now - start;

                if (!switched) {
                    float fadeOutT = Math.min(1.0f, elapsed / (float) Math.max(1L, fadeOutNanos));
                    float eased = easeInOut(fadeOutT);
                    if (!applyTransitionState(sequence, eased, false, false, pageName, layout)) {
                        return;
                    }
                    if (fadeOutT >= 1.0f) {
                        switched = true;
                        if (!applyTransitionState(sequence, 1.0f, true, false, pageName, layout)) {
                            return;
                        }
                    }
                } else {
                    long fadeInElapsed = Math.max(0L, elapsed - fadeOutNanos);
                    float fadeInT = Math.min(1.0f, fadeInElapsed / (float) Math.max(1L, fadeInNanos));
                    float eased = easeInOut(fadeInT);
                    if (!applyTransitionState(sequence, 1.0f - eased, false, false, pageName, layout)) {
                        return;
                    }
                }

                if (elapsed >= durationNanos) {
                    break;
                }

                nextFrame += frameNanos;
                long waitNanos = nextFrame - System.nanoTime();
                if (waitNanos > 0L) {
                    LockSupport.parkNanos(waitNanos);
                } else {
                    nextFrame = System.nanoTime();
                }
            }

            finishTransition(sequence, onComplete);
        }

        private boolean applyTransitionState(long sequence,
                                             float overlay,
                                             boolean switchPage,
                                             boolean finish,
                                             String pageName,
                                             CardLayout layout) {
            final boolean[] success = { true };
            try {
                SwingUtilities.invokeAndWait(() -> {
                    if (sequence != pageFadeSequence) {
                        success[0] = false;
                        return;
                    }
                    overlayAlpha = Math.max(0.0f, Math.min(1.0f, overlay));
                    if (switchPage) {
                        layout.show(this, pageName);
                        revalidate();
                        doLayout();
                        currentPageName = pageName;
                        currentPageVisibleSinceNanos = System.nanoTime();
                    }
                    if (finish) {
                        overlayAlpha = 0.0f;
                        transitionRunning = false;
                    }
                    renderTransitionFrame();
                });
            } catch (Exception e) {
                return false;
            }
            return success[0];
        }

        private void finishTransition(long sequence, Runnable onComplete) {
            applyTransitionState(sequence, 0.0f, false, true, null, null);
            if (sequence == pageFadeSequence) {
                pageFadeWorker = null;
                if (onComplete != null) {
                    SwingUtilities.invokeLater(onComplete);
                }
                SwingUtilities.invokeLater(this::drainQueuedTransition);
            }
        }

        private float easeInOut(float t) {
            return 0.5f - 0.5f * (float) Math.cos(Math.PI * Math.max(0.0f, Math.min(1.0f, t)));
        }

        private void renderTransitionFrame() {
            if (!isShowing()) {
                repaint();
                return;
            }
            Rectangle visible = getVisibleRect();
            if (visible == null || visible.isEmpty()) {
                repaint();
                return;
            }
            paintImmediately(visible);
        }

        @Override
        protected void paintChildren(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            applyQualityRenderingHints(g2);
            super.paintChildren(g2);
            if (overlayAlpha > 0.0f) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, overlayAlpha));
                g2.setColor(bgColor);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.dispose();
        }
    }

    private class MinimalScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = theme.scrollbarThumb;
            trackColor = new Color(0, 0, 0, 0);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private JButton createZeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            button.setOpaque(false);
            button.setFocusable(false);
            button.setBorder(BorderFactory.createEmptyBorder());
            return button;
        }

        @Override
        protected Dimension getMinimumThumbSize() {
            return new Dimension(10, 44);
        }

        @Override
        protected void paintThumb(Graphics g, javax.swing.JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setColor(thumbColor);
            int x = thumbBounds.x + 2;
            int y = thumbBounds.y + 2;
            int w = Math.max(10, thumbBounds.width - 4);
            int h = Math.max(34, thumbBounds.height - 4);
            g2.fillRoundRect(x, y, w, h, 12, 12);
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, javax.swing.JComponent c, Rectangle trackBounds) {
            // Keep the track visually quiet so only the thumb is visible.
        }
    }

    private class ModernCheckBoxIcon implements Icon {
        private final boolean selected;
        private final boolean enabled;

        private ModernCheckBoxIcon(boolean selected, boolean enabled) {
            this.selected = selected;
            this.enabled = enabled;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            applyQualityRenderingHints(g2);
            applyTextRenderingHints(g2);

            Color fill = selected
                    ? (enabled ? accentColor : withAlpha(accentColor, 120))
                    : (enabled ? theme.checkboxFill : theme.checkboxDisabledFill);
            Color border = selected
                    ? (enabled ? accentColor.darker() : theme.checkboxDisabledBorder)
                    : (enabled ? theme.checkboxBorder : theme.checkboxDisabledBorder);
            Color mark = enabled ? theme.checkboxMark : withAlpha(theme.checkboxMark, 170);

            g2.setColor(fill);
            g2.fillRoundRect(x + 1, y + 1, 16, 16, 6, 6);
            g2.setColor(border);
            g2.drawRoundRect(x + 1, y + 1, 16, 16, 6, 6);

            if (selected) {
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(mark);
                g2.drawLine(x + 5, y + 9, x + 8, y + 12);
                g2.drawLine(x + 8, y + 12, x + 13, y + 6);
            }

            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 18;
        }

        @Override
        public int getIconHeight() {
            return 18;
        }
    }
}
