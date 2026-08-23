#ifndef AppVersion
#define AppVersion "1.0.5"
#endif
#ifndef AppSource
#define AppSource "."
#endif
#ifndef OutputDir
#define OutputDir "."
#endif
#ifndef AppIcon
#define AppIcon "assets\ts3j-client.ico"
#endif

#define MyAppName "ts3j-client"
#define MyAppPublisher "ts3j"

[Setup]
AppId=4E0C9103-1EE0-4E2C-9CCB-8BE146519E16
AppName={#MyAppName}
AppVersion={#AppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={code:GetDefaultDir}
DisableProgramGroupPage=yes
UsePreviousAppDir=yes
UsePreviousUserInfo=yes
PrivilegesRequired=lowest
OutputDir={#OutputDir}
OutputBaseFilename=ts3j-client-{#AppVersion}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
SetupIconFile={#AppIcon}
; The client can stay hidden in the tray and may have Java child processes.
; Close it explicitly in PrepareToInstall instead of relying on Inno's generic
; window-close prompt, which cannot always see tray-only JavaFX windows.
CloseApplications=no
RestartApplications=no
Uninstallable=yes
UninstallDisplayName={#MyAppName}
UninstallDisplayIcon={app}\ts3j-client.ico
VersionInfoDescription=TeamSpeak shared voice session timer
VersionInfoProductName={#MyAppName}
VersionInfoCompany={#MyAppPublisher}
VersionInfoVersion={#AppVersion}.0

[Files]
Source: "{#AppSource}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[InstallDelete]
Type: files; Name: "{autodesktop}\ts3j-client.lnk"
Type: files; Name: "{autoprograms}\ts3j-client.lnk"

[Icons]
Name: "{autodesktop}\ts3j-client"; Filename: "{app}\ts3j-client.exe"; IconFilename: "{app}\ts3j-client.ico"; IconIndex: 0; WorkingDir: "{app}"; Comment: "ts3j-client TeamSpeak"
Name: "{autoprograms}\ts3j-client"; Filename: "{app}\ts3j-client.exe"; IconFilename: "{app}\ts3j-client.ico"; IconIndex: 0; WorkingDir: "{app}"; Comment: "ts3j-client TeamSpeak"

[Run]
Filename: "{app}\ts3j-client.exe"; Description: "Iniciar ts3j-client"; Flags: postinstall nowait skipifsilent

[UninstallDelete]
Type: files; Name: "{userappdata}\Microsoft\Windows\Start Menu\Programs\Startup\ts3j-client-startup.vbs"

[Code]
const
  SHCNE_ASSOCCHANGED = $08000000;
  SHCNF_IDLIST = $0000;

procedure SHChangeNotify(wEventId: Integer; uFlags: Integer; dwItem1: Integer; dwItem2: Integer);
  external 'SHChangeNotify@shell32.dll stdcall';

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
    SHChangeNotify(SHCNE_ASSOCCHANGED, SHCNF_IDLIST, 0, 0);
end;

function ClientProcessRunning: Boolean;
var
  ResultCode: Integer;
  Output: TExecOutput;
  I: Integer;
begin
  Result := False;
  { tasklist is short-lived and lets us bound the shutdown wait without
    waiting indefinitely on a Java process that is already being torn down. }
  if not ExecAndCaptureOutput(ExpandConstant('{cmd}'),
    '/C tasklist /FI "IMAGENAME eq ts3j-client.exe" /NH /FO CSV',
    '', SW_HIDE, ewWaitUntilTerminated, ResultCode, Output) then
  begin
    { If the query fails, err on the side of forcing the process tree down. }
    Log('Could not query ts3j-client processes; forcing taskkill.');
    Result := True;
    Exit;
  end;
  for I := 0 to GetArrayLength(Output.StdOut) - 1 do
    if Pos('ts3j-client.exe', LowerCase(Output.StdOut[I])) > 0 then
    begin
      Result := True;
      Exit;
    end;
end;

procedure CloseRunningClient;
var
  ResultCode: Integer;
  I: Integer;
begin
  Log('Closing running ts3j-client processes before replacing files.');

  { Ask the running app to disconnect cleanly. Do not wait for the Java
    launcher: the bounded polling below handles both old and new versions. }
  if FileExists(ExpandConstant('{app}\ts3j-client.exe')) then
    Exec(ExpandConstant('{app}\ts3j-client.exe'), '--shutdown',
      ExpandConstant('{app}'), SW_HIDE, ewNoWait, ResultCode);
  Sleep(500);

  { A tray-only process may not respond to WM_CLOSE. /T also terminates its
    bundled Java child process. Every invocation is non-blocking so a broken
    child cannot freeze the Preparing to Install page. }
  for I := 1 to 12 do
  begin
    if not ClientProcessRunning then
    begin
      Log('All ts3j-client processes have exited.');
      Exit;
    end;
    Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /T /IM ts3j-client.exe',
      '', SW_HIDE, ewNoWait, ResultCode);
    Sleep(250);
  end;

  if ClientProcessRunning then
    Log('Timed out waiting for ts3j-client processes; continuing with setup.');
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  CloseRunningClient;
  Result := '';
end;

function FindPreviousMsiInstallLocation(RootKey: Integer; BaseKey: String): String;
var
  SubKeys: TArrayOfString;
  I: Integer;
  DisplayName: String;
  InstallLocation: String;
begin
  Result := '';
  if not RegGetSubkeyNames(RootKey, BaseKey, SubKeys) then
    Exit;

  for I := 0 to GetArrayLength(SubKeys) - 1 do
  begin
    DisplayName := '';
    InstallLocation := '';
    if RegQueryStringValue(RootKey, BaseKey + '\' + SubKeys[I], 'DisplayName', DisplayName)
      and (CompareText(DisplayName, '{#MyAppName}') = 0)
      and RegQueryStringValue(RootKey, BaseKey + '\' + SubKeys[I], 'InstallLocation', InstallLocation)
      and (InstallLocation <> '')
      and DirExists(InstallLocation) then
    begin
      Result := RemoveBackslashUnlessRoot(InstallLocation);
      Exit;
    end;
  end;
end;

function GetDefaultDir(Param: String): String;
var
  Previous: String;
begin
  Previous := FindPreviousMsiInstallLocation(HKLM64, 'Software\Microsoft\Windows\CurrentVersion\Uninstall');
  if Previous = '' then
    Previous := FindPreviousMsiInstallLocation(HKLM, 'Software\Microsoft\Windows\CurrentVersion\Uninstall');
  if Previous = '' then
    Previous := FindPreviousMsiInstallLocation(HKCU64, 'Software\Microsoft\Windows\CurrentVersion\Uninstall');
  if Previous = '' then
    Previous := FindPreviousMsiInstallLocation(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Uninstall');
  if Previous = '' then
    Previous := FindPreviousMsiInstallLocation(HKLM, 'Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall');

  if Previous <> '' then
    Result := Previous
  else
    Result := ExpandConstant('{localappdata}\ts3j-client');
end;
