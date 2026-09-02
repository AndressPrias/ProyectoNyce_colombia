#define MyAppName "Control Muestras LENC"
#define MyAppVersion "1.3.9"
#define MyAppPublisher "Andres Prias"
#define MyAppURL "https://andresprias.dev"
#define MyAppExeName "ControlMuestrasLENC.exe"

#ifndef AppImageDir
  #define AppImageDir "..\dist\ControlMuestrasLENC"
#endif

#ifndef WebSyncToken
  #define WebSyncToken ""
#endif

[Setup]
AppId={{7E3D95B4-4E90-4F9C-9EF0-A6B9B71C2C41}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={localappdata}\Programs\ControlMuestrasLENC
UsePreviousAppDir=no
DefaultGroupName={#MyAppName}
DisableDirPage=yes
DisableProgramGroupPage=yes
LicenseFile=
OutputDir=..\dist\installer
OutputBaseFilename=ControlMuestrasLENC-Setup-Usuario
SetupIconFile=icon_App.ico
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
WizardImageFile=wizard_empresa.bmp
WizardSmallImageFile=wizard_empresa_small.bmp
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
UninstallDisplayIcon={app}\{#MyAppExeName}
VersionInfoVersion={#MyAppVersion}
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription=Instalador de Control Muestras LENC
VersionInfoCopyright=Copyright 2026 Andres Prias

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "websync"; Description: "Activar la sincronización con el portal web en este equipo"; GroupDescription: "Equipo principal:"; Flags: unchecked; Check: WebSyncAvailable

[Files]
Source: "{#AppImageDir}\*"; DestDir: "{app}"; Excludes: "config.properties"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#AppImageDir}\config.properties"; DestDir: "{app}"; Flags: onlyifdoesntexist

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; IconFilename: "{app}\{#MyAppExeName}"
Name: "{group}\Desinstalar {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; IconFilename: "{app}\{#MyAppExeName}"

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Ejecutar {#MyAppName}"; Flags: nowait postinstall skipifsilent

[Code]
function WebSyncAvailable: Boolean;
begin
  Result := Length('{#WebSyncToken}') > 0;
end;

procedure SetConfigProperty(Lines: TStringList; const Key, Value: String);
var
  I: Integer;
  Prefix: String;
begin
  Prefix := Key + '=';
  for I := Lines.Count - 1 downto 0 do
  begin
    if Pos(Prefix, Trim(Lines[I])) = 1 then
      Lines.Delete(I);
  end;
  Lines.Add(Prefix + Value);
end;

procedure ConfigureWebSync;
var
  ConfigPath: String;
  Lines: TStringList;
begin
  ConfigPath := ExpandConstant('{app}\config.properties');
  Lines := TStringList.Create;
  try
    if FileExists(ConfigPath) then
      Lines.LoadFromFile(ConfigPath);

    SetConfigProperty(Lines, 'web.sync.enabled', 'true');
    SetConfigProperty(Lines, 'web.sync.url', 'https://muestras.andev.com.co/api/sync.php');
    SetConfigProperty(Lines, 'web.sync.token', '{#WebSyncToken}');
    SetConfigProperty(Lines, 'web.sync.interval.minutes', '5');
    Lines.SaveToFile(ConfigPath);
  finally
    Lines.Free;
  end;
end;

procedure EnsureStorageConfig;
var
  ConfigPath, SourceFolder, StorageFolder, ConfiguredFolder, Line, Prefix: String;
  Lines: TStringList;
  I: Integer;
begin
  ConfigPath := ExpandConstant('{app}\config.properties');
  SourceFolder := ExpandConstant('{src}');
  StorageFolder := ExtractFileDir(RemoveBackslashUnlessRoot(SourceFolder));
  Prefix := 'storage.folder=';
  ConfiguredFolder := '';
  Lines := TStringList.Create;
  try
    if FileExists(ConfigPath) then
      Lines.LoadFromFile(ConfigPath);

    for I := 0 to Lines.Count - 1 do
    begin
      Line := Trim(Lines[I]);
      if Pos(Prefix, Line) = 1 then
      begin
        ConfiguredFolder := Copy(Line, Length(Prefix) + 1, MaxInt);
        StringChangeEx(ConfiguredFolder, '\:', ':', True);
        StringChangeEx(ConfiguredFolder, '\\', '\', True);
        Break;
      end;
    end;

    if (not DirExists(ConfiguredFolder)) and
       (CompareText(ExtractFileName(RemoveBackslashUnlessRoot(SourceFolder)), 'actualizaciones') = 0) and
       DirExists(StorageFolder) then
    begin
      StringChangeEx(StorageFolder, '\', '/', True);
      SetConfigProperty(Lines, 'storage.folder', StorageFolder);
      Lines.SaveToFile(ConfigPath);
    end;
  finally
    Lines.Free;
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    EnsureStorageConfig;
    if WizardIsTaskSelected('websync') then
      ConfigureWebSync;
  end;
end;


