package com.potato.updater.config;

/**
 * 核心配置常量
 *
 * 【双逻辑与双需求分析】：
 * 系统逻辑：统一存放与服务端的交互约定数据，以及本地专属的特殊目录名，易于二次开发与分发修改。
 */
public class AppConfig {

    // Potato Updater 的专用工作空间名，放置在 Game Core 目录下
    public static final String UPDATER_TRAY_DIR_NAME = "A_Potato_Updater";

    // 生成/拉取的临时文件区
    public static final String DOWNLOAD_TEMP_DIR_NAME = "temp_downloads";

    // 日志目录
    public static final String LOGS_DIR_NAME = "logs";

    // 固定的本地状态记录文件
    public static final String LOCAL_STATE_FILE = "state.json";
    public static final String LOCAL_MANIFEST_FILE = "manifest.json";

    // ======= 测试默认配置，实际使用可能通过 seed 的 config 获取 =======
    // 默认的 R2 存储节点引导地址
    public static final String DEFAULT_BOOTSTRAP_URL = "https://example-r2-url.com/potato_bootstrap.json";

}
