package com.potato.seed.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class SeedGUI {
    private static final Color LINUX_SOLID_BG = new Color(250, 250, 250);
    private JFrame frame;
    private String currentStatus = "boot";
    private MainPanel mainPanel;
    private boolean isHovered = false;
    private final WindowRuntime windowRuntime = WindowRuntime.detect();

    // 阻塞状态变量
    private boolean isBlocked = false;
    private boolean userDecision = false;
    private boolean decisionMade = false;
    private String blockMessage = "";

    // 动画与超时状态
    private Timer progressTimer;
    private float progressOffset = 0f;
    private float hoverAnim = 0f; // 0.0 (未悬浮) 到 1.0 (完全悬浮)
    private long startTime;
    private boolean isTimedOut = false;

    // 拖拽相关
    private int initialClickX, initialClickY;

    // 按钮交互动画状态
    private boolean hoveredLeftBtn = false;
    private boolean hoveredRightBtn = false;
    private boolean hoveredCenterBtn = false;
    private float leftBtnAnim = 0f;
    private float rightBtnAnim = 0f;
    private float centerBtnAnim = 0f;
    private volatile Runnable closeRequestHandler;
    private volatile boolean suppressCloseRequestHandler = false;
    private volatile boolean closeRequestDispatched = false;

    public void show() {
        startTime = System.currentTimeMillis();
        frame = new JFrame();
        configureFrameChrome();
        frame.setBackground(new Color(0, 0, 0, 0)); // 窗体外部透明以显示圆角
        frame.setBackground(windowRuntime.useTransparentWindowBackground() ? new Color(0, 0, 0, 0) : LINUX_SOLID_BG);
        frame.setSize(320, 130);
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(windowRuntime.useAlwaysOnTop());
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        System.out.println("[PotatoSeed] Window runtime: " + windowRuntime.describe());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispatchCloseRequest();
            }
        });

        mainPanel = new MainPanel();

        // 监听鼠标悬停来控制附属文本的可见性
        mainPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHovered = true;
                mainPanel.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // 如果鼠标实际离开了窗口区域
                if (!mainPanel.contains(e.getPoint())) {
                    isHovered = false;
                    mainPanel.repaint();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                initialClickX = e.getX();
                initialClickY = e.getY();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int w = mainPanel.getWidth();
                int h = mainPanel.getHeight();

                if (isBlocked) {
                    // 忽略按钮 (左边)
                    if (e.getX() >= w / 2 - 80 && e.getX() <= w / 2 - 10 && e.getY() >= h - 40 && e.getY() <= h - 12) {
                        userDecision = true;
                        decisionMade = true;
                    }
                    // 退出按钮 (右边)
                    if (e.getX() >= w / 2 + 10 && e.getX() <= w / 2 + 80 && e.getY() >= h - 40 && e.getY() <= h - 12) {
                        userDecision = false;
                        decisionMade = true;
                    }
                } else if (isTimedOut) {
                    // 强制退出按钮 (居中)
                    if (e.getX() >= w / 2 - 35 && e.getX() <= w / 2 + 35 && e.getY() >= h - 40 && e.getY() <= h - 12) {
                        System.exit(1);
                    }
                }
            }
        });

        mainPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (!isHovered) {
                    isHovered = true;
                }

                int w = mainPanel.getWidth();
                int h = mainPanel.getHeight();
                boolean repaintNeeded = false;

                if (isBlocked) {
                    boolean nl = (e.getX() >= w / 2 - 80 && e.getX() <= w / 2 - 10 && e.getY() >= h - 40 && e.getY() <= h - 12);
                    boolean nr = (e.getX() >= w / 2 + 10 && e.getX() <= w / 2 + 80 && e.getY() >= h - 40 && e.getY() <= h - 12);
                    if (hoveredLeftBtn != nl || hoveredRightBtn != nr) repaintNeeded = true;
                    hoveredLeftBtn = nl;
                    hoveredRightBtn = nr;
                } else if (isTimedOut) {
                    boolean nc = (e.getX() >= w / 2 - 35 && e.getX() <= w / 2 + 35 && e.getY() >= h - 40 && e.getY() <= h - 12);
                    if (hoveredCenterBtn != nc) repaintNeeded = true;
                    hoveredCenterBtn = nc;
                }

                if (repaintNeeded || isHovered) {
                    mainPanel.repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                // 实现无边框窗口的拖拽移动
                int thisX = frame.getLocation().x;
                int thisY = frame.getLocation().y;
                int xMoved = e.getX() - initialClickX;
                int yMoved = e.getY() - initialClickY;
                frame.setLocation(thisX + xMoved, thisY + yMoved);
            }
        });

        frame.setContentPane(mainPanel);

        // 进度条与过渡动画驱动
        progressTimer = new Timer(12, e -> {
            progressOffset += 0.9f;
            if (progressOffset > 80) progressOffset = -20;

            // 悬浮平滑过渡处理 (接近 1.0 或 0.0)
            boolean targetHover = isHovered || isBlocked || isTimedOut;
            if (targetHover && hoverAnim < 1.0f) {
                hoverAnim = Math.min(1.0f, hoverAnim + 0.1f); // 淡入速度
            } else if (!targetHover && hoverAnim > 0.0f) {
                hoverAnim = Math.max(0.0f, hoverAnim - 0.1f); // 淡出速度
            }

            // 按钮悬浮动画过渡
            if (hoveredLeftBtn && leftBtnAnim < 1.0f) leftBtnAnim = Math.min(1.0f, leftBtnAnim + 0.12f);
            else if (!hoveredLeftBtn && leftBtnAnim > 0.0f) leftBtnAnim = Math.max(0.0f, leftBtnAnim - 0.12f);

            if (hoveredRightBtn && rightBtnAnim < 1.0f) rightBtnAnim = Math.min(1.0f, rightBtnAnim + 0.12f);
            else if (!hoveredRightBtn && rightBtnAnim > 0.0f) rightBtnAnim = Math.max(0.0f, rightBtnAnim - 0.12f);

            if (hoveredCenterBtn && centerBtnAnim < 1.0f) centerBtnAnim = Math.min(1.0f, centerBtnAnim + 0.12f);
            else if (!hoveredCenterBtn && centerBtnAnim > 0.0f) centerBtnAnim = Math.max(0.0f, centerBtnAnim - 0.12f);

            // 超时检测（45秒无关闭则视为严重超时卡死）
            if (!isTimedOut && !isBlocked && (System.currentTimeMillis() - startTime > 45000)) {
                isTimedOut = true;
            }

            if (frame.isVisible()) {
                mainPanel.repaint();
            }
        });
        progressTimer.start();

        finalizeFrameChrome();
        frame.setVisible(true);
        if (windowRuntime.useStableLinuxWindowMode()) {
            SwingUtilities.invokeLater(() -> {
                frame.setLocationRelativeTo(null);
                frame.repaint();
            });
        }
    }

    public void setCloseRequestHandler(Runnable closeRequestHandler) {
        this.closeRequestHandler = closeRequestHandler;
    }

    public void updateStatus(String text) {
        this.currentStatus = text;
        if (mainPanel != null) {
            SwingUtilities.invokeLater(() -> mainPanel.repaint());
        }
    }

    public boolean askContinue(String message) {
        this.blockMessage = message;
        this.isBlocked = true;
        updateStatus("遇到异常，需人工干预");

        decisionMade = false;
        isHovered = true; // 阻塞时强制算作激发状态以显示提示
        // 移除原有的 hoverAnim = 1.0f 硬设定，让后台 Timer 的 +0.15f 渐变器接管平滑浮现动画

        if (mainPanel != null) {
            SwingUtilities.invokeLater(() -> mainPanel.repaint());
        }

        while (!decisionMade) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        this.isBlocked = false;
        if (mainPanel != null) {
            SwingUtilities.invokeLater(() -> mainPanel.repaint());
        }
        return userDecision;
    }

    public void close() {
        fadeOutAndClose();
    }

    public void fadeOutAndClose() {
        if (frame != null && frame.isVisible()) {
            suppressCloseRequestHandler = true;
            if (!windowRuntime.useWindowOpacityEffects()) {
                if (progressTimer != null) {
                    progressTimer.stop();
                }
                SwingUtilities.invokeLater(() -> frame.dispose());
                return;
            }
            new Thread(() -> {
                if (progressTimer != null) progressTimer.stop();
                for (float i = 1.0f; i >= 0.0f; i -= 0.05f) {
                    final float opacity = i;
                    SwingUtilities.invokeLater(() -> frame.setOpacity(opacity));
                    try { Thread.sleep(20); } catch (Exception ignored) {}
                }
                SwingUtilities.invokeLater(() -> frame.dispose());
            }).start();
        }
    }

    public void disposeSilently() {
        suppressCloseRequestHandler = true;
        if (progressTimer != null) {
            progressTimer.stop();
        }
        if (frame != null) {
            frame.dispose();
        }
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
        Thread closeThread = new Thread(handler, "potato-seed-close");
        closeThread.setDaemon(true);
        closeThread.start();
    }

    // 自定义核心渲染面板
    private void configureFrameChrome() {
        frame.setUndecorated(true);
    }

    private void finalizeFrameChrome() {
        if (windowRuntime.useStableLinuxWindowMode()) {
            frame.setUndecorated(false);
        }
    }

    private class MainPanel extends JPanel {
        public MainPanel() {
            setOpaque(windowRuntime.useStableLinuxWindowMode());
            // 手动开启双缓冲消除闪烁
            setDoubleBuffered(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();

            // 开启最高质量抗锯齿
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int width = getWidth();
            int height = getHeight();

            // 1. 绘制带有圆角的底板壳 (完全不透明纯色)
            int radius = 16;
            g2.setColor(new Color(250, 250, 250, 255));
            if (windowRuntime.useStableLinuxWindowMode()) {
                g2.fillRect(0, 0, width, height);
                g2.setColor(new Color(230, 230, 230));
                g2.drawRect(0, 0, width - 1, height - 1);
            } else {
                g2.fillRoundRect(0, 0, width, height, radius, radius);

            // 细微的边框勾勒
            g2.setColor(new Color(230, 230, 230));
            g2.drawRoundRect(0, 0, width - 1, height - 1, radius, radius);
            }

            // 基于 hoverAnim 计算各个元素的 Y 轴位移
            // 将默认 Y 往上提，使得字和进度条的整体几何中轴线恰好位于界面高度 1/2 处
            int titleY = height / 2 - 4; // 固定中心基准 Y

            // 2. 居中绘制 "Potato Seed"
            g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
            FontMetrics fm = g2.getFontMetrics();
            String title = "Potato Seed";
            int titleWidth = fm.stringWidth(title);

            g2.setColor(new Color(60, 60, 60));
            g2.drawString(title, (width - titleWidth) / 2, titleY);

            // 3. 绘制进度条（蓝色圆角长条）
            if (!isBlocked) {
                int barWidth = 60;
                int barHeight = 4;
                int barX = (width - barWidth) / 2;
                int barY = titleY + 14;

                // 进度条槽
                g2.setColor(new Color(230, 230, 230));
                g2.fillRoundRect(barX, barY, barWidth, barHeight, barHeight, barHeight);

                // 蓝色的游标
                g2.setClip(new java.awt.geom.RoundRectangle2D.Float(barX, barY, barWidth, barHeight, barHeight, barHeight));
                g2.setColor(new Color(40, 150, 255)); // 现代蓝色
                int sweepWidth = 20;
                g2.fillRoundRect(barX + (int)progressOffset, barY, sweepWidth, barHeight, barHeight, barHeight);
                g2.setClip(null);
            }

            // 4. 绘制悬停/阻塞时的额外元素 (带有 Alpha 渐变效果)
            if (hoverAnim > 0.01f) {
                // 根据 hoverAnim 计算文本不透明度 (0 到 255)
                int textAlpha = (int)(hoverAnim * 255);
                textAlpha = Math.max(0, Math.min(255, textAlpha));

                g2.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
                fm = g2.getFontMetrics();

                if (isBlocked) {
                    // 绘制报错提示和两个虚拟按钮
                    g2.setColor(new Color(220, 60, 60, textAlpha)); // 红色警告文本
                    String[] lines = blockMessage.split("\n");
                    int msgY = titleY + 20;
                    for (int i = 0; i < lines.length; i++) {
                        int lw = fm.stringWidth(lines[i]);
                        g2.drawString(lines[i], (width - lw) / 2, msgY + i * 16);
                    }

                    // 绘制按钮
                    g2.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
                    int btnRootY = height - 40;

                    // 左侧按钮颜色融合
                    int lbg = 240 - (int)(leftBtnAnim * 20); // 240 -> 220
                    int lborder = 200 - (int)(leftBtnAnim * 40); // 200 -> 160
                    // 左侧：继续
                    g2.setColor(new Color(lbg, lbg, lbg, textAlpha));
                    g2.fillRoundRect(width / 2 - 80, btnRootY, 70, 28, 8, 8);
                    g2.setColor(new Color(lborder, lborder, lborder, textAlpha));
                    g2.drawRoundRect(width / 2 - 80, btnRootY, 70, 28, 8, 8);
                    g2.setColor(new Color(80, 80, 80, textAlpha));
                    g2.drawString("跳过继续", width / 2 - 68, btnRootY + 19);

                    // 右侧按钮颜色融合
                    int rbg = 240 - (int)(rightBtnAnim * 20);
                    int rborder = 200 - (int)(rightBtnAnim * 40);
                    // 右侧：退出
                    g2.setColor(new Color(rbg, rbg, rbg, textAlpha));
                    g2.fillRoundRect(width / 2 + 10, btnRootY, 70, 28, 8, 8);
                    g2.setColor(new Color(rborder, rborder, rborder, textAlpha));
                    g2.drawRoundRect(width / 2 + 10, btnRootY, 70, 28, 8, 8);
                    g2.setColor(new Color(80, 80, 80, textAlpha));
                    g2.drawString("停止退出", width / 2 + 22, btnRootY + 19);

                } else if (isTimedOut) {
                    g2.setColor(new Color(220, 60, 60, textAlpha)); // 红色警告文本
                    String timeoutMsg = "进程响应过慢，可能已卡死";
                    int msgWidth = fm.stringWidth(timeoutMsg);
                    g2.drawString(timeoutMsg, (width - msgWidth) / 2, titleY + 22);

                    int btnRootY = height - 40;
                    int cbg = 240 - (int)(centerBtnAnim * 20);
                    int cborder = 200 - (int)(centerBtnAnim * 40);

                    g2.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
                    g2.setColor(new Color(cbg, cbg, cbg, textAlpha));
                    g2.fillRoundRect(width / 2 - 35, btnRootY, 70, 28, 8, 8);
                    g2.setColor(new Color(cborder, cborder, cborder, textAlpha));
                    g2.drawRoundRect(width / 2 - 35, btnRootY, 70, 28, 8, 8);
                    g2.setColor(new Color(220, 40, 40, textAlpha));
                    g2.drawString("强制退出", width / 2 - 22, btnRootY + 19);

                } else {
                    // 仅显示状态文本
                    g2.setColor(new Color(120, 120, 120, textAlpha));
                    int statusWidth = fm.stringWidth(currentStatus);
                    int statusY = height - 20;
                    g2.drawString(currentStatus, (width - statusWidth) / 2, statusY);
                }
            }

            g2.dispose();
        }
    }
}
