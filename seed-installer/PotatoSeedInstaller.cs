using Microsoft.Win32;
using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Net;
using System.Diagnostics;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Web.Script.Serialization;
using System.Windows.Forms;

namespace PotatoSeedInstaller
{
    internal static class UiText
    {
        public const string NotDetected = "\u672a\u68c0\u6d4b\u5230";
        public const string Title = "\u51c6\u5907\u5b89\u88c5 Potato_Seed";
        public const string Subtitle = "\u8bf7\u786e\u8ba4\u7248\u672c\u540d\u79f0\u548c\u542f\u52a8\u5668\u76ee\u5f55\u540d\u79f0\uff0c\u518d\u9009\u62e9\u542f\u52a8\u53c2\u6570\u5199\u5165\u65b9\u5f0f\u3002";
        public const string CoreDir = "\u6e38\u620f\u6838\u5fc3\u76ee\u5f55";
        public const string VersionName = "\u7248\u672c\u540d\u79f0";
        public const string LauncherDir = "\u542f\u52a8\u5668\u76ee\u5f55";
        public const string Choose = "\u9009\u62e9...";
        public const string Redetect = "\u91cd\u65b0\u68c0\u6d4b";
        public const string DetectedVersion = "\u5f53\u524d\u68c0\u6d4b\u5230\u7684\u7248\u672c\u540d\u79f0";
        public const string DetectedLauncher = "\u5f53\u524d\u68c0\u6d4b\u5230\u7684\u542f\u52a8\u5668\u76ee\u5f55\u540d\u79f0";
        public const string ModeTitle = "\u542f\u52a8\u53c2\u6570\u5199\u5165\u65b9\u5f0f";
        public const string ReplaceMode = "\u66ff\u6362\u51b2\u7a81\u9879\u5e76\u4fdd\u7559\u5176\u4f59\u53c2\u6570\uff08\u63a8\u8350\uff09";
        public const string AppendMode = "\u5728\u539f\u6709\u53c2\u6570\u540e\u9644\u52a0\u65b0\u7684 Potato_Seed \u53c2\u6570";
        public const string StartInstall = "\u5f00\u59cb\u5b89\u88c5";
        public const string Exit = "\u9000\u51fa";
        public const string Idle = "\u628a\u5b89\u88c5\u5668\u653e\u8fdb\u7248\u672c\u76ee\u5f55\u6216 .minecraft \u76ee\u5f55\u540e\u53cc\u51fb\u5373\u53ef\u3002";
        public const string Installing = "\u6b63\u5728\u5b89\u88c5 Potato_Seed\uff0c\u8bf7\u7a0d\u5019...";
        public const string Success = "\u5b89\u88c5\u6210\u529f\u3002";
        public const string Failed = "\u5b89\u88c5\u5931\u8d25\u3002";
        public const string SuccessBody = "\u5b89\u88c5\u6210\u529f\u3002\n\n\u7248\u672c\u540d\u79f0\uff1a";
        public const string LauncherBody = "\n\u542f\u52a8\u5668\u76ee\u5f55\u540d\u79f0\uff1a";
        public const string PathBody = "\n\u5b89\u88c5\u4f4d\u7f6e\uff1a";
        public const string RestartBody = "\n\u542f\u52a8\u5668\u5904\u7406\uff1a";
        public const string Restarted = "\u5df2\u5173\u95ed\u5e76\u91cd\u65b0\u542f\u52a8 PCL";
        public const string RestartSkipped = "\u672a\u627e\u5230\u53ef\u91cd\u542f\u7684 PCL \u4e3b\u7a0b\u5e8f\uff0c\u8bf7\u624b\u52a8\u91cd\u542f";
        public const string InvalidOptions = "\u5b89\u88c5\u53c2\u6570\u4e3a\u7a7a\u3002";
        public const string InvalidCoreDir = "\u672a\u68c0\u6d4b\u5230\u6709\u6548\u7684\u6e38\u620f\u6838\u5fc3\u76ee\u5f55\uff0c\u8bf7\u5148\u786e\u8ba4\u6216\u624b\u52a8\u9009\u62e9\u3002";
    }

    internal static class Program
    {
        [STAThread]
        private static void Main(string[] args)
        {
            ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072 | SecurityProtocolType.Tls12;

            if (HasFlag(args, "--smoke-test"))
            {
                Environment.ExitCode = RunSmokeTest();
                return;
            }

            InstallOptions cliOptions;
            if (TryParseCli(args, out cliOptions))
            {
                try
                {
                    InstallResult result = InstallerLogic.Install(cliOptions);
                    SafeWriteLine("INSTALL_OK");
                    SafeWriteLine("CoreDir=" + result.CoreDir);
                    SafeWriteLine("VersionName=" + result.VersionName);
                    SafeWriteLine("LauncherDir=" + result.LauncherDir);
                    SafeWriteLine("Mode=" + result.Mode);
                    return;
                }
                catch (Exception ex)
                {
                    SafeWriteLine(ex.Message);
                    Environment.Exit(1);
                    return;
                }
            }

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new InstallerForm());
        }

        private static bool HasFlag(string[] args, string expected)
        {
            if (args == null)
            {
                return false;
            }

            foreach (string arg in args)
            {
                if (expected.Equals(arg, StringComparison.OrdinalIgnoreCase))
                {
                    return true;
                }
            }
            return false;
        }

        private static int RunSmokeTest()
        {
            Uri seedDownloadUri;
            Uri seedConfigUri;
            bool valid = Uri.TryCreate(BuildEndpoints.SeedDownloadUrl, UriKind.Absolute, out seedDownloadUri) &&
                         Uri.TryCreate(BuildEndpoints.SeedConfigUrl, UriKind.Absolute, out seedConfigUri) &&
                         seedDownloadUri != null &&
                         seedConfigUri != null &&
                         !string.IsNullOrWhiteSpace(seedDownloadUri.Host) &&
                         seedDownloadUri.Host.Equals(seedConfigUri.Host, StringComparison.OrdinalIgnoreCase) &&
                         (seedDownloadUri.Scheme.Equals("http", StringComparison.OrdinalIgnoreCase) ||
                          seedDownloadUri.Scheme.Equals("https", StringComparison.OrdinalIgnoreCase));
            if (!valid)
            {
                SafeWriteLine("POTATO_INSTALLER_SMOKE_TEST_FAILED");
                return 1;
            }

            SafeWriteLine("POTATO_INSTALLER_SMOKE_TEST_OK");
            return 0;
        }

        private static void SafeWriteLine(string value)
        {
            try
            {
                Console.OutputEncoding = Encoding.UTF8;
                Console.WriteLine(value);
            }
            catch
            {
            }
        }

        private static bool TryParseCli(string[] args, out InstallOptions options)
        {
            options = null;
            if (args == null || args.Length == 0)
            {
                return false;
            }

            bool silent = false;
            string coreDir = null;
            string launcherDir = null;
            string versionName = null;
            InstallMode mode = InstallMode.Replace;

            for (int i = 0; i < args.Length; i++)
            {
                string arg = args[i] ?? string.Empty;
                if (arg.Equals("--silent", StringComparison.OrdinalIgnoreCase))
                {
                    silent = true;
                    continue;
                }
                if (arg.Equals("--core-dir", StringComparison.OrdinalIgnoreCase) && i + 1 < args.Length)
                {
                    coreDir = args[++i];
                    continue;
                }
                if (arg.Equals("--launcher-dir", StringComparison.OrdinalIgnoreCase) && i + 1 < args.Length)
                {
                    launcherDir = args[++i];
                    continue;
                }
                if (arg.Equals("--version-name", StringComparison.OrdinalIgnoreCase) && i + 1 < args.Length)
                {
                    versionName = args[++i];
                    continue;
                }
                if (arg.Equals("--mode", StringComparison.OrdinalIgnoreCase) && i + 1 < args.Length)
                {
                    mode = args[++i].Equals("append", StringComparison.OrdinalIgnoreCase)
                        ? InstallMode.Append
                        : InstallMode.Replace;
                }
            }

            if (!silent)
            {
                return false;
            }

            DetectionContext detected = InstallerLogic.Detect(Path.GetDirectoryName(Application.ExecutablePath) ?? Environment.CurrentDirectory);
            options = new InstallOptions
            {
                CoreDir = string.IsNullOrWhiteSpace(coreDir) ? detected.CoreDir : coreDir,
                LauncherDir = string.IsNullOrWhiteSpace(launcherDir) ? detected.LauncherDir : launcherDir,
                VersionName = string.IsNullOrWhiteSpace(versionName) ? detected.VersionName : versionName,
                Mode = mode,
                RestartLauncherAfterInstall = false
            };
            return true;
        }
    }

    internal sealed class InstallerForm : Form
    {
        private readonly TextBox coreDirTextBox;
        private readonly TextBox versionNameTextBox;
        private readonly TextBox launcherDirTextBox;
        private readonly Label detectedVersionValue;
        private readonly Label detectedLauncherValue;
        private readonly Label statusLabel;
        private readonly RadioButton replaceRadio;
        private readonly Button installButton;
        private readonly Button cancelButton;

        public InstallerForm()
        {
            Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Regular, GraphicsUnit.Point);
            Text = "Potato Seed Installer";
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            ClientSize = new Size(680, 440);
            BackColor = Color.FromArgb(247, 249, 252);

            Label title = new Label();
            title.Text = UiText.Title;
            title.Font = new Font("Microsoft YaHei UI", 16F, FontStyle.Bold, GraphicsUnit.Point);
            title.AutoSize = true;
            title.Location = new Point(24, 22);

            Label subtitle = new Label();
            subtitle.Text = UiText.Subtitle;
            subtitle.ForeColor = Color.FromArgb(90, 102, 118);
            subtitle.AutoSize = true;
            subtitle.Location = new Point(26, 56);

            Panel detectPanel = new Panel();
            detectPanel.BackColor = Color.White;
            detectPanel.BorderStyle = BorderStyle.FixedSingle;
            detectPanel.Location = new Point(24, 88);
            detectPanel.Size = new Size(632, 202);

            Label coreDirLabel = new Label();
            coreDirLabel.Text = UiText.CoreDir;
            coreDirLabel.AutoSize = true;
            coreDirLabel.Location = new Point(18, 20);

            coreDirTextBox = new TextBox();
            coreDirTextBox.Location = new Point(18, 42);
            coreDirTextBox.Size = new Size(500, 28);
            coreDirTextBox.TextChanged += delegate { RefreshDetectedInfo(); };

            Button browseCoreButton = new Button();
            browseCoreButton.Text = UiText.Choose;
            browseCoreButton.Location = new Point(530, 40);
            browseCoreButton.Size = new Size(82, 31);
            browseCoreButton.Click += delegate { BrowseForFolder(coreDirTextBox); };

            Button redetectButton = new Button();
            redetectButton.Text = UiText.Redetect;
            redetectButton.Location = new Point(530, 98);
            redetectButton.Size = new Size(82, 31);
            redetectButton.Click += delegate { AutoDetect(); };

            Label versionLabel = new Label();
            versionLabel.Text = UiText.VersionName;
            versionLabel.AutoSize = true;
            versionLabel.Location = new Point(18, 82);

            versionNameTextBox = new TextBox();
            versionNameTextBox.Location = new Point(18, 104);
            versionNameTextBox.Size = new Size(240, 28);
            versionNameTextBox.TextChanged += delegate { RefreshDetectedInfo(); };

            Label launcherLabel = new Label();
            launcherLabel.Text = UiText.LauncherDir;
            launcherLabel.AutoSize = true;
            launcherLabel.Location = new Point(18, 144);

            launcherDirTextBox = new TextBox();
            launcherDirTextBox.Location = new Point(18, 166);
            launcherDirTextBox.Size = new Size(500, 28);
            launcherDirTextBox.TextChanged += delegate { RefreshDetectedInfo(); };

            Button browseLauncherButton = new Button();
            browseLauncherButton.Text = UiText.Choose;
            browseLauncherButton.Location = new Point(530, 164);
            browseLauncherButton.Size = new Size(82, 31);
            browseLauncherButton.Click += delegate { BrowseForFolder(launcherDirTextBox); };

            detectPanel.Controls.Add(coreDirLabel);
            detectPanel.Controls.Add(coreDirTextBox);
            detectPanel.Controls.Add(browseCoreButton);
            detectPanel.Controls.Add(redetectButton);
            detectPanel.Controls.Add(versionLabel);
            detectPanel.Controls.Add(versionNameTextBox);
            detectPanel.Controls.Add(launcherLabel);
            detectPanel.Controls.Add(launcherDirTextBox);
            detectPanel.Controls.Add(browseLauncherButton);

            Panel summaryPanel = new Panel();
            summaryPanel.BackColor = Color.White;
            summaryPanel.BorderStyle = BorderStyle.FixedSingle;
            summaryPanel.Location = new Point(24, 304);
            summaryPanel.Size = new Size(632, 74);

            Label detectedVersionLabel = new Label();
            detectedVersionLabel.Text = UiText.DetectedVersion;
            detectedVersionLabel.AutoSize = true;
            detectedVersionLabel.Location = new Point(18, 16);

            detectedVersionValue = new Label();
            detectedVersionValue.Text = UiText.NotDetected;
            detectedVersionValue.Font = new Font("Microsoft YaHei UI", 11F, FontStyle.Bold, GraphicsUnit.Point);
            detectedVersionValue.AutoSize = true;
            detectedVersionValue.Location = new Point(18, 38);

            Label detectedLauncherLabel = new Label();
            detectedLauncherLabel.Text = UiText.DetectedLauncher;
            detectedLauncherLabel.AutoSize = true;
            detectedLauncherLabel.Location = new Point(332, 16);

            detectedLauncherValue = new Label();
            detectedLauncherValue.Text = UiText.NotDetected;
            detectedLauncherValue.Font = new Font("Microsoft YaHei UI", 11F, FontStyle.Bold, GraphicsUnit.Point);
            detectedLauncherValue.AutoSize = true;
            detectedLauncherValue.Location = new Point(332, 38);

            summaryPanel.Controls.Add(detectedVersionLabel);
            summaryPanel.Controls.Add(detectedVersionValue);
            summaryPanel.Controls.Add(detectedLauncherLabel);
            summaryPanel.Controls.Add(detectedLauncherValue);

            GroupBox modeGroup = new GroupBox();
            modeGroup.Text = UiText.ModeTitle;
            modeGroup.Location = new Point(24, 388);
            modeGroup.Size = new Size(460, 46);

            replaceRadio = new RadioButton();
            replaceRadio.Text = UiText.ReplaceMode;
            replaceRadio.AutoSize = true;
            replaceRadio.Location = new Point(16, 18);
            replaceRadio.Checked = true;

            RadioButton appendRadio = new RadioButton();
            appendRadio.Text = UiText.AppendMode;
            appendRadio.AutoSize = true;
            appendRadio.Location = new Point(240, 18);

            modeGroup.Controls.Add(replaceRadio);
            modeGroup.Controls.Add(appendRadio);

            installButton = new Button();
            installButton.Text = UiText.StartInstall;
            installButton.Location = new Point(506, 392);
            installButton.Size = new Size(150, 42);
            installButton.BackColor = Color.FromArgb(47, 127, 236);
            installButton.ForeColor = Color.White;
            installButton.FlatStyle = FlatStyle.Flat;
            installButton.FlatAppearance.BorderSize = 0;
            installButton.Click += InstallButton_Click;

            cancelButton = new Button();
            cancelButton.Text = UiText.Exit;
            cancelButton.Location = new Point(506, 344);
            cancelButton.Size = new Size(150, 36);
            cancelButton.Click += delegate { Close(); };

            statusLabel = new Label();
            statusLabel.Text = UiText.Idle;
            statusLabel.AutoSize = false;
            statusLabel.Size = new Size(460, 42);
            statusLabel.Location = new Point(24, 398);
            statusLabel.ForeColor = Color.FromArgb(90, 102, 118);

            Controls.Add(title);
            Controls.Add(subtitle);
            Controls.Add(detectPanel);
            Controls.Add(summaryPanel);
            Controls.Add(modeGroup);
            Controls.Add(cancelButton);
            Controls.Add(installButton);
            Controls.Add(statusLabel);

            AutoDetect();
        }

        private void AutoDetect()
        {
            DetectionContext context = InstallerLogic.Detect(Path.GetDirectoryName(Application.ExecutablePath) ?? Environment.CurrentDirectory);
            coreDirTextBox.Text = context.CoreDir ?? string.Empty;
            versionNameTextBox.Text = context.VersionName ?? string.Empty;
            launcherDirTextBox.Text = context.LauncherDir ?? string.Empty;
            RefreshDetectedInfo();
        }

        private void BrowseForFolder(TextBox target)
        {
            using (FolderBrowserDialog dialog = new FolderBrowserDialog())
            {
                dialog.ShowNewFolderButton = false;
                if (Directory.Exists(target.Text))
                {
                    dialog.SelectedPath = target.Text;
                }

                if (dialog.ShowDialog(this) == DialogResult.OK)
                {
                    target.Text = dialog.SelectedPath;
                }
            }
        }

        private void RefreshDetectedInfo()
        {
            string versionName = versionNameTextBox.Text.Trim();
            string launcherDir = launcherDirTextBox.Text.Trim();
            detectedVersionValue.Text = string.IsNullOrWhiteSpace(versionName) ? UiText.NotDetected : versionName;
            detectedLauncherValue.Text = string.IsNullOrWhiteSpace(launcherDir)
                ? UiText.NotDetected
                : Path.GetFileName(launcherDir.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar));
            if (string.IsNullOrWhiteSpace(detectedLauncherValue.Text))
            {
                detectedLauncherValue.Text = UiText.NotDetected;
            }
        }

        private void InstallButton_Click(object sender, EventArgs e)
        {
            InstallOptions options = new InstallOptions
            {
                CoreDir = coreDirTextBox.Text.Trim(),
                VersionName = versionNameTextBox.Text.Trim(),
                LauncherDir = launcherDirTextBox.Text.Trim(),
                Mode = replaceRadio.Checked ? InstallMode.Replace : InstallMode.Append,
                RestartLauncherAfterInstall = true
            };

            installButton.Enabled = false;
            cancelButton.Enabled = false;
            statusLabel.Text = UiText.Installing;

            try
            {
                InstallResult result = InstallerLogic.Install(options);
                statusLabel.Text = UiText.Success;
                MessageBox.Show(
                    this,
                    UiText.SuccessBody + result.VersionName + UiText.LauncherBody + result.LauncherLeafName + UiText.PathBody + result.SeedTarget + UiText.RestartBody + (result.LauncherRestarted ? UiText.Restarted : UiText.RestartSkipped),
                    "Potato Seed Installer",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Information);
                Close();
            }
            catch (Exception ex)
            {
                statusLabel.Text = UiText.Failed;
                MessageBox.Show(
                    this,
                    UiText.Failed + "\n\n" + ex.Message,
                    "Potato Seed Installer",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
            }
            finally
            {
                installButton.Enabled = true;
                cancelButton.Enabled = true;
            }
        }
    }

    internal enum InstallMode
    {
        Replace,
        Append
    }

    internal sealed class InstallOptions
    {
        public string CoreDir;
        public string VersionName;
        public string LauncherDir;
        public InstallMode Mode;
        public bool RestartLauncherAfterInstall;
    }

    internal sealed class DetectionContext
    {
        public string CoreDir;
        public string VersionName;
        public string LauncherDir;
    }

    internal sealed class InstallResult
    {
        public string CoreDir;
        public string VersionName;
        public string LauncherDir;
        public string LauncherLeafName;
        public string SeedTarget;
        public string Mode;
        public string LauncherExePath;
        public bool LauncherRestarted;
    }

    internal static class InstallerLogic
    {
        private static readonly string DownloadUrl = BuildEndpoints.SeedDownloadUrl;
        private const string RegistryPath = @"Software\PCL";
        private const string RegistryValueName = "LaunchAdvanceJvm";
        private const int DownloadMaxAttempts = 3;
        private const int DownloadConnectTimeoutMs = 15000;
        private const int DownloadReadWriteTimeoutMs = 30000;
        private const int DownloadRetryDelayMs = 1000;

        public static DetectionContext Detect(string startDir)
        {
            string normalized = Path.GetFullPath(startDir);
            string leaf = Path.GetFileName(normalized);
            if (leaf.Equals("mods", StringComparison.OrdinalIgnoreCase) ||
                leaf.Equals("A_Potato_Seed", StringComparison.OrdinalIgnoreCase) ||
                leaf.Equals("A_Potato_Updater", StringComparison.OrdinalIgnoreCase))
            {
                string parentDir = Path.GetDirectoryName(normalized);
                if (!string.IsNullOrWhiteSpace(parentDir))
                {
                    normalized = parentDir;
                }
            }

            string versionName = string.Empty;
            string launcherDir = string.Empty;
            string coreLeaf = Path.GetFileName(normalized);
            string parent = Path.GetDirectoryName(normalized);
            string parentLeaf = string.IsNullOrWhiteSpace(parent) ? string.Empty : Path.GetFileName(parent);
            string grandParent = string.IsNullOrWhiteSpace(parent) ? string.Empty : Path.GetDirectoryName(parent);
            string grandParentLeaf = string.IsNullOrWhiteSpace(grandParent) ? string.Empty : Path.GetFileName(grandParent);

            if (coreLeaf.Equals(".minecraft", StringComparison.OrdinalIgnoreCase))
            {
                versionName = ".minecraft";
                launcherDir = Path.GetDirectoryName(normalized) ?? string.Empty;
            }
            else if (parentLeaf.Equals("versions", StringComparison.OrdinalIgnoreCase) &&
                     grandParentLeaf.Equals(".minecraft", StringComparison.OrdinalIgnoreCase))
            {
                versionName = coreLeaf;
                launcherDir = Path.GetDirectoryName(grandParent) ?? string.Empty;
            }
            else
            {
                string walker = normalized;
                while (!string.IsNullOrWhiteSpace(walker))
                {
                    string walkerLeaf = Path.GetFileName(walker);
                    if (walkerLeaf.Equals(".minecraft", StringComparison.OrdinalIgnoreCase))
                    {
                        launcherDir = Path.GetDirectoryName(walker) ?? string.Empty;
                        break;
                    }

                    string next = Path.GetDirectoryName(walker);
                    if (string.IsNullOrWhiteSpace(next) || next == walker)
                    {
                        break;
                    }
                    walker = next;
                }

                versionName = coreLeaf;
            }

            return new DetectionContext
            {
                CoreDir = normalized,
                VersionName = versionName,
                LauncherDir = launcherDir
            };
        }

        public static InstallResult Install(InstallOptions options)
        {
            if (options == null)
            {
                throw new InvalidOperationException(UiText.InvalidOptions);
            }

            string coreDir = NormalizeDir(options.CoreDir);
            if (string.IsNullOrWhiteSpace(coreDir) || !Directory.Exists(coreDir))
            {
                throw new InvalidOperationException(UiText.InvalidCoreDir);
            }

            string versionName = string.IsNullOrWhiteSpace(options.VersionName)
                ? Path.GetFileName(coreDir)
                : options.VersionName.Trim();

            string launcherDir = NormalizeDir(options.LauncherDir);
            if (string.IsNullOrWhiteSpace(launcherDir))
            {
                launcherDir = Detect(coreDir).LauncherDir;
            }
            string launcherExePath = ResolveLauncherExecutablePath(launcherDir);

            string seedTarget = Path.Combine(coreDir, "Potato_Seed.jar");
            string seedAgentArgument = "Potato_Seed.jar";
            string tempTarget = seedTarget + ".download";
            string seedConfigDir = Path.Combine(coreDir, "A_Potato_Seed");
            string seedConfigPath = Path.Combine(seedConfigDir, "seed_config.json");
            string updaterDir = Path.Combine(coreDir, "A_Potato_Updater");

            Directory.CreateDirectory(seedConfigDir);
            Directory.CreateDirectory(updaterDir);

            DownloadSeed(tempTarget);
            DeleteStaleSeedJars(coreDir);
            ReplaceFile(tempTarget, seedTarget);

            EnsureSeedConfig(seedConfigPath);
            SanitizeGlobalPclArgs();

            string versionSetupPath = ResolveVersionSetupPath(coreDir, versionName);
            RepairInstallerTouchedVersionIsolation(versionSetupPath);
            string existingArgs = ReadIniValue(versionSetupPath, "VersionAdvanceJvm");
            string newArgs = BuildVersionAdvanceJvm(existingArgs, seedAgentArgument, options.Mode);
            // "0" maps to the first item ("开启"), which enables version-level isolation
            WriteIniValue(versionSetupPath, "VersionAdvanceJvm", newArgs);

            string versionJsonPath = ResolveVersionJsonPath(coreDir, versionName);
            if (!string.IsNullOrWhiteSpace(versionJsonPath) && File.Exists(versionJsonPath))
            {
                UpdateVersionJson(versionJsonPath, seedAgentArgument);
            }

            DeleteLauncherCacheFiles(launcherDir);

            bool launcherRestarted = false;
            if (options.RestartLauncherAfterInstall)
            {
                launcherRestarted = RestartLauncher(launcherExePath);
            }

            return new InstallResult
            {
                CoreDir = coreDir,
                VersionName = versionName,
                LauncherDir = launcherDir,
                LauncherLeafName = string.IsNullOrWhiteSpace(launcherDir)
                    ? UiText.NotDetected
                    : Path.GetFileName(launcherDir.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar)),
                SeedTarget = seedTarget,
                Mode = options.Mode == InstallMode.Replace ? "replace" : "append",
                LauncherExePath = launcherExePath,
                LauncherRestarted = launcherRestarted
            };
        }

        private static string NormalizeDir(string path)
        {
            return string.IsNullOrWhiteSpace(path) ? string.Empty : Path.GetFullPath(path.Trim());
        }

        private static void DownloadSeed(string outputPath)
        {
            if (File.Exists(outputPath))
            {
                File.Delete(outputPath);
            }

            Exception lastError = null;
            for (int attempt = 1; attempt <= DownloadMaxAttempts; attempt++)
            {
                try
                {
                    DownloadSeedOnce(outputPath);
                    return;
                }
                catch (Exception ex)
                {
                    lastError = ex;
                    TryDeleteFile(outputPath);
                    if (attempt >= DownloadMaxAttempts || !ShouldRetryDownload(ex))
                    {
                        break;
                    }
                    System.Threading.Thread.Sleep(DownloadRetryDelayMs);
                }
            }

            if (lastError != null)
            {
                throw new InvalidOperationException("下载 Potato_Seed.jar 失败，请检查网络后重试。\n" + lastError.Message, lastError);
            }
        }

        private static void DownloadSeedOnce(string outputPath)
        {
            HttpWebRequest request = (HttpWebRequest)WebRequest.Create(DownloadUrl);
            request.Method = "GET";
            request.AllowAutoRedirect = true;
            request.Timeout = DownloadConnectTimeoutMs;
            request.ReadWriteTimeout = DownloadReadWriteTimeoutMs;
            request.AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate;
            request.UserAgent = "Mozilla/5.0 AppleWebKit/537.36 PotatoSeedInstaller/1.0";

            using (HttpWebResponse response = (HttpWebResponse)request.GetResponse())
            {
                if (response.StatusCode != HttpStatusCode.OK)
                {
                    throw new IOException("HTTP " + (int)response.StatusCode + " while downloading Potato_Seed.jar");
                }

                using (Stream input = response.GetResponseStream())
                {
                    if (input == null)
                    {
                        throw new IOException("Installer download returned an empty response stream.");
                    }

                    using (FileStream output = new FileStream(outputPath, FileMode.Create, FileAccess.Write, FileShare.None))
                    {
                        input.CopyTo(output);
                    }
                }
            }

            if (!File.Exists(outputPath))
            {
                throw new IOException("Installer download did not produce a local file.");
            }

            FileInfo info = new FileInfo(outputPath);
            if (info.Length <= 0)
            {
                throw new IOException("Installer download produced an empty Potato_Seed.jar file.");
            }

            ComputeSha256(outputPath);
        }

        private static bool ShouldRetryDownload(Exception ex)
        {
            WebException webException = ex as WebException;
            if (webException != null)
            {
                HttpWebResponse response = webException.Response as HttpWebResponse;
                if (response != null)
                {
                    int statusCode = (int)response.StatusCode;
                    return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500;
                }

                return true;
            }

            IOException ioException = ex as IOException;
            return ioException != null;
        }

        private static void TryDeleteFile(string path)
        {
            try
            {
                if (File.Exists(path))
                {
                    File.Delete(path);
                }
            }
            catch
            {
            }
        }

        private static string ComputeSha256(string path)
        {
            using (SHA256 sha256 = SHA256.Create())
            using (FileStream stream = File.OpenRead(path))
            {
                byte[] hash = sha256.ComputeHash(stream);
                StringBuilder builder = new StringBuilder(hash.Length * 2);
                foreach (byte value in hash)
                {
                    builder.Append(value.ToString("x2"));
                }
                return builder.ToString();
            }
        }

        private static void ReplaceFile(string tempPath, string targetPath)
        {
            if (File.Exists(targetPath))
            {
                File.Delete(targetPath);
            }
            File.Move(tempPath, targetPath);
        }

        private static void DeleteStaleSeedJars(string coreDir)
        {
            foreach (string stalePath in GetStaleSeedJarPaths(coreDir))
            {
                if (File.Exists(stalePath))
                {
                    File.Delete(stalePath);
                }
            }
        }

        private static IEnumerable<string> GetStaleSeedJarPaths(string coreDir)
        {
            yield return Path.Combine(coreDir, "mods", "Potato_Seed.jar");
            yield return Path.Combine(coreDir, "A_Potato_Seed", "Potato_Seed.jar");
            yield return Path.Combine(coreDir, "A_Potato_Updater", "Potato_Seed.jar");
        }

        private static void EnsureSeedConfig(string configPath)
        {
            if (File.Exists(configPath))
            {
                return;
            }

            string json = "{\r\n" +
                          "  \"enableSeed\": true,\r\n" +
                          "  \"enableUpdaterCheck\": true,\r\n" +
                          "  \"remoteConfigUrl\": \"" + BuildEndpoints.SeedConfigUrl + "\",\r\n" +
                          "  \"updaterDirName\": \"A_Potato_Updater\",\r\n" +
                          "  \"updaterJarName\": \"Potato_Updater.jar\"\r\n" +
                          "}";
            File.WriteAllText(configPath, json, new UTF8Encoding(false));
        }

        private static void SanitizeGlobalPclArgs()
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.CreateSubKey(RegistryPath))
                {
                    if (key == null)
                    {
                        return;
                    }

                    string existing = key.GetValue(RegistryValueName) as string;
                    if (existing == null)
                    {
                        return;
                    }

                    key.SetValue(RegistryValueName, RemoveLegacyAgents(existing), RegistryValueKind.String);
                }
            }
            catch
            {
            }
        }

        private static string ResolveVersionSetupPath(string coreDir, string versionName)
        {
            string coreLeaf = Path.GetFileName(coreDir);
            List<string> candidates = new List<string> { Path.Combine(coreDir, "PCL", "Setup.ini") };

            if (coreLeaf.Equals(".minecraft", StringComparison.OrdinalIgnoreCase) &&
                !string.IsNullOrWhiteSpace(versionName) &&
                !versionName.Equals(".minecraft", StringComparison.OrdinalIgnoreCase))
            {
                candidates.Add(Path.Combine(coreDir, "versions", versionName, "PCL", "Setup.ini"));
            }

            foreach (string candidate in candidates)
            {
                if (File.Exists(candidate))
                {
                    return candidate;
                }
            }

            return candidates[0];
        }

        private static string ResolveVersionJsonPath(string coreDir, string versionName)
        {
            string coreLeaf = Path.GetFileName(coreDir);
            List<string> candidates = new List<string>();

            if (!string.IsNullOrWhiteSpace(coreLeaf) && !coreLeaf.Equals(".minecraft", StringComparison.OrdinalIgnoreCase))
            {
                candidates.Add(Path.Combine(coreDir, coreLeaf + ".json"));
            }

            if (coreLeaf.Equals(".minecraft", StringComparison.OrdinalIgnoreCase) &&
                !string.IsNullOrWhiteSpace(versionName) &&
                !versionName.Equals(".minecraft", StringComparison.OrdinalIgnoreCase))
            {
                candidates.Add(Path.Combine(coreDir, "versions", versionName, versionName + ".json"));
            }

            foreach (string candidate in candidates)
            {
                if (File.Exists(candidate))
                {
                    return candidate;
                }
            }

            return candidates.Count > 0 ? candidates[0] : string.Empty;
        }

        private static string ReadIniValue(string path, string key)
        {
            if (string.IsNullOrWhiteSpace(path) || !File.Exists(path))
            {
                return string.Empty;
            }

            foreach (string line in File.ReadAllLines(path, Encoding.UTF8))
            {
                if (line.StartsWith(key + ":", StringComparison.OrdinalIgnoreCase))
                {
                    return line.Substring(key.Length + 1).Trim();
                }
            }

            return string.Empty;
        }

        private static void WriteIniValue(string path, string key, string value)
        {
            List<string> lines = new List<string>();
            if (File.Exists(path))
            {
                lines.AddRange(File.ReadAllLines(path, Encoding.UTF8));
            }

            bool updated = false;
            for (int i = 0; i < lines.Count; i++)
            {
                if (lines[i].StartsWith(key + ":", StringComparison.OrdinalIgnoreCase))
                {
                    lines[i] = key + ":" + value;
                    updated = true;
                }
            }

            if (!updated)
            {
                lines.Add(key + ":" + value);
            }

            string directory = Path.GetDirectoryName(path);
            if (!string.IsNullOrWhiteSpace(directory))
            {
                Directory.CreateDirectory(directory);
            }
            File.WriteAllLines(path, lines.ToArray(), new UTF8Encoding(false));
        }

        private static void DeleteIniValue(string path, string key)
        {
            if (string.IsNullOrWhiteSpace(path) || !File.Exists(path))
            {
                return;
            }

            List<string> lines = new List<string>(File.ReadAllLines(path, Encoding.UTF8));
            bool removed = false;
            for (int i = lines.Count - 1; i >= 0; i--)
            {
                if (lines[i].StartsWith(key + ":", StringComparison.OrdinalIgnoreCase))
                {
                    lines.RemoveAt(i);
                    removed = true;
                }
            }

            if (removed)
            {
                File.WriteAllLines(path, lines.ToArray(), new UTF8Encoding(false));
            }
        }

        private static void RepairInstallerTouchedVersionIsolation(string versionSetupPath)
        {
            string existing = ReadIniValue(versionSetupPath, "VersionArgumentIndieV2");
            if (string.IsNullOrWhiteSpace(existing))
            {
                return;
            }

            string normalized = existing.Trim();
            if (normalized.Equals("True", StringComparison.OrdinalIgnoreCase) ||
                normalized.Equals("0", StringComparison.OrdinalIgnoreCase))
            {
                DeleteIniValue(versionSetupPath, "VersionArgumentIndieV2");
            }
        }

        private static string BuildVersionAdvanceJvm(string existingArgs, string seedAgentArgument, InstallMode mode)
        {
            string required = "-javaagent:" + seedAgentArgument + " -XX:-UseAdaptiveSizePolicy";
            string sanitized = RemoveLegacyAgents(existingArgs ?? string.Empty);
            sanitized = Regex.Replace(sanitized, @"(?i)(^|\s)-XX:-UseAdaptiveSizePolicy(?=\s|$)", " ");
            sanitized = Regex.Replace(sanitized, @"\s+", " ").Trim();

            if (string.IsNullOrWhiteSpace(sanitized))
            {
                return required;
            }

            if (mode == InstallMode.Replace)
            {
                return required + " " + sanitized;
            }

            return sanitized + " " + required;
        }

        private static string RemoveLegacyAgents(string argsText)
        {
            if (string.IsNullOrWhiteSpace(argsText))
            {
                return string.Empty;
            }

            string value = argsText;
            string[] patterns =
            {
                @"(?i)(^|\s)-javaagent:(?:""[^""]*mcpatch[^""]*""|[^""\s]*mcpatch[^""\s]*)(?=\s|$)",
                @"(?i)(^|\s)-javaagent:(?:""[^""]*dynamic[-_]?loader[^""]*""|[^""\s]*dynamic[-_]?loader[^""\s]*)(?=\s|$)",
                @"(?i)(^|\s)-javaagent:(?:""[^""]*Potato_Seed\.jar[^""]*""|[^""\s]*Potato_Seed\.jar)(?=\s|$)"
            };

            foreach (string pattern in patterns)
            {
                value = Regex.Replace(value, pattern, " ");
            }

            return Regex.Replace(value, @"\s+", " ").Trim();
        }

        private static bool IsLegacyAgentArg(string text, string marker)
        {
            if (string.IsNullOrWhiteSpace(text))
            {
                return false;
            }

            string trimmed = text.Trim();
            if (!trimmed.StartsWith("-javaagent:", StringComparison.OrdinalIgnoreCase))
            {
                return false;
            }

            return Regex.IsMatch(trimmed, marker, RegexOptions.IgnoreCase);
        }

        private static void UpdateVersionJson(string path, string seedAgentArgument)
        {
            JavaScriptSerializer serializer = new JavaScriptSerializer
            {
                MaxJsonLength = int.MaxValue,
                RecursionLimit = 512
            };

            Dictionary<string, object> root = serializer.DeserializeObject(File.ReadAllText(path, Encoding.UTF8)) as Dictionary<string, object>;
            if (root == null || !root.ContainsKey("arguments"))
            {
                return;
            }

            Dictionary<string, object> arguments = root["arguments"] as Dictionary<string, object>;
            if (arguments == null || !arguments.ContainsKey("jvm"))
            {
                return;
            }

            object[] jvmArgs = arguments["jvm"] as object[];
            if (jvmArgs == null)
            {
                return;
            }

            string[] required =
            {
                "-javaagent:" + seedAgentArgument,
                "-XX:-UseAdaptiveSizePolicy"
            };
            List<object> updated = new List<object>();
            bool inserted = false;

            foreach (object item in jvmArgs)
            {
                string text = item as string;
                if (text == null)
                {
                    updated.Add(item);
                    continue;
                }

                string trimmed = text.Trim();
                bool isMcpatch = IsLegacyAgentArg(trimmed, "mcpatch");
                bool isDynamicLoader = IsLegacyAgentArg(trimmed, @"dynamic[-_]?loader");
                bool isSeed = IsLegacyAgentArg(trimmed, @"Potato_Seed\.jar");
                bool isAdaptive = trimmed.Equals("-XX:-UseAdaptiveSizePolicy", StringComparison.OrdinalIgnoreCase);
                if (isMcpatch || isDynamicLoader || isSeed || isAdaptive)
                {
                    continue;
                }

                if (!inserted && IsVersionJsonInsertionAnchor(trimmed))
                {
                    updated.AddRange(required);
                    inserted = true;
                }

                updated.Add(item);
            }

            if (!inserted)
            {
                updated.AddRange(required);
            }

            arguments["jvm"] = updated.ToArray();
            File.WriteAllText(path, serializer.Serialize(root), new UTF8Encoding(false));
        }

        private static bool IsVersionJsonInsertionAnchor(string value)
        {
            return value.Equals("-cp", StringComparison.OrdinalIgnoreCase) ||
                   value.StartsWith("-Djava.library.path=", StringComparison.OrdinalIgnoreCase);
        }

        private static void DeleteLauncherCacheFiles(string launcherDir)
        {
            if (string.IsNullOrWhiteSpace(launcherDir) || !Directory.Exists(launcherDir))
            {
                return;
            }

            string[] candidates =
            {
                Path.Combine(launcherDir, "LatestLaunch.bat")
            };

            foreach (string candidate in candidates)
            {
                try
                {
                    if (File.Exists(candidate))
                    {
                        File.Delete(candidate);
                    }
                }
                catch
                {
                }
            }
        }

        private static string ResolveLauncherExecutablePath(string launcherDir)
        {
            foreach (string candidateDir in GetLauncherSearchDirs(launcherDir))
            {
                if (string.IsNullOrWhiteSpace(candidateDir) || !Directory.Exists(candidateDir))
                {
                    continue;
                }

                string[] preferredNames =
                {
                    "HokubuCraft.exe",
                    "Plain Craft Launcher 2.exe",
                    "PCL.exe"
                };

                foreach (string preferred in preferredNames)
                {
                    string preferredPath = Path.Combine(candidateDir, preferred);
                    if (File.Exists(preferredPath))
                    {
                        return preferredPath;
                    }
                }

                foreach (string exePath in Directory.GetFiles(candidateDir, "*.exe", SearchOption.TopDirectoryOnly))
                {
                    string fileName = Path.GetFileName(exePath);
                    if (fileName.Equals("Potato_Seed_Installer.exe", StringComparison.OrdinalIgnoreCase))
                    {
                        continue;
                    }
                    return exePath;
                }
            }

            return string.Empty;
        }

        private static IEnumerable<string> GetLauncherSearchDirs(string launcherDir)
        {
            List<string> dirs = new List<string>();
            if (!string.IsNullOrWhiteSpace(launcherDir))
            {
                dirs.Add(launcherDir);

                string leaf = Path.GetFileName(launcherDir.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar));
                if (leaf.Equals("PCL", StringComparison.OrdinalIgnoreCase))
                {
                    string parent = Path.GetDirectoryName(launcherDir);
                    if (!string.IsNullOrWhiteSpace(parent))
                    {
                        dirs.Add(parent);
                    }
                }
                else
                {
                    string childPcl = Path.Combine(launcherDir, "PCL");
                    if (Directory.Exists(childPcl))
                    {
                        dirs.Add(childPcl);
                    }
                }
            }
            return dirs;
        }

        private static bool RestartLauncher(string launcherExePath)
        {
            if (string.IsNullOrWhiteSpace(launcherExePath) || !File.Exists(launcherExePath))
            {
                return false;
            }

            string normalizedExe = Path.GetFullPath(launcherExePath);
            foreach (Process process in Process.GetProcesses())
            {
                try
                {
                    string processPath = process.MainModule == null ? string.Empty : process.MainModule.FileName;
                    if (string.IsNullOrWhiteSpace(processPath))
                    {
                        continue;
                    }

                    if (!string.Equals(Path.GetFullPath(processPath), normalizedExe, StringComparison.OrdinalIgnoreCase))
                    {
                        continue;
                    }

                    if (!process.HasExited)
                    {
                        try
                        {
                            process.CloseMainWindow();
                        }
                        catch
                        {
                        }

                        if (!process.WaitForExit(5000))
                        {
                            process.Kill();
                            process.WaitForExit(5000);
                        }
                    }
                }
                catch
                {
                }
                finally
                {
                    process.Dispose();
                }
            }

            Process.Start(new ProcessStartInfo
            {
                FileName = normalizedExe,
                WorkingDirectory = Path.GetDirectoryName(normalizedExe) ?? string.Empty,
                UseShellExecute = true
            });
            return true;
        }
    }
}
