package com.github.manevolent.ts3j.client;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Small, dependency-free translation table for the JavaFX shell. */
final class UiText {
    private static final Map<String, String[]> TEXT = new HashMap<>();

    static {
        put("server", "TeamSpeak", "TeamSpeak", "TeamSpeak");
        put("server.description", "TeamSpeak server", "Servidor TeamSpeak", "TeamSpeak 服务器");
        put("add.connection", "Add connection", "Añadir conexión", "添加连接");
        put("theme.toggle", "Switch light/dark theme", "Cambiar tema claro/oscuro", "切换明暗主题");
        put("preferences", "Preferences", "Preferencias", "偏好设置");
        put("channels", "CHANNELS", "CANALES", "频道");
        put("text.header", "TEXT", "TEXTO", "文本");
        put("voice.header", "VOICE", "VOZ", "语音");
        put("no.text.channels", "No text channels", "Sin canales de texto", "没有文字频道");
        put("no.voice.channels", "No voice channels", "Sin canales de voz", "没有语音频道");
        put("voice.channel", "Voice channel", "Canal de voz", "语音频道");
        put("text.channel", "Text channel", "Canal de texto", "文字频道");
        put("channel.tooltip", "%s · %s", "%s · %s", "%s · %s");
        put("connect", "Connect", "Conectar", "连接");
        put("connect.server", "Connect to a TeamSpeak server", "Conectar a un servidor TeamSpeak", "连接到 TeamSpeak 服务器");
        put("disconnect", "Disconnect", "Desconectar", "断开连接");
        put("leave", "Leave", "Salir", "离开");
        put("voice.time", "Voice time", "Tiempo en voz", "语音时间");
        put("status.disconnected", "Disconnected", "Desconectado", "未连接");
        put("status.disconnected.detail", "Connect a server to view its channels", "Conecta un servidor para ver sus canales", "连接服务器以查看频道");
        put("status.connecting", "Connecting", "Conectando", "正在连接");
        put("status.connected.active", "Connected · active channel", "Conectado · canal activo", "已连接 · 活跃频道");
        put("status.connected.none", "Connected · no channel", "Conectado · sin canal", "已连接 · 无频道");
        put("status.error", "Connection error", "Error de conexión", "连接错误");
        put("connection.limited", "Limited channel view: this identity cannot list every channel. Visible channels and live events remain available; use an authorized TeamSpeak identity to see the rest.", "Vista limitada de canales: esta identidad no puede enumerar todos los canales. Los canales visibles y los eventos en vivo siguen disponibles; usa una identidad autorizada de TeamSpeak para ver el resto.", "频道视图受限：此身份无法列出所有频道。仍可使用可见频道和实时事件；使用有权限的 TeamSpeak 身份可查看其余频道。");
        put("channels.count", "%s · %d channels", "%s · %d canales", "%s · %d 个频道");
        put("server.none", "No server", "Sin servidor", "无服务器");
        put("connecting", "Connecting to the server…", "Conectando al servidor…", "正在连接服务器…");
        put("voice.shared", "The session is shared by everyone present.", "La sesión es compartida por todas las personas presentes.", "此会话由频道中的所有用户共享。");
        put("text.rule", "Channel messages · kept locally between connections.", "Mensajes del canal · se conservan localmente entre conexiones.", "频道消息 · 在连接之间保存在本机。");
        put("text.compose", "Write a message…", "Escribe un mensaje…", "输入消息…");
        put("text.compose.help", "Message to send to this channel.", "Mensaje que se enviará al chat de este canal.", "要发送到此频道的消息。");
        put("text.voiceless.rule", "Channel text is available when the server allows it.", "El texto del canal se integra cuando el servidor lo permite.", "服务器允许时可使用频道文字。");
        put("text.send.tooltip", "Send a message to this channel (Enter)", "Enviar un mensaje al canal (Enter)", "发送频道消息（Enter）");
        put("text.send", "Send message to this channel", "Enviar mensaje al canal", "发送频道消息");
        put("chat.empty", "No messages in this channel yet.", "No hay mensajes en este canal todavía.", "此频道还没有消息。");
        put("chat.history.end", "*** End of chat history", "*** Fin del historial del chat", "*** 聊天记录结束");
        put("chat.avatar", "Avatar of %s", "Avatar de %s", "%s 的头像");
        put("chat.user.time", "%s at %s", "%s a las %s", "%s · %s");
        put("chat.message.accessible", "%s at %s: %s", "%s a las %s: %s", "%s · %s：%s");
        put("chat.day.today", "Today", "Hoy", "今天");
        put("chat.day.yesterday", "Yesterday", "Ayer", "昨天");
        put("people", "PEOPLE · %d", "PERSONAS · %d", "用户 · %d");
        put("select.voice", "Select a voice channel to see people and shared time.", "Selecciona un canal de voz para ver personas y tiempo compartido.", "选择语音频道以查看用户和共享时间。");
        put("empty.disconnected.title", "No connection", "Sin conexión", "未连接");
        put("empty.disconnected.body", "Connect a server to load channels and shared sessions.", "Conecta un servidor para cargar canales y sesiones compartidas.", "连接服务器以加载频道和共享会话。");
        put("empty.nochannel.title", "Connected without a channel", "Conectado sin canal", "已连接但没有频道");
        put("empty.nochannel.body", "Join a voice channel to show its timer.", "Entra en un canal de voz para mostrar su temporizador.", "加入语音频道以显示计时器。");
        put("empty.channel", "Channel is empty.", "Canal vacío", "频道为空");
        put("user.accessible", "User %s. Right-click to configure their volume.", "Usuario %s. Clic derecho para configurar su volumen.", "用户 %s。右键配置音量。");
        put("mic.off", "mic muted", "mic apagado", "麦克风已静音");
        put("mic.tooltip", "Microphone muted in the TeamSpeak state", "Micrófono silenciado en el estado de TeamSpeak", "TeamSpeak 状态中的麦克风已静音");
        put("away", "away", "ausente", "离开");
        put("away.tooltip", "TeamSpeak away status", "Estado ausente de TeamSpeak", "TeamSpeak 离开状态");
        put("audio.off", "audio muted", "audio apagado", "音频已静音");
        put("audio.tooltip", "Audio muted in the TeamSpeak state", "Audio silenciado en el estado de TeamSpeak", "TeamSpeak 状态中的音频已静音");
        put("volume.tooltip", "Local volume adjustment for %s", "Ajuste de volumen local para %s", "%s 的本地音量调整");
        put("volume.change", "Change volume…", "Cambiar volumen…", "更改音量…");
        put("volume.reset.menu", "Reset volume", "Restablecer volumen", "重置音量");
        put("volume.title", "Volume - %s", "Volumen - %s", "音量 - %s");
        put("volume.header", "Local playback adjustment", "Ajuste local de reproducción", "本地播放调整");
        put("volume.accessible", "Individual volume for %s", "Volumen individual de %s", "%s 的个人音量");
        put("volume.field", "Volume", "Volumen", "音量");
        put("volume.restore", "Reset", "Restablecer", "重置");
        put("volume.restore.tooltip", "Return to 0.0 dB", "Volver a 0.0 dB", "恢复为 0.0 dB");
        put("volume.help", "Positive values make the voice louder; negative values make it quieter.\nThis setting is saved only on this computer.", "Los valores positivos aumentan la voz; los negativos la reducen.\nEl ajuste se guarda solo en este equipo.", "正值提高音量，负值降低音量。\n此设置仅保存在本机。");
        put("away.available", "Mark yourself away", "Marcarse como ausente", "标记为离开");
        put("away.active", "Return to available", "Volver al estado disponible", "恢复在线状态");
        put("mic.available", "Mute microphone", "Silenciar micrófono", "静音麦克风");
        put("mic.active", "Enable microphone", "Activar micrófono", "启用麦克风");
        put("audio.available", "Mute speakers", "Silenciar altavoces", "静音扬声器");
        put("audio.active", "Enable speakers", "Activar altavoces", "启用扬声器");
        put("connection.title", "Connect to TeamSpeak", "Conectar a TeamSpeak", "连接到 TeamSpeak");
        put("connection.header", "Secure server connection", "Conexión segura al servidor", "安全服务器连接");
        put("connection.host", "Host", "Host", "主机");
        put("connection.port", "Port (optional)", "Puerto (opcional)", "端口（可选）");
        put("connection.nickname", "Nickname", "Apodo", "昵称");
        put("connection.password", "Password", "Contraseña", "密码");
        put("connection.state", "Shared state", "Estado compartido", "共享状态");
        put("connection.host.prompt", "192.168.196.65 or server name", "192.168.196.65 o nombre del servidor", "192.168.196.65 或服务器名称");
        put("connection.port.prompt", "Automatic (9987)", "Automático (9987)", "自动（9987）");
        put("connection.port.tooltip", "Optional. Leave empty to use the standard 9987 port.", "Opcional. Déjalo vacío para usar el puerto estándar 9987.", "可选。留空使用标准 9987 端口。");
        put("connection.port.help", "Optional. Leave empty to use TeamSpeak 3's standard port: 9987.", "Opcional. Déjalo vacío para usar el puerto estándar de TeamSpeak 3: 9987.", "可选。留空使用 TeamSpeak 3 标准端口：9987。");
        put("connection.host.help", "TeamSpeak server IP address or name.", "Dirección IP o nombre del servidor de TeamSpeak.", "TeamSpeak 服务器 IP 地址或名称。");
        put("connection.nickname.help", "Name other users will see.", "Nombre que verán los demás usuarios.", "其他用户看到的名称。");
        put("connection.password.help", "Server password, if required.", "Contraseña del servidor, si la requiere.", "服务器密码（如需要）。");
        put("connection.state.help", "Shared local file that stores each voice-session start.", "Archivo local compartido donde se conserva el inicio de cada sesión de voz.", "保存语音会话开始时间的共享本地文件。");
        put("connection.state.required", "Enter a shared state file", "Indica un archivo de estado compartido", "请输入共享状态文件");
        put("dialog.cancel", "Cancel", "Cancelar", "取消");
        put("dialog.ok", "OK", "Aceptar", "确定");
        put("invalid.data", "Invalid data", "Datos no válidos", "数据无效");
        put("settings.title", "Preferences", "Preferencias", "偏好设置");
        put("settings.header", "Startup, tray and language", "Inicio, bandeja e idioma", "启动、托盘和语言");
        put("settings.startup", "Start with Windows and open in the tray", "Iniciar con Windows y abrir en la bandeja", "随 Windows 启动并打开到托盘");
        put("settings.startup.accessible", "Start ts3j-client with Windows and leave it in the tray", "Iniciar ts3j-client con Windows y dejarlo en la bandeja", "随 Windows 启动 ts3j-client 并留在托盘");
        put("settings.startup.help", "Startup uses the current user's Startup folder and needs no administrator permissions.", "El inicio automático usa la carpeta de inicio del usuario y no requiere permisos de administrador.", "启动项使用当前用户的启动文件夹，无需管理员权限。");
        put("settings.startup.macos", "Start with macOS and open in the menu bar", "Iniciar con macOS y abrir en la barra de menús", "随 macOS 启动并打开到菜单栏");
        put("settings.startup.accessible.macos", "Start ts3j-client when you log in to macOS and leave it in the menu bar", "Iniciar ts3j-client al entrar en macOS y dejarlo en la barra de menús", "登录 macOS 时启动 ts3j-client 并留在菜单栏");
        put("settings.startup.help.macos", "Startup uses the current user's LaunchAgents folder and needs no administrator permissions.", "El inicio automático usa la carpeta LaunchAgents del usuario actual y no requiere permisos de administrador.", "启动项使用当前用户的 LaunchAgents 文件夹，无需管理员权限。");
        put("settings.close.tray", "Minimize to the tray when closing the window", "Al cerrar la ventana, minimizar a la bandeja", "关闭窗口时最小化到托盘");
        put("settings.close.tray.accessible", "Minimize to the tray when closing the window", "Minimizar a la bandeja al cerrar la ventana", "关闭窗口时最小化到托盘");
        put("settings.tray.available", "You can restore the window from the ts3j-client tray icon.", "Puedes recuperar la ventana desde el icono de ts3j-client en la bandeja.", "可以从 ts3j-client 托盘图标恢复窗口。");
        put("settings.tray.unavailable", "The system tray is not available in this environment.", "La bandeja del sistema no está disponible en este entorno.", "当前环境不支持系统托盘。");
        put("settings.close.tray.macos", "Keep running in the menu bar when closing the window", "Al cerrar la ventana, mantener la app en la barra de menús", "关闭窗口时继续在菜单栏中运行");
        put("settings.close.tray.accessible.macos", "Keep ts3j-client running in the macOS menu bar when closing the window", "Mantener ts3j-client en la barra de menús de macOS al cerrar la ventana", "关闭窗口时让 ts3j-client 继续在 macOS 菜单栏中运行");
        put("settings.tray.available.macos", "You can restore the window from the ts3j-client menu bar icon.", "Puedes recuperar la ventana desde el icono de ts3j-client en la barra de menús.", "可以从 ts3j-client 菜单栏图标恢复窗口。");
        put("settings.tray.unavailable.macos", "The macOS menu bar icon is not available in this environment.", "El icono de la barra de menús de macOS no está disponible en este entorno.", "当前环境不支持 macOS 菜单栏图标。");
        put("settings.language", "Language", "Idioma", "语言");
        put("settings.language.help", "Choose the interface language. The change is saved for the next launch.", "Elige el idioma de la interfaz. Se guarda para el próximo inicio.", "选择界面语言。设置会保存并在下次启动时使用。");
        put("settings.voice.notifications", "Voice notifications", "Avisos de voz", "语音通知");
        put("settings.voice.help", "Announce TeamSpeak actions through your speakers. Uses Windows speech with bundled app-owned cues as a fallback.", "Anuncia las acciones de TeamSpeak por los altavoces. Usa la voz de Windows y sonidos propios incluidos como respaldo.", "通过扬声器播报 TeamSpeak 操作。使用 Windows 语音，并以内置的应用提示音作为备用。");
        put("settings.voice.help.macos", "Announce TeamSpeak actions through your speakers. Uses macOS speech with bundled app-owned cues as a fallback.", "Anuncia las acciones de TeamSpeak por los altavoces. Usa la voz de macOS y sonidos propios incluidos como respaldo.", "通过扬声器播报 TeamSpeak 操作。使用 macOS 语音，并以内置的应用提示音作为备用。");
        put("settings.voice.volume", "Alert volume", "Volumen de avisos", "提醒音量");
        put("settings.voice.volume.help", "Controls the volume of spoken announcements and bundled cues. Saved automatically.", "Controla el volumen de la voz y de los sonidos incluidos. Se guarda automáticamente.", "控制语音播报和内置提示音的音量。设置会自动保存。");
        put("settings.theme", "Theme", "Tema", "主题");
        put("settings.theme.help", "The current light/dark theme is saved automatically.", "El tema claro/oscuro actual se guarda automáticamente.", "当前明暗主题会自动保存。");
        put("settings.version", "Version %s", "Versión %s", "版本 %s");
        put("settings.update", "Application updates", "Actualizaciones de la aplicación", "应用更新");
        put("settings.update.help", "Check the public GitHub release for a newer installer.", "Comprueba el release público de GitHub para buscar un instalador más reciente.", "检查 GitHub 公开发布中是否有新的安装程序。");
        put("settings.update.check", "Check for updates", "Buscar actualizaciones", "检查更新");
        put("settings.update.check.accessible", "Check GitHub for a newer ts3j-client version", "Comprobar en GitHub si hay una versión nueva de ts3j-client", "在 GitHub 检查新的 ts3j-client 版本");
        put("settings.update.checking", "Checking GitHub for updates…", "Comprobando actualizaciones en GitHub…", "正在 GitHub 检查更新…");
        put("settings.update.latest", "You already have the latest version (%s).", "Ya tienes instalada la última versión (%s).", "你已经安装了最新版本（%s）。");
        put("settings.update.available", "Version %s is available. Downloading…", "La versión %s está disponible. Descargando…", "版本 %s 可用。正在下载…");
        put("settings.update.downloading", "Downloading version %s · %d%%", "Descargando la versión %s · %d%%", "正在下载版本 %s · %d%%");
        put("settings.update.progress", "Update download progress", "Progreso de descarga de la actualización", "更新下载进度");
        put("settings.update.ready", "Update downloaded. Restarting with the installer…", "Actualización descargada. Reiniciando con el instalador…", "更新已下载。正在使用安装程序重启…");
        put("settings.update.ready.macos", "Update downloaded. Opening the macOS installer…", "Actualización descargada. Abriendo el instalador de macOS…", "更新已下载。正在打开 macOS 安装程序…");
        put("settings.update.error", "Update failed: %s", "No se pudo actualizar: %s", "更新失败：%s");
        put("info.startup.error", "Could not update startup", "No se pudo actualizar el inicio", "无法更新启动设置");
        put("info.installed.required", "This option requires the installed version of ts3j-client.", "Esta opción requiere ejecutar la versión instalada de ts3j-client.", "此选项需要运行已安装版本的 ts3j-client。");
        put("tray.open", "Open ts3j-client", "Abrir ts3j-client", "打开 ts3j-client");
        put("tray.quit", "Quit", "Salir", "退出");
        put("tray.mic.app", "ts3j-client · ready", "ts3j-client · listo", "ts3j-client · 就绪");
        put("tray.mic.muted", "ts3j-client · microphone muted", "ts3j-client · micrófono apagado", "ts3j-client · 麦克风已静音");
        put("tray.mic.idle", "ts3j-client · microphone idle", "ts3j-client · micrófono inactivo", "ts3j-client · 麦克风空闲");
        put("tray.mic.active", "ts3j-client · microphone active", "ts3j-client · micrófono activo", "ts3j-client · 麦克风活动");
        put("settings.audio", "Audio devices", "Dispositivos de audio", "音频设备");
        put("settings.audio.help", "Choose the local microphone and playback device. Levels are measured locally and are never sent without a TeamSpeak audio backend.", "Elige el micrófono y el dispositivo de reproducción locales. Los niveles se miden localmente y no se envían sin un backend de audio de TeamSpeak.", "选择本地麦克风和播放设备。电平仅在本机测量，没有 TeamSpeak 音频后端时不会发送。");
        put("settings.audio.open", "Configure audio devices", "Configurar dispositivos de audio", "配置音频设备");
        put("settings.audio.open.accessible", "Open microphone and playback device settings", "Abrir la configuración del micrófono y la reproducción", "打开麦克风和播放设备设置");
        put("settings.audio.capture", "Microphone", "Micrófono", "麦克风");
        put("settings.audio.capture.device", "Capture device", "Dispositivo de entrada", "输入设备");
        put("settings.audio.capture.level", "Microphone level", "Nivel del micrófono", "麦克风电平");
        put("settings.audio.playback", "Playback", "Reproducción", "播放");
        put("settings.audio.playback.device", "Playback device", "Dispositivo de salida", "输出设备");
        put("settings.audio.playback.level", "Playback level", "Nivel de reproducción", "播放电平");
        put("settings.audio.default", "Default", "Predeterminado", "默认");
        put("settings.audio.no.devices", "No compatible device found", "No se encontró un dispositivo compatible", "未找到兼容设备");
        put("settings.audio.monitoring", "Live local meter", "Medidor local en tiempo real", "本地实时电平");
        put("settings.audio.capture.help", "The green meter reacts to sound detected by the selected microphone.", "El medidor verde reacciona al sonido detectado por el micrófono seleccionado.", "绿色电平会响应所选麦克风检测到的声音。");
        put("settings.audio.playback.help", "The output meter follows audio produced by this app and the test tone.", "El medidor de salida sigue el audio producido por esta app y el tono de prueba.", "输出电平会显示本应用和测试音调产生的音频。");
        put("settings.audio.test", "Play test sound", "Reproducir sonido de prueba", "播放测试声音");
        put("settings.audio.test.tooltip", "Play a short tone through the selected playback device", "Reproducir un tono corto en el dispositivo seleccionado", "通过所选播放设备播放短音调");
        put("settings.audio.level.unavailable", "Unavailable", "No disponible", "不可用");
        put("settings.audio.level.quiet", "Quiet", "Silencio", "安静");
        put("settings.audio.level.detected", "Detected", "Detectado", "已检测");
        put("timer.active", "Active", "En curso", "进行中");
        put("timer.none", "No active voice session in this channel.", "No hay una sesión de voz activa en este canal.", "此频道没有活跃的语音会话。");
        put("timer.unknown", "Waiting for the server timer authority to report the session start.", "Esperando a que el temporizador del servidor informe el inicio de la sesión.", "正在等待服务器计时器报告会话开始时间。");
        put("timer.start", "Shared session start: %s", "Inicio de sesión compartida: %s", "共享会话开始：%s");
        put("timer.inherited", "Session active before connecting · start received from the server.", "La sesión ya estaba activa · el inicio fue recibido del servidor.", "连接前会话已处于活动状态 · 开始时间由服务器提供。");
        put("dialog.title", "Message", "Mensaje", "消息");
        put("error.prefix", "!  ", "!  ", "!  ");
    }

    private UiText() { }

    private static void put(String key, String english, String spanish, String chinese) {
        TEXT.put(key, new String[] {english, spanish, chinese});
    }

    static String text(UiLanguage language, String key, Object... arguments) {
        String[] values = TEXT.get(key);
        String value = values == null ? key : values[index(language)];
        return arguments == null || arguments.length == 0
                ? value : String.format(Locale.ROOT, value, arguments);
    }

    private static int index(UiLanguage language) {
        if (language == UiLanguage.SPANISH) return 1;
        if (language == UiLanguage.CHINESE) return 2;
        return 0;
    }
}
