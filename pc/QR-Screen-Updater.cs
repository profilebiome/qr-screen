using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;

namespace QrScreenUpdater
{
    /// <summary>
    /// Actualizador de QR Screen para Windows: detecta el teléfono por ADB,
    /// compara la versión instalada con la publicada en GitHub y la instala.
    /// </summary>
    public class MainForm : Form
    {
        const string VersionUrl = "https://profilebiome.github.io/qr-screen/version.json";
        const string Package = "com.drako.qrscreen";
        const string PlatformToolsUrl = "https://developer.android.com/studio/releases/platform-tools";

        static readonly Color Ink = Color.FromArgb(6, 7, 6);
        static readonly Color Surface = Color.FromArgb(12, 22, 18);
        static readonly Color Mint = Color.FromArgb(52, 211, 153);
        static readonly Color Paper = Color.FromArgb(242, 246, 243);
        static readonly Color Muted = Color.FromArgb(154, 172, 162);

        TextBox log;
        Label deviceLabel;
        Label installedLabel;
        Label latestLabel;
        Button installButton;
        Button checkButton;
        Button adbButton;
        string adbPath;
        string serial;
        int installedCode = -1;
        string installedName = "-";
        int latestCode = -1;
        string latestName = "-";
        string latestApk;
        string latestSha;

        [STAThread]
        public static void Main(string[] args)
        {
            if (args.Length >= 2 && args[0] == "--selftest")
            {
                SelfTest(args[1]);
                return;
            }
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }

        public MainForm()
        {
            Text = "QR Screen Updater";
            ClientSize = new Size(760, 560);
            MinimumSize = new Size(700, 520);
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Ink;
            ForeColor = Paper;
            Font = new Font("Segoe UI", 9.5f);
            try { this.Icon = System.Drawing.Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }

            Label title = new Label();
            title.Text = "QR Screen Updater";
            title.Font = new Font("Segoe UI", 17f, FontStyle.Bold);
            title.ForeColor = Paper;
            title.AutoSize = true;
            title.Location = new Point(24, 20);
            Controls.Add(title);

            Label subtitle = new Label();
            subtitle.Text = "Instala o actualiza QR Screen en el teléfono conectado por USB";
            subtitle.ForeColor = Muted;
            subtitle.AutoSize = true;
            subtitle.Location = new Point(26, 58);
            Controls.Add(subtitle);

            deviceLabel = MakeInfo(26, 96, "Teléfono: comprobando…");
            installedLabel = MakeInfo(26, 118, "Instalada: -");
            latestLabel = MakeInfo(26, 140, "Disponible: -");

            checkButton = MakeButton("Comprobar", 24, 178, 130, false);
            checkButton.Click += delegate { RefreshAll(false); };

            installButton = MakeButton("Instalar / actualizar", 166, 178, 190, true);
            installButton.Enabled = false;
            installButton.Click += delegate { InstallUpdate(); };

            adbButton = MakeButton("Buscar adb…", 368, 178, 120, false);
            adbButton.Click += delegate { ChooseAdb(); };

            Button adbHelp = MakeButton("Descargar platform-tools", 500, 178, 236, false);
            adbHelp.Click += delegate { OpenUrl(PlatformToolsUrl); };

            log = new TextBox();
            log.Multiline = true;
            log.ReadOnly = true;
            log.ScrollBars = ScrollBars.Vertical;
            log.BackColor = Surface;
            log.ForeColor = Paper;
            log.BorderStyle = BorderStyle.None;
            log.Font = new Font("Consolas", 9f);
            log.Location = new Point(26, 232);
            log.Size = new Size(708, 300);
            log.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Bottom;
            Controls.Add(log);

            Load += delegate { RefreshAll(true); };
        }

        Label MakeInfo(int x, int y, string text)
        {
            Label l = new Label();
            l.Text = text;
            l.ForeColor = Muted;
            l.AutoSize = true;
            l.Location = new Point(x, y);
            Controls.Add(l);
            return l;
        }

        Button MakeButton(string text, int x, int y, int w, bool primary)
        {
            Button b = new Button();
            b.Text = text;
            b.Location = new Point(x, y);
            b.Size = new Size(w, 38);
            b.FlatStyle = FlatStyle.Flat;
            b.FlatAppearance.BorderColor = primary ? Mint : Color.FromArgb(21, 60, 46);
            b.BackColor = primary ? Mint : Surface;
            b.ForeColor = primary ? Color.FromArgb(4, 20, 14) : Paper;
            b.Font = new Font("Segoe UI", 9.5f, primary ? FontStyle.Bold : FontStyle.Regular);
            b.Cursor = Cursors.Hand;
            Controls.Add(b);
            return b;
        }

        void Log(string line)
        {
            if (InvokeRequired)
            {
                BeginInvoke(new Action<string>(Log), line);
                return;
            }
            log.AppendText(DateTime.Now.ToString("HH:mm:ss") + "  " + line + Environment.NewLine);
        }

        void RefreshAll(bool initial)
        {
            checkButton.Enabled = false;
            installButton.Enabled = false;
            new Thread(delegate()
            {
                try
                {
                    if (adbPath == null || !File.Exists(adbPath))
                    {
                        adbPath = FindAdb();
                    }
                    if (adbPath == null)
                    {
                        SetText(deviceLabel, "Teléfono: falta adb");
                        Log("No se encontró adb.exe. Descarga platform-tools o usa «Buscar adb…».");
                    }
                    else
                    {
                        Log("adb: " + adbPath);
                        serial = FirstDevice();
                        if (serial == null)
                        {
                            SetText(deviceLabel, "Teléfono: no conectado");
                            installedName = "-";
                            installedCode = -1;
                            Log("No hay dispositivos. Activa Depuración USB y acepta el aviso del teléfono.");
                        }
                        else
                        {
                            SetText(deviceLabel, "Teléfono: " + serial);
                            ReadInstalled();
                        }
                    }
                    FetchLatest();
                    bool update = latestCode > installedCode && installedCode > 0;
                    SetEnabled(installButton, update);
                    if (update)
                    {
                        Log("Actualización disponible: " + latestName + " (tienes " + installedName + ")");
                    }
                    else if (latestCode > 0 && installedCode > 0)
                    {
                        Log("Todo al día.");
                    }
                }
                catch (Exception ex)
                {
                    Log("Error: " + ex.Message);
                }
                finally
                {
                    SetEnabled(checkButton, true);
                }
            }).Start();
        }

        void SetEnabled(Button button, bool enabled)
        {
            if (InvokeRequired)
            {
                BeginInvoke(new Action<Button, bool>(SetEnabled), button, enabled);
                return;
            }
            button.Enabled = enabled;
        }

        void SetText(Label label, string text)
        {
            if (InvokeRequired)
            {
                BeginInvoke(new Action<Label, string>(SetText), label, text);
                return;
            }
            label.Text = text;
        }

        void ReadInstalled()
        {
            string output = RunAdb("-s " + serial + " shell dumpsys package " + Package);
            Match name = Regex.Match(output, @"versionName=(\S+)");
            Match code = Regex.Match(output, @"versionCode=(\d+)");
            if (name.Success)
            {
                installedName = name.Groups[1].Value;
                installedCode = code.Success ? int.Parse(code.Groups[1].Value) : 0;
                SetText(installedLabel, "Instalada: " + installedName + " (" + installedCode + ")");
                Log("Instalada en el teléfono: " + installedName);
            }
            else
            {
                installedName = "-";
                installedCode = 0;
                SetText(installedLabel, "Instalada: no está instalada");
                Log("QR Screen no está instalado en el teléfono.");
            }
        }

        void FetchLatest()
        {
            try
            {
                ServicePointManager.SecurityProtocol |= (SecurityProtocolType)3072;
                using (WebClient client = new WebClient())
                {
                    client.Headers["User-Agent"] = "QR-Screen-Updater";
                    string json = client.DownloadString(VersionUrl);
                    Match code = Regex.Match(json, "\"versionCode\"\\s*:\\s*(\\d+)");
                    Match name = Regex.Match(json, "\"versionName\"\\s*:\\s*\"([^\"]+)\"");
                    Match apk = Regex.Match(json, "\"apk\"\\s*:\\s*\"([^\"]+)\"");
                    Match sha = Regex.Match(json, "\"sha256\"\\s*:\\s*\"([^\"]+)\"");
                    if (!code.Success || !name.Success || !apk.Success)
                    {
                        Log("version.json no tiene el formato esperado.");
                        return;
                    }
                    latestCode = int.Parse(code.Groups[1].Value);
                    latestName = name.Groups[1].Value;
                    latestApk = apk.Groups[1].Value;
                    latestSha = sha.Success ? sha.Groups[1].Value : "";
                    SetText(latestLabel, "Disponible: " + latestName + " (" + latestCode + ")");
                    Log("Publicada en GitHub: " + latestName);
                }
            }
            catch (Exception ex)
            {
                Log("No se pudo consultar GitHub: " + ex.Message);
            }
        }

        void InstallUpdate()
        {
            if (serial == null || latestApk == null)
            {
                MessageBox.Show("Conecta el teléfono y comprueba de nuevo.", "QR Screen Updater");
                return;
            }
            if (installedCode > 0 && latestCode <= installedCode)
            {
                MessageBox.Show("Ya tienes la última versión (" + installedName + ").", "QR Screen Updater");
                return;
            }

            string answer = null;
            using (Form prompt = MakePrompt())
            {
                prompt.ShowDialog(this);
                answer = prompt.Tag as string;
            }
            if (answer != "ok")
            {
                return;
            }

            installButton.Enabled = false;
            new Thread(delegate()
            {
                try
                {
                    string dir = Path.Combine(Path.GetTempPath(), "qr-screen-updater");
                    Directory.CreateDirectory(dir);
                    string file = Path.Combine(dir, "QR-Screen-" + latestName + ".apk");
                    Log("Descargando " + latestName + "…");
                    using (WebClient client = new WebClient())
                    {
                        client.Headers["User-Agent"] = "QR-Screen-Updater";
                        client.DownloadFile(latestApk, file);
                    }
                    string hash = Sha256(file);
                    Log("SHA-256 descargado: " + hash);
                    if (latestSha.Length > 0 && !hash.Equals(latestSha, StringComparison.OrdinalIgnoreCase))
                    {
                        File.Delete(file);
                        Log("El archivo no coincide con el SHA-256 publicado. Instalación cancelada.");
                        BeginInvoke(new Action(delegate { installButton.Enabled = true; }));
                        return;
                    }
                    Log("Instalando en " + serial + "… (puede tardar unos segundos)");
                    string output = RunAdb("-s " + serial + " install -r \"" + file + "\"");
                    Log(output.Trim());
                    if (output.Contains("Success"))
                    {
                        Log("Instalado. Comprobando de nuevo…");
                        RefreshAll(false);
                    }
                    else if (output.Contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") || output.Contains("signatures do not match"))
                    {
                        Log("La firma no coincide con la versión instalada. Desinstala QR Screen en el teléfono y vuelve a intentarlo.");
                    }
                    else
                    {
                        Log("No se pudo instalar. Revisa el mensaje de adb de arriba.");
                        BeginInvoke(new Action(delegate { installButton.Enabled = true; }));
                    }
                }
                catch (Exception ex)
                {
                    Log("Error al instalar: " + ex.Message);
                    BeginInvoke(new Action(delegate { installButton.Enabled = true; }));
                }
            }).Start();
        }

        Form MakePrompt()
        {
            Form f = new Form();
            f.Text = "Actualizar QR Screen";
            f.ClientSize = new Size(420, 170);
            f.FormBorderStyle = FormBorderStyle.FixedDialog;
            f.StartPosition = FormStartPosition.CenterParent;
            f.MaximizeBox = false;
            f.MinimizeBox = false;
            f.BackColor = Ink;
            f.ForeColor = Paper;

            Label text = new Label();
            text.Text = "Se va a instalar " + latestName + " en el teléfono conectado.\n"
                + "La app y sus códigos guardados se conservan.";
            text.ForeColor = Paper;
            text.Location = new Point(20, 20);
            text.Size = new Size(380, 60);
            f.Controls.Add(text);

            Button ok = MakeButton("Actualizar ahora", 20, 100, 190, true);
            ok.Parent = f;
            ok.Click += delegate { f.Tag = "ok"; f.Close(); };

            Button cancel = MakeButton("Cancelar", 220, 100, 180, false);
            cancel.Parent = f;
            cancel.Click += delegate { f.Close(); };
            return f;
        }

        void ChooseAdb()
        {
            using (OpenFileDialog dialog = new OpenFileDialog())
            {
                dialog.Title = "Selecciona adb.exe";
                dialog.Filter = "adb (adb.exe)|adb.exe|Ejecutables (*.exe)|*.exe";
                if (dialog.ShowDialog(this) == DialogResult.OK)
                {
                    adbPath = dialog.FileName;
                    Log("adb: " + adbPath);
                    RefreshAll(false);
                }
            }
        }

        string FindAdb()
        {
            string[] candidates = new string[]
            {
                Path.Combine(Path.GetDirectoryName(Application.ExecutablePath), "adb.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), @"AppData\Local\Android\Sdk\platform-tools\adb.exe"),
                @"C:\dev\android-sdk\platform-tools\adb.exe",
                @"C:\Android\platform-tools\adb.exe",
                @"C:\platform-tools\adb.exe",
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), @"Android\platform-tools\adb.exe")
            };
            foreach (string candidate in candidates)
            {
                try
                {
                    if (File.Exists(candidate))
                    {
                        return candidate;
                    }
                }
                catch { }
            }
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo("where", "adb");
                psi.RedirectStandardOutput = true;
                psi.UseShellExecute = false;
                psi.CreateNoWindow = true;
                using (Process p = Process.Start(psi))
                {
                    string line = p.StandardOutput.ReadLine();
                    p.WaitForExit(4000);
                    if (!string.IsNullOrEmpty(line) && File.Exists(line.Trim()))
                    {
                        return line.Trim();
                    }
                }
            }
            catch { }
            return null;
        }

        string FirstDevice()
        {
            string output = RunAdb("devices");
            foreach (string raw in output.Split('\n'))
            {
                string line = raw.Trim();
                if (line.EndsWith("\tdevice") || line.EndsWith(" device"))
                {
                    int tab = line.IndexOf('\t');
                    int space = line.IndexOf(' ');
                    int cut = tab >= 0 ? tab : space;
                    if (cut > 0)
                    {
                        return line.Substring(0, cut).Trim();
                    }
                }
            }
            return null;
        }

        string RunAdb(string arguments)
        {
            if (adbPath == null)
            {
                return "";
            }
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo(adbPath, arguments);
                psi.RedirectStandardOutput = true;
                psi.RedirectStandardError = true;
                psi.UseShellExecute = false;
                psi.CreateNoWindow = true;
                using (Process p = Process.Start(psi))
                {
                    string standard = p.StandardOutput.ReadToEnd();
                    string error = p.StandardError.ReadToEnd();
                    if (!p.WaitForExit(60000))
                    {
                        try { p.Kill(); } catch { }
                        return standard + "\n(tiempo de espera agotado)";
                    }
                    if (error.Trim().Length > 0)
                    {
                        return standard + "\n" + error;
                    }
                    return standard;
                }
            }
            catch (Exception ex)
            {
                return "Error ejecutando adb: " + ex.Message;
            }
        }

        string Sha256(string file)
        {
            using (SHA256 sha = SHA256.Create())
            using (FileStream stream = File.OpenRead(file))
            {
                byte[] bytes = sha.ComputeHash(stream);
                StringBuilder sb = new StringBuilder(bytes.Length * 2);
                foreach (byte b in bytes)
                {
                    sb.Append(b.ToString("x2"));
                }
                return sb.ToString();
            }
        }

        void OpenUrl(string url)
        {
            try { Process.Start(new ProcessStartInfo(url) { UseShellExecute = true }); }
            catch (Exception ex) { Log("No se pudo abrir el navegador: " + ex.Message); }
        }

        /// <summary>Diagnóstico sin ventana: escribe el resultado en un archivo.</summary>
        static void SelfTest(string outFile)
        {
            StringBuilder sb = new StringBuilder();
            try
            {
                MainForm form = new MainForm();
                form.adbPath = form.FindAdb();
                sb.AppendLine("adb=" + (form.adbPath ?? "not found"));
                if (form.adbPath != null)
                {
                    form.serial = form.FirstDevice();
                    sb.AppendLine("device=" + (form.serial ?? "none"));
                    if (form.serial != null)
                    {
                        form.ReadInstalled();
                        sb.AppendLine("installed=" + form.installedName + " (" + form.installedCode + ")");
                    }
                }
                form.FetchLatest();
                sb.AppendLine("latest=" + form.latestName + " (" + form.latestCode + ")");
                sb.AppendLine("apk=" + (form.latestApk ?? "none"));
                sb.AppendLine("sha256=" + (form.latestSha ?? ""));
            }
            catch (Exception ex)
            {
                sb.AppendLine("error=" + ex.Message);
            }
            File.WriteAllText(outFile, sb.ToString());
        }
    }
}
