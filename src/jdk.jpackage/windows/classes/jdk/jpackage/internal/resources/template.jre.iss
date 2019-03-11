;This file will be executed next to the application bundle image
;I.e. current directory will contain folder INSTALLER_NAME with application files
[Setup]
AppId=PRODUCT_APP_IDENTIFIER
AppName=INSTALLER_NAME
AppVersion=APPLICATION_VERSION
AppVerName=INSTALLER_NAME APPLICATION_VERSION
AppPublisher=APPLICATION_VENDOR
AppComments=APPLICATION_DESCRIPTION
AppCopyright=APPLICATION_COPYRIGHT
VersionInfoVersion=APPLICATION_VERSION
VersionInfoDescription=APPLICATION_DESCRIPTION
DefaultDirName=APPLICATION_INSTALL_ROOT\INSTALLER_NAME
DisableStartupPrompt=Yes
DisableDirPage=DISABLE_DIR_PAGE
DisableProgramGroupPage=Yes
DisableReadyPage=Yes
DisableFinishedPage=Yes
DisableWelcomePage=Yes
DefaultGroupName=APPLICATION_GROUP
;Optional License
LicenseFile=APPLICATION_LICENSE_FILE
;WinXP or above
MinVersion=0,5.1
OutputBaseFilename=INSTALLER_FILE_NAME
Compression=lzma
SolidCompression=yes
PrivilegesRequired=APPLICATION_INSTALL_PRIVILEGE
SetupIconFile=
UninstallDisplayIcon=
UninstallDisplayName=INSTALLER_NAME
WizardImageStretch=No
WizardSmallImageFile=INSTALLER_NAME-setup-icon.bmp
ArchitecturesInstallIn64BitMode=ARCHITECTURE_BIT_MODE
FILE_ASSOCIATIONS

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "APPLICATION_IMAGE\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Code]
function returnTrue(): Boolean;
begin
  Result := True;
end;

function returnFalse(): Boolean;
begin
  Result := False;
end;

function InitializeSetup(): Boolean;
begin
  Result := True;
end;
